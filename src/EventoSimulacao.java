final class EventoSimulacao implements Comparable<EventoSimulacao> {
    enum Tipo { CHEGADA, SAIDA }

    final double tempo;
    final Tipo tipo;
    final String origem;
    String destino;
    final Cliente cliente;
    final long ordem;

    EventoSimulacao(double tempo, Tipo tipo, String origem, String destino,
                    Cliente cliente, long ordem) {
        this.tempo = tempo;
        this.tipo = tipo;
        this.origem = origem;
        this.destino = destino;
        this.cliente = cliente;
        this.ordem = ordem;
    }

    @Override
    public int compareTo(EventoSimulacao outro) {
        int comparacaoTempo = Double.compare(tempo, outro.tempo);
        return comparacaoTempo != 0 ? comparacaoTempo : Long.compare(ordem, outro.ordem);
    }
}