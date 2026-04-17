## ADDED Requirements

### Requirement: Docker Compose com toda a infraestrutura local
O sistema SHALL fornecer um `docker-compose.yml` que inicializa Kafka, PostgreSQL, Jaeger, Prometheus e Grafana com um único comando. A infraestrutura SHALL estar pronta para uso em menos de 60 segundos em hardware moderno.

#### Scenario: Inicialização completa da infraestrutura
- **WHEN** `docker compose up -d` é executado na raiz do projeto
- **THEN** os serviços Kafka, PostgreSQL, Jaeger, Prometheus e Grafana estão disponíveis e saudáveis

#### Scenario: Tópicos Kafka criados automaticamente
- **WHEN** o Kafka inicializa
- **THEN** os tópicos `payflow.account.events` (6 partições), `payflow.transfer.events` (6 partições), `payflow.transfer.commands` (6 partições) e `payflow.dlq` (1 partição) são criados automaticamente

### Requirement: Dashboard Grafana pré-configurado
O sistema SHALL incluir um dashboard Grafana importável que exibe as métricas de negócio do PayFlow sem configuração manual.

#### Scenario: Dashboard disponível após inicialização
- **WHEN** o Grafana inicializa com os volumes configurados
- **THEN** o dashboard `payflow-overview` está disponível e conectado ao Prometheus como datasource

#### Scenario: Dashboard exibe métricas de transferência
- **WHEN** transferências são executadas e o dashboard é aberto no Grafana
- **THEN** os painéis de transferências/segundo, taxa de falha de saga e duração p99 exibem dados

### Requirement: CI/CD com GitHub Actions
O sistema SHALL ter pipelines de CI/CD separadas para Spring e Micronaut, executando testes com Testcontainers (Docker-in-Docker) e publicando imagens Docker no GHCR apenas em main.

#### Scenario: Pipeline de testes passa em pull request
- **WHEN** um pull request é aberto
- **THEN** a pipeline executa `mvn test` com Testcontainers e falha se qualquer teste falhar

#### Scenario: Pipeline de benchmark executa apenas em main
- **WHEN** um commit é feito na branch main
- **THEN** a pipeline executa os benchmarks k6 e commita os resultados em `benchmarks/results/`

#### Scenario: Cobertura de testes publicada
- **WHEN** os testes são executados na pipeline
- **THEN** o relatório Jacoco é enviado para o Codecov e o badge de cobertura é atualizado no README

### Requirement: README executável em menos de 5 minutos
O README SHALL conter as instruções mínimas para qualquer engenheiro sênior executar o projeto localmente sem conhecimento prévio do repositório. O tempo do `git clone` até os serviços respondendo SHALL ser inferior a 5 minutos.

#### Scenario: Execução local seguindo apenas o README
- **WHEN** os comandos do README são executados em sequência em ambiente com Java 21, Maven e Docker instalados
- **THEN** os serviços Account e Transfer (Spring) estão respondendo e a infraestrutura está de pé

#### Scenario: README contém benchmarks documentados
- **WHEN** o README é consultado
- **THEN** há uma tabela comparativa com startup time, memória RSS e latência p99 para Spring Boot e Micronaut, com contexto de hardware e JVM flags utilizados
