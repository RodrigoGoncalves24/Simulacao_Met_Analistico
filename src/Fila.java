import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

final class Fila {
    final FilaConfig config;
    final ArrayDeque<Cliente> esperando = new ArrayDeque<>();
    final Map<Integer, Double> tempoPorEstado = new HashMap<>();
    int emServico;
    int perdas;

    Fila(FilaConfig config) {
        this.config = config;
    }

    int tamanho() {
        return esperando.size() + emServico;
    }

    boolean cheia() {
        return config.capacidade > 0 && tamanho() >= config.capacidade;
    }
}