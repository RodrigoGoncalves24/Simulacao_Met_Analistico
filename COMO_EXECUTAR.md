# Como executar o simulador

## 1. Pre-requisitos

- Java JDK 17 ou superior.
- Maven 3.8 ou superior.
- SnakeYAML 2.2, instalado automaticamente pelo Maven.
- Um arquivo de modelo no formato YAML.

Para verificar o Java e o Maven no PowerShell:

```powershell
java -version
mvn -version
```

## 2. Arquivos importantes

- `src/Simulador.java`: ponto de entrada da aplicacao.
- `src/YamlModeloLoader.java`: leitura e validacao do modelo YAML.
- `src/GeradorLCG.java`: gerador congruencial linear e contador de aleatorios.
- `src/Fila.java`: estado de cada fila durante a simulacao.
- `src/EventoSimulacao.java`: eventos cronologicos da simulacao.
- `pom.xml`: dependencias e configuracao do build.
- `src/model (1).yml`: modelo de exemplo.

O arquivo que define a rede de filas e o YAML. Para alterar a simulacao, edite `src/model (1).yml` ou crie outro arquivo YAML e informe o caminho na execucao.

## 3. Gerar o executavel

Abra o PowerShell na pasta raiz do projeto e execute:

```powershell
mvn clean package
```

Ao final, o arquivo executavel sera criado em:

```text
target/simulador-rede-filas-1.0.0.jar
```

O `pom.xml` usa o Maven Shade Plugin, portanto o JAR gerado inclui o SnakeYAML e pode ser executado sozinho.

## 4. Executar o modelo de exemplo

```powershell
java -jar target/simulador-rede-filas-1.0.0.jar "src/model (1).yml"
```

Se aparecer `NoClassDefFoundError: org/yaml/snakeyaml/Yaml`, o JAR foi gerado antes da configuracao do Shade Plugin. Execute `mvn clean package` novamente e use o novo JAR.

As aspas sao necessarias porque o nome do arquivo possui espacos.

## 5. Executar outro modelo

Crie ou copie outro arquivo YAML, por exemplo `modelos/minha-rede.yml`, e execute:

```powershell
java -jar target/simulador-rede-filas-1.0.0.jar "modelos/minha-rede.yml"
```

Nao e necessario alterar o codigo Java para mudar a topologia.

## 6. Estrutura do arquivo YAML

### Chegadas

`arrivals` informa o instante da primeira chegada em cada fila. O destino da chegada e a fila indicada pela chave.

```yaml
arrivals:
  Q1: 2.0
```

A fila tambem pode definir `minArrival` e `maxArrival`. Nesse caso, depois de cada chegada, a proxima chegada sera agendada usando um intervalo uniforme nesse intervalo.

### Filas

Cada entrada em `queues` representa uma fila:

```yaml
queues:
  Q1:
    servers: 1
    capacity: 3
    minArrival: 1.0
    maxArrival: 2.0
    minService: 2.0
    maxService: 3.0
```

Campos:

- `servers`: quantidade de servidores em paralelo.
- `capacity`: capacidade total, incluindo espera e atendimento.
- `capacity: 0`: capacidade infinita.
- `minArrival` e `maxArrival`: intervalo entre chegadas. Sao opcionais.
- `minService` e `maxService`: intervalo uniforme do atendimento.

### Rede e roteamento

`network` define os destinos possiveis depois do atendimento:

```yaml
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
```

- `source`: fila de origem.
- `target`: fila de destino.
- `target: -1`: saida para o exterior.
- `probability`: chance da rota ser escolhida.

As rotas sao testadas na ordem em que aparecem no arquivo, usando faixas cumulativas. Para cada fila, a soma das probabilidades nao pode ultrapassar `1.0`. Se a soma ficar abaixo de `1.0`, a diferenca sera completada automaticamente como saida para o exterior.

### Gerador LCG

O gerador pode ser configurado no bloco `lcg`:

```yaml
lcg:
  seed: 1
  a: 1664525
  c: 1013904223
  m: 4294967296
```

A cada uso, o gerador calcula:

```text
X(n+1) = (a * X(n) + c) mod m
numero = X(n+1) / m
```

Chegadas, tempos de atendimento e roteamentos consomem numeros do mesmo gerador. A simulacao encerra ao consumir o 100.000o numero.

## 7. Resultado exibido

Ao terminar, o console informa:

- quantidade de aleatorios consumidos;
- tempo global da simulacao;
- quantidade de saidas para o exterior;
- perdas de cada fila;
- tempo acumulado em cada estado de cada fila;
- probabilidade de cada estado de cada fila.

## 8. Executar os testes basicos

Depois de compilar, execute:

```powershell
java -cp "target/classes;$env:USERPROFILE/.m2/repository/org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar" SimuladorTest
```

O resultado esperado e:

```text
Testes basicos aprovados.
```

## 9. Execucao sem Maven

Caso o Maven nao esteja instalado, e possivel compilar usando o SnakeYAML ja baixado no cache local:

```powershell
$jar = Join-Path $env:USERPROFILE '.m2\repository\org\yaml\snakeyaml\2.2\snakeyaml-2.2.jar'
Remove-Item out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory out | Out-Null
javac -cp $jar -d out src\*.java
java -cp "out;$jar" Simulador "src\model (1).yml"
```

Para executar temporariamente um JAR antigo sem reconstruir:

```powershell
$jar = Join-Path $env:USERPROFILE '.m2\repository\org\yaml\snakeyaml\2.2\snakeyaml-2.2.jar'
java -cp "target/simulador-rede-filas-1.0.0.jar;$jar" Simulador "src\trabalho1.yml"
```

Se o JAR nao existir, execute `mvn dependency:go-offline` ou `mvn package` em um ambiente com Maven e acesso a internet.
