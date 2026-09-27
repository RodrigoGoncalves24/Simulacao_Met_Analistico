# Simulação de Fila e Escalonador

## Simulador genérico de redes de filas

O simulador atual carrega uma rede de filas a partir de um arquivo YAML. A quantidade de filas, servidores, capacidades, intervalos de atendimento e rotas não é definida no código.

### Execução

Pré-requisitos: Java 17 ou superior e Maven.

```text
mvn package
java -jar target/simulador-rede-filas-1.0.0.jar caminho/para/model.yml
```

O simulador encerra imediatamente após consumir o 100.000º número pseudoaleatório. O relatório final apresenta o tempo global, saídas para o exterior, perdas por fila, tempo acumulado em cada estado e probabilidade de cada estado.

### Formato do modelo

O arquivo segue o formato do módulo 8:

```yaml
arrivals:
        Q1: 2.0

queues:
        Q1:
                servers: 1
                capacity: 0
                minArrival: 1.0
                maxArrival: 2.0
                minService: 2.0
                maxService: 3.0
        Q2:
                servers: 2
                capacity: 4
                minService: 3.0
                maxService: 5.0

network:
- source: Q1
        target: Q2
        probability: 0.2
- source: Q1
        target: -1
        probability: 0.8
- source: Q2
        target: -1
        probability: 1.0

lcg:
        seed: 1
        a: 1664525
        c: 1013904223
        m: 4294967296
```

`-1` representa o exterior. Uma chegada é um evento `-1 -> fila`; uma saída é `fila -> -1`; e um roteamento interno é `fila -> fila`. `capacity: 0` representa capacidade infinita e as demais capacidades incluem clientes em espera e em atendimento.

As rotas são avaliadas na ordem em que aparecem no YAML, usando faixas cumulativas. A soma das probabilidades de uma fila não pode exceder `1`; caso fique abaixo de `1`, a probabilidade restante é completada automaticamente como saída para `-1`. O campo opcional `rndnumbers` pode ser usado para reproduzir uma sequência fornecida; quando ela termina, o simulador continua com o LCG configurado.

O arquivo `model (1).yml` é um exemplo de entrada e pode ser executado diretamente pelo comando acima.

Projeto desenvolvido em Java para simular o comportamento de uma fila de atendimento utilizando **números pseudoaleatórios**, processos, servidores e um escalonador de eventos.

O objetivo é simular a entrada de processos em um sistema, verificar a disponibilidade de servidores, encaminhar processos para atendimento ou perda e controlar os eventos de chegada (`IN`) e saída (`OUT`).

## Funcionamento atual

A estrutura atual da simulação já permite:

* Gerar processos utilizando números pseudoaleatórios.
* Identificar processos de chegada (`IN`).
* Encaminhar processos para a fila de processamento.
* Identificar servidores livres e ocupados.
* Iniciar o atendimento de um processo quando existe servidor disponível.
* Gerar um tempo de atendimento e programar um evento de saída (`OUT`).
* Manter processos aguardando quando os servidores estão ocupados.
* Encaminhar processos para a fila de perda quando a capacidade do sistema é atingida.
* Selecionar eventos de chegada e saída através do `nextEvent()`.

O fluxo geral da simulação é:

```text
Geração dos processos
        ↓
Seleção do próximo evento
        ↓
Evento de chegada (IN) ou saída (OUT)
        ↓
Fila / Servidor / Perda
        ↓
Agendamento do próximo evento
        ↓
Avanço do tempo global (TG)
```

O `nextEvent()` possui uma responsabilidade específica: **identificar qual evento deve ser tratado a seguir**.

Ele não deve executar ou resolver o evento. Após a seleção, o fluxo principal decide se deve chamar:

```text
eventoChegada()
```

ou:

```text
eventSaida()
```

A responsabilidade de tratar o evento pertence a esses métodos.

---

# Principais estruturas

### `processes`

Lista contendo os processos que ainda possuem eventos de chegada (`IN`) a serem tratados.

Quando um processo de chegada é selecionado pelo `nextEvent()`, ele é removido dessa lista.

### `filaProcessamento`

Representa os processos que estão aguardando ou sendo processados pelos servidores.

### `filaProcessoSaida`

Contém os processos que já iniciaram atendimento e possuem um evento de saída (`OUT`) agendado.

### `filaPerda`

Armazena os processos que não puderam entrar no sistema por falta de espaço.

Um processo colocado nessa lista encerra seu fluxo dentro da simulação, salvo alteração futura das regras do modelo.

### `servidores`

Representa a ocupação dos servidores.

Atualmente:

```text
0 → servidor livre
ID do processo → servidor ocupado
```

Exemplo:

```text
Servidor 0 → 15
Servidor 1 → 0
```

Nesse exemplo, o servidor `0` está atendendo o processo `15`, enquanto o servidor `1` está livre.

### `escalonador`

Mapa que relaciona:

```text
ID do processo → tempo do evento agendado
```

É utilizado principalmente para armazenar os horários dos eventos de saída (`OUT`) dos processos que estão sendo atendidos.

### `TG`

Representa o **tempo global da simulação**, ou seja, o instante atual em que a simulação está sendo executada.

### `deltaTempo`

Armazena os intervalos de tempo utilizados durante a simulação.

### `listaEventos`

Mantém registros dos tipos de eventos gerados (`IN` e `OUT`).

---

# Funções

## `main()`

Controla o fluxo principal da simulação.

Responsabilidades:

* Inicializar os servidores.
* Gerar os processos.
* Executar as iterações.
* Solicitar o próximo evento.
* Encaminhar o processo para `eventoChegada()` ou `eventSaida()`.
* Atualizar o tempo global.
* Exibir informações de acompanhamento.

---

## `geraProcessos()`

Cria os processos iniciais da simulação.

Cada processo recebe:

* ID;
* tempo relacionado à chegada;
* evento inicial `IN`.

Os valores são obtidos através do gerador pseudoaleatório.

### Atenção

Os valores gerados por `tempoChegada()` representam **intervalos de chegada** e ainda precisam ser tratados corretamente para determinar os instantes absolutos de chegada dos processos.

Essa parte está entre os pontos que ainda precisam ser revisados.

---

## `nextRandom()`

Gera o próximo número pseudoaleatório utilizando um **Linear Congruential Generator (LCG)**:

```text
X(n+1) = (A × X(n) + C) mod M
```

O valor é normalizado e armazenado em `aleatoriosGerados`.

---

## `nextEvent()`

Responsável por identificar o **próximo evento da simulação**.

Atualmente, ele verifica separadamente:

```text
processes
    ↓
eventos IN

filaProcessoSaida
    ↓
eventos OUT
```

e procura o evento com menor tempo.

### Importante

`nextEvent()` **não deve executar o evento**.

Sua responsabilidade é apenas responder:

```text
"Qual evento deve acontecer primeiro?"
```

A execução ocorre posteriormente em:

```text
eventoChegada()
```

ou:

```text
eventSaida()
```

### Ponto ainda em revisão

Os tempos utilizados atualmente possuem representações diferentes:

```text
tempoChegada → intervalo de chegada
tempoSaida   → duração do atendimento
escalonador  → horário agendado
TG           → tempo atual da simulação
```

É necessário padronizar a comparação para que o `nextEvent()` trabalhe corretamente com **instantes absolutos da simulação**.

---

## `eventoChegada(Process process)`

Processa um evento de chegada.

Fluxo esperado:

```text
Processo chega
    ↓
Existe espaço?
    ↓
SIM → entra no sistema
    ↓
Existe servidor livre?
    ↓
SIM → inicia atendimento
    ↓
Calcula tempo de atendimento
    ↓
Agenda OUT
```

Caso não exista espaço disponível:

```text
Processo → filaPerda
```

Quando o processo inicia atendimento, ele também deve:

* ocupar um servidor;
* possuir um tempo de saída;
* entrar em `filaProcessoSaida`;
* possuir seu evento `OUT` registrado no `escalonador`.

---

## `eventSaida(Process process)`

Processa o término do atendimento de um processo.

Fluxo esperado:

```text
Processo termina atendimento
        ↓
Libera servidor
        ↓
Existe processo aguardando?
        ↓
SIM → seleciona próximo processo
        ↓
Coloca processo no servidor
        ↓
Calcula novo atendimento
        ↓
Agenda novo OUT
```

Essa função ainda precisa ser validada principalmente no cenário em que existem processos aguardando na fila.

---

## `tempoSaida()`

Gera o tempo de atendimento de um processo utilizando um número pseudoaleatório.

Atualmente o intervalo utilizado é:

```text
1 ≤ tempo de atendimento ≤ 2
```

O valor representa a **duração do atendimento**, e não necessariamente o instante absoluto da saída.

---

## `tempoChegada()`

Gera um intervalo pseudoaleatório entre chegadas de processos.

O valor gerado representa a **duração até a próxima chegada**.

Portanto, os tempos de chegada absolutos precisam ser acumulados durante a geração dos processos.

---

## `getServidoresLivres()`

Percorre os servidores e contabiliza quantos estão disponíveis.

Um servidor com valor `0` é considerado livre.

---

## `acumalaTempo()`

Armazena os intervalos de tempo utilizados pela simulação em `deltaTempo`.

---

# Métodos de log

### `log()`

Exibe o estado atual da simulação.

### `filasStatus()`

Exibe:

* fila de processamento;
* fila de perdas;
* lista de eventos.

### `tempoStatus()`

Exibe:

* `TG`;
* valores de `deltaTempo`.

### `escalonadorStatus()`

Exibe os eventos atualmente registrados no escalonador.

---

# Pontos de atenção

## 1. Representação do tempo

Este é atualmente o **principal ponto de revisão da simulação**.

É necessário diferenciar:

```text
Tempo de chegada
    ↓
intervalo até a próxima chegada

Tempo de atendimento
    ↓
duração do atendimento

Tempo do evento
    ↓
instante absoluto em que o evento acontece

TG
    ↓
instante atual da simulação
```

Por exemplo, se os intervalos de chegada forem:

```text
1.0
1.5
2.0
```

os instantes absolutos podem ser:

```text
Processo 1 → 1.0
Processo 2 → 2.5
Processo 3 → 4.5
```

O objetivo é evitar comparar diretamente uma duração com um instante absoluto.

---

## 2. Atualização do `TG`

O `TG` deve representar o **instante atual da simulação**.

Quando um evento for selecionado:

```text
TG = tempo absoluto do evento
```

Assim, a simulação consegue avançar cronologicamente.

Deve-se evitar somar novamente ao `TG` um valor que já representa um instante absoluto.

---

## 3. Seleção cronológica dos eventos

O principal requisito do escalonador é:

```text
evento com menor tempo absoluto
        ↓
próximo evento executado
```

Exemplo:

```text
Processo A → IN  = 5
Processo B → OUT = 3
Processo C → IN  = 4
```

A ordem correta deve ser:

```text
B OUT
C IN
A IN
```

Esse comportamento depende de todos os eventos utilizarem a mesma referência temporal.

---

## 4. Entrada no escalonador

Revisar exatamente **quando um processo entra no `escalonador`**.

Um processo deve ser inserido quando possuir um evento futuro que precisa ser executado.

Exemplo:

```text
Processo entra no servidor
        ↓
Tempo de atendimento calculado
        ↓
Instante da saída calculado
        ↓
OUT é agendado
        ↓
escalonador recebe o evento
```

---

## 5. Saída dos processos

Revisar `eventSaida()`.

Após um processo sair:

1. O servidor deve ser liberado.
2. O processo deve deixar a lista de processos com `OUT` pendente.
3. O próximo processo aguardando deve ser selecionado.
4. O processo selecionado deve ocupar o servidor.
5. Um novo tempo de atendimento deve ser calculado.
6. Um novo `OUT` deve ser agendado.

---

## 6. Controle dos servidores

Verificar se `servidores` representa corretamente a ocupação.

Exemplo:

```text
Servidor 0 → 15
Servidor 1 → 0
```

Significa:

```text
Servidor 0 → ocupado pelo processo 15
Servidor 1 → livre
```

Também deve ser garantido que cada processo ocupe apenas um servidor.

---

## 7. Processos perdidos

Um processo colocado em `filaPerda` deve ter seu fluxo encerrado.

Deve-se garantir que processos perdidos:

* não ocupem servidores;
* não entrem em `filaProcessoSaida`;
* não sejam adicionados ao `escalonador`;
* não sejam selecionados novamente pelo `nextEvent()`.

---

## 8. Capacidade da fila

`TAM_MAX` representa a capacidade da fila de processamento.

É importante diferenciar:

```text
TAM_MAX
    ↓
capacidade da fila

QTD_SERVIDORES
    ↓
quantidade de servidores disponíveis
```

São recursos diferentes da simulação.

---

## 9. Teste com fila cheia

A simulação ainda precisa ser validada em situações onde os processos chegam mais rapidamente do que os servidores conseguem atendê-los.

Um cenário esperado seria:

```text
P1 → servidor
P2 → servidor
P3 → fila
P4 → fila
P5 → perda
```

Quando um servidor for liberado:

```text
P1 sai
 ↓
P3 entra no servidor
```

Esse cenário deve ser utilizado para validar `eventSaida()` e a transferência da fila para os servidores.

---

# Próximas revisões sugeridas

> Esta seção histórica descrevia a implementação antiga. O simulador atual usa `PriorityQueue<EventoSimulacao>` no lugar de `nextEvent()` e `escalonador`.

A implementação atual foi revisada nestes pontos:

* [x] Corrigir e validar a representação dos tempos de chegada.
* [x] Garantir que os tempos de chegada sejam acumulados corretamente.
* [x] Padronizar eventos utilizando instantes absolutos.
* [x] Corrigir e validar o avanço do `TG`.
* [x] Validar a seleção do menor evento na fila de prioridade.
* [x] Substituir o `escalonador` por eventos cronológicos com origem e destino.
* [x] Remover eventos executados com `PriorityQueue.poll()`.
* [x] Revisar a ocupação e liberação dos servidores.
* [x] Revisar a transferência da fila para os servidores após um `OUT`.
* [x] Garantir que processos perdidos não sejam processados novamente.
* [x] Criar testes básicos para o LCG e a ordem cronológica.
* [x] Validar manualmente a ordem cronológica durante a execução.

---

# Objetivo da implementação

Ao final, a simulação deverá representar corretamente:

```text
             CHEGADA
                ↓
       ┌────────────────┐
       │  Há espaço?    │
       └───────┬────────┘
           SIM │ NÃO
               │
               ↓
             FILA ─────────→ PERDA
               │
               ↓
     Servidor disponível?
          │           │
         SIM         NÃO
          │           │
          ↓           ↓
     ATENDIMENTO     AGUARDA
          │
          ↓
       EVENTO OUT
          │
          ↓
   Libera servidor
          │
          ↓
 Existe processo na fila?
          │
         SIM
          ↓
 Próximo processo entra
          │
          ↓
 Novo atendimento
          │
          ↓
 Novo evento OUT
```

O `nextEvent()` permanece como o componente responsável exclusivamente por identificar **qual evento cronológico deve ser tratado a seguir**, enquanto `eventoChegada()` e `eventSaida()` são responsáveis pela execução das regras correspondentes a cada tipo de evento.
