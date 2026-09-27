import java.util.List;

final class GeradorLCG {
    static final int LIMITE = 100_000;

    private long estado;
    private final long a;
    private final long c;
    private final long m;
    private final List<Double> valoresFornecidos;
    private int consumidos;

    GeradorLCG(long seed, long a, long c, long m, List<Double> valoresFornecidos) {
        if (m <= 0) {
            throw new IllegalArgumentException("m deve ser maior que zero");
        }
        this.estado = seed;
        this.a = a;
        this.c = c;
        this.m = m;
        this.valoresFornecidos = valoresFornecidos;
    }

    double proximo() {
        if (consumidos == LIMITE) {
            throw new LimiteAleatoriosException();
        }
        if (!valoresFornecidos.isEmpty() && consumidos < valoresFornecidos.size()) {
            consumidos++;
            return valoresFornecidos.get(consumidos - 1);
        }
        estado = Math.floorMod(a * estado + c, m);
        consumidos++;
        return (double) estado / m;
    }

    int consumidos() {
        return consumidos;
    }
}

final class LimiteAleatoriosException extends RuntimeException {
}