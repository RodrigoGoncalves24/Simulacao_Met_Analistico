import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

public final class Simulador {
    private static final String EXTERIOR = "-1";

    private final Modelo modelo;
    private final GeradorLCG gerador;
    private final Map<String, Fila> filas = new HashMap<>();
    private final List<RotaConfig> rotas;
    private final PriorityQueue<EventoSimulacao> eventos = new PriorityQueue<>();
    private long proximoCliente;
    private long ordemEvento;
    private double tempoGlobal;
    private double ultimoTempo;
    private int saidas;

    private Simulador(Modelo modelo) {
        this.modelo = modelo;
        this.gerador = new GeradorLCG(modelo.seed, modelo.a, modelo.c, modelo.m, modelo.rndnumbers);
        this.rotas = modelo.rotas;
        modelo.filas.values().forEach(config -> filas.put(config.id, new Fila(config)));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Uso: java -jar simulador-rede-filas.jar <modelo.yml>");
        }
        new Simulador(new YamlModeloLoader().carregar(Path.of(args[0]))).executar();
    }

    private void executar() {
        agendarChegadasIniciais();
        try {
            while (!eventos.isEmpty()) {
                EventoSimulacao evento = eventos.poll();
                atualizarEstatisticas(evento.tempo);
                tempoGlobal = evento.tempo;
                if (evento.tipo == EventoSimulacao.Tipo.CHEGADA) {
                    tratarChegada(evento);
                } else {
                    tratarSaida(evento);
                }
            }
        } catch (LimiteAleatoriosException limite) {
            System.out.println("Limite de 100000 aleatorios atingido.");
        }
        atualizarEstatisticas(tempoGlobal);
        imprimirRelatorio();
    }

    private void agendarChegadasIniciais() {
        for (Map.Entry<String, Double> chegada : modelo.chegadas.entrySet()) {
            agendarChegada(chegada.getKey(), chegada.getValue());
        }
    }

    private void tratarChegada(EventoSimulacao evento) {
        Fila fila = fila(evento.destino);
        FilaConfig config = fila.config;
        admitir(fila, evento.cliente);
        if (config.minArrival != null) {
            agendarChegada(fila.config.id, tempoGlobal + uniforme(config.minArrival, config.maxArrival));
        }
    }

    private void tratarSaida(EventoSimulacao evento) {
        Fila origem = fila(evento.origem);
        RotaConfig rota = sortearRota(origem.config.id);
        evento.destino = rota.destino;
        origem.emServico--;
        iniciarServicos(origem);

        if (evento.destino.equals(EXTERIOR)) {
            saidas++;
        } else {
            admitir(fila(evento.destino), evento.cliente);
        }
    }

    private void admitir(Fila fila, Cliente cliente) {
        if (fila.cheia()) {
            fila.perdas++;
            return;
        }
        cliente.fila = fila;
        fila.esperando.addLast(cliente);
        iniciarServicos(fila);
    }

    private void iniciarServicos(Fila fila) {
        while (fila.emServico < fila.config.servidores && !fila.esperando.isEmpty()) {
            Cliente cliente = fila.esperando.peekFirst();
            double duracao = uniforme(fila.config.minService, fila.config.maxService);
            fila.esperando.removeFirst();
            fila.emServico++;
            eventos.add(new EventoSimulacao(tempoGlobal + duracao,
                    EventoSimulacao.Tipo.SAIDA, fila.config.id, EXTERIOR,
                    cliente, ordemEvento++));
        }
    }

    private RotaConfig sortearRota(String origem) {
        List<RotaConfig> opcoes = rotas.stream()
                .filter(rota -> rota.origem.equals(origem))
                .collect(Collectors.toList());
        double sorteio = gerador.proximo();
        double acumulado = 0.0;
        for (RotaConfig rota : opcoes) {
            acumulado += rota.probabilidade;
            if (sorteio < acumulado) {
                return rota;
            }
        }
        return opcoes.get(opcoes.size() - 1);
    }

    private void agendarChegada(String destino, double tempo) {
        eventos.add(new EventoSimulacao(tempo, EventoSimulacao.Tipo.CHEGADA,
                EXTERIOR, destino, new Cliente(++proximoCliente), ordemEvento++));
    }

    private double uniforme(double minimo, double maximo) {
        return minimo + (maximo - minimo) * gerador.proximo();
    }

    private void atualizarEstatisticas(double novoTempo) {
        double intervalo = novoTempo - ultimoTempo;
        if (intervalo < 0) {
            throw new IllegalStateException("O tempo global nao pode retroceder");
        }
        for (Fila fila : filas.values()) {
            fila.tempoPorEstado.merge(fila.tamanho(), intervalo, Double::sum);
        }
        ultimoTempo = novoTempo;
    }

    private void imprimirRelatorio() {
        System.out.println("Aleatorios utilizados: " + gerador.consumidos());
        System.out.println("Tempo global da simulacao: " + tempoGlobal);
        System.out.println("Saidas para o exterior: " + saidas);
        for (Fila fila : modelo.filas.values().stream()
                .map(config -> filas.get(config.id)).toList()) {
            double total = fila.tempoPorEstado.values().stream().mapToDouble(Double::doubleValue).sum();
            Map<Integer, Double> probabilidades = new HashMap<>();
            fila.tempoPorEstado.forEach((estado, tempo) -> probabilidades.put(estado, total == 0 ? 0 : tempo / total));
            System.out.println(fila.config.id + " - perdas: " + fila.perdas);
            System.out.println(fila.config.id + " - tempos acumulados por estado: " + fila.tempoPorEstado);
            System.out.println(fila.config.id + " - probabilidades dos estados: " + probabilidades);
        }
    }

    private Fila fila(String id) {
        Fila fila = filas.get(id);
        if (fila == null) {
            throw new IllegalArgumentException("Fila inexistente: " + id);
        }
        return fila;
    }
}