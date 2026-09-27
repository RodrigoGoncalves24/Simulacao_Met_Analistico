import org.yaml.snakeyaml.Yaml;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class YamlModeloLoader {
    Modelo carregar(Path caminho) throws IOException {
        Map<String, Object> raiz;
        String conteudo = Files.readString(caminho).replaceAll("(?m)^\\s*!PARAMETERS\\s*$", "");
        raiz = new Yaml().load(conteudo);
        if (raiz == null) {
            throw new IllegalArgumentException("O arquivo YAML esta vazio");
        }

        Map<String, FilaConfig> filas = new LinkedHashMap<>();
        Map<String, Object> filasYaml = mapa(raiz, "queues");
        for (Map.Entry<String, Object> entry : filasYaml.entrySet()) {
            Map<String, Object> valor = mapa(entry.getValue());
            filas.put(entry.getKey(), new FilaConfig(
                    entry.getKey(), inteiro(valor, "servers", 1), inteiro(valor, "capacity", 0),
                    decimalOpcional(valor, "minArrival"), decimalOpcional(valor, "maxArrival"),
                    decimalObrigatorio(valor, "minService"), decimalObrigatorio(valor, "maxService")));
        }

        List<RotaConfig> rotas = new ArrayList<>();
        for (Object item : lista(raiz, "network")) {
            Map<String, Object> rota = mapa(item);
            rotas.add(new RotaConfig(texto(rota, "source"), texto(rota, "target"),
                    decimalObrigatorio(rota, "probability")));
        }
        completarSaidasExternas(filas.keySet(), rotas);

        Map<String, Double> chegadas = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : mapa(raiz, "arrivals").entrySet()) {
            chegadas.put(entry.getKey(), numero(entry.getValue()));
        }

        Map<String, Object> lcg = raiz.containsKey("lcg") ? mapa(raiz, "lcg") : raiz;
        long seed = inteiroLong(lcg, "seed", 1);
        long a = inteiroLong(lcg, "a", 1664525);
        long c = inteiroLong(lcg, "c", 1013904223);
        long m = inteiroLong(lcg, "m", 4294967296L);
        List<Double> rndnumbers = new ArrayList<>();
        for (Object value : listaOpcional(raiz, "rndnumbers")) {
            rndnumbers.add(numero(value));
        }

        validar(filas, rotas, chegadas);
        return new Modelo(filas, rotas, chegadas, seed, a, c, m, rndnumbers);
    }

    private static void validar(Map<String, FilaConfig> filas, List<RotaConfig> rotas,
                                Map<String, Double> chegadas) {
        for (String chegada : chegadas.keySet()) {
            if (!filas.containsKey(chegada)) {
                throw new IllegalArgumentException("Fila de chegada inexistente: " + chegada);
            }
        }
        for (FilaConfig fila : filas.values()) {
            if (fila.servidores < 1 || fila.capacidade < 0 || fila.minService > fila.maxService) {
                throw new IllegalArgumentException("Configuracao invalida da fila " + fila.id);
            }
            if ((fila.minArrival == null) != (fila.maxArrival == null)
                    || (fila.minArrival != null && fila.minArrival > fila.maxArrival)) {
                throw new IllegalArgumentException("Intervalo de chegada invalido na fila " + fila.id);
            }
            double total = rotas.stream().filter(rota -> rota.origem.equals(fila.id))
                    .mapToDouble(rota -> rota.probabilidade).sum();
            if (Math.abs(total - 1.0) > 0.000001) {
                throw new IllegalArgumentException("As probabilidades de " + fila.id + " devem somar 1 (atual: " + total + ")");
            }
        }
        for (RotaConfig rota : rotas) {
            if (!filas.containsKey(rota.origem) || (!rota.destino.equals("-1") && !filas.containsKey(rota.destino))) {
                throw new IllegalArgumentException("Rota invalida: " + rota.origem + " -> " + rota.destino);
            }
            if (rota.probabilidade < 0) {
                throw new IllegalArgumentException("Probabilidade negativa na rota " + rota.origem);
            }
        }
    }

    private static void completarSaidasExternas(Iterable<String> ids, List<RotaConfig> rotas) {
        for (String id : ids) {
            double total = rotas.stream().filter(rota -> rota.origem.equals(id))
                    .mapToDouble(rota -> rota.probabilidade).sum();
            if (total < 1.0 - 0.000001) {
                rotas.add(new RotaConfig(id, "-1", 1.0 - total));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> mapa(Map<String, Object> mapa, String chave) {
        return mapa(mapa.get(chave));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> lista(Map<String, Object> mapa, String chave) {
        Object value = mapa.get(chave);
        return value == null ? List.of() : (List<Object>) value;
    }

    private static List<Object> listaOpcional(Map<String, Object> mapa, String chave) {
        return lista(mapa, chave);
    }

    private static String texto(Map<String, Object> mapa, String chave) {
        return String.valueOf(mapa.get(chave));
    }

    private static int inteiro(Map<String, Object> mapa, String chave, int padrao) {
        return mapa.containsKey(chave) ? (int) numero(mapa.get(chave)) : padrao;
    }

    private static long inteiroLong(Map<String, Object> mapa, String chave, long padrao) {
        return mapa.containsKey(chave) ? (long) numero(mapa.get(chave)) : padrao;
    }

    private static Double decimalOpcional(Map<String, Object> mapa, String chave) {
        return mapa.containsKey(chave) ? numero(mapa.get(chave)) : null;
    }

    private static double decimalObrigatorio(Map<String, Object> mapa, String chave) {
        if (!mapa.containsKey(chave)) {
            throw new IllegalArgumentException("Campo obrigatorio ausente: " + chave);
        }
        return numero(mapa.get(chave));
    }

    private static double numero(Object value) {
        return ((Number) value).doubleValue();
    }
}