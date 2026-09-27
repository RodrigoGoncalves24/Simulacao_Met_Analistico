import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Modelo {
    final Map<String, FilaConfig> filas;
    final List<RotaConfig> rotas;
    final Map<String, Double> chegadas;
    final long seed;
    final long a;
    final long c;
    final long m;
    final List<Double> rndnumbers;

    Modelo(Map<String, FilaConfig> filas, List<RotaConfig> rotas,
           Map<String, Double> chegadas, long seed, long a, long c, long m,
           List<Double> rndnumbers) {
        this.filas = Collections.unmodifiableMap(new LinkedHashMap<>(filas));
        this.rotas = List.copyOf(rotas);
        this.chegadas = Collections.unmodifiableMap(new LinkedHashMap<>(chegadas));
        this.seed = seed;
        this.a = a;
        this.c = c;
        this.m = m;
        this.rndnumbers = List.copyOf(rndnumbers);
    }
}

final class FilaConfig {
    final String id;
    final int servidores;
    final int capacidade;
    final Double minArrival;
    final Double maxArrival;
    final double minService;
    final double maxService;

    FilaConfig(String id, int servidores, int capacidade, Double minArrival,
               Double maxArrival, double minService, double maxService) {
        this.id = id;
        this.servidores = servidores;
        this.capacidade = capacidade;
        this.minArrival = minArrival;
        this.maxArrival = maxArrival;
        this.minService = minService;
        this.maxService = maxService;
    }
}

final class RotaConfig {
    final String origem;
    final String destino;
    final double probabilidade;

    RotaConfig(String origem, String destino, double probabilidade) {
        this.origem = origem;
        this.destino = destino;
        this.probabilidade = probabilidade;
    }
}