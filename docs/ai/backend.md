# Backend

Spring Boot 4.0.6, Java 26, Maven (`mvnw`). GAV: `com.stockai:stock-ai-analyzer-backend:0.0.1-SNAPSHOT`.

## Comandos

```bash
./mvnw spring-boot:run          # inicia o servidor
./mvnw test                     # todos os testes
./mvnw test -Dtest=NomeTest     # teste específico
./mvnw package -DskipTests      # build sem testes
mvn dependency:resolve          # rodar após alterar pom.xml
mvn clean compile               # rodar após qualquer alteração estrutural
```

## Dependências principais (`pom.xml`)

| groupId:artifactId | versão | uso |
|---|---|---|
| spring-boot-starter-data-jpa | (gerenciada pelo parent 4.0.6) | JPA/Hibernate |
| spring-boot-starter-data-redis | idem | cache |
| spring-boot-starter-webmvc | idem | REST |
| spring-boot-starter-websocket | idem | presente, mas efetivamente não usado (ver `anti-patterns.md`) |
| org.postgresql:postgresql | idem | driver |
| dev.langchain4j:langchain4j-ollama | 1.11.0 | embeddings locais |
| dev.langchain4j:langchain4j-open-ai | 1.11.0 | cliente OpenAI-compatible (Gemini + Groq) |
| dev.langchain4j:langchain4j-pgvector | 1.10.0-beta18 | vector store — versão pinada separada, ciclo de release próprio |
| spring-boot-starter-security | idem | filtro de segurança |
| spring-boot-starter-oauth2-client | idem | login Google |
| io.jsonwebtoken:jjwt-api/impl/jackson | 0.12.6 | JWT |
| spring-boot-starter-flyway | (gerenciada pelo parent 4.0.6) | migração de schema — starter próprio do Spring Boot 4, sem ele Flyway não roda automaticamente mesmo com `flyway-core` no classpath |
| org.flywaydb:flyway-database-postgresql | 11.14.1 (gerenciada pelo parent, resolvida via `mvn dependency:tree`) | suporte a Postgres — obrigatório desde Flyway 10, `flyway-core` sozinho não basta |

## Regras Maven obrigatórias

- Nunca adicionar dependência sem checar versão exata no Maven Central antes.
- Sempre rodar `mvn dependency:resolve` após alterar `pom.xml`.
- Nunca unificar versões de módulos LangChain4j numa propriedade única — eles têm ciclos de release diferentes (prova viva: `langchain4j-pgvector` é 1.10.0-beta18 enquanto os outros são 1.11.0).
- Versão não encontrada → pesquisar a mais recente disponível antes de tentar outra.

## Banco de dados

Estratégia: **Flyway**, migrations em `backend/src/main/resources/db/migration/V{n}__*.sql`. `spring.jpa.hibernate.ddl-auto: validate` — Hibernate só confere que as entidades batem com o schema aplicado, nunca altera o banco. Isso substitui o `ddl-auto: update` usado até 2026-08-06 (ver `decisions.md`).

`V1__baseline_schema.sql` é a baseline exata do schema que existia sob `ddl-auto: update` até a migração. Config em `application.yml`: `spring.flyway.baseline-on-migrate: true` + `baseline-version: 1` — banco já existente (schema não vazio, sem `flyway_schema_history`) é marcado como já estando na v1 sem reexecutar o script; ambiente novo/vazio roda V1 normalmente. Verificado localmente: schema vazio → V1 aplica limpo → `ddl-auto: validate` passa sem `SchemaManagementException` → segunda execução detecta "up to date", não reaplica.

**Toda mudança de entidade daqui pra frente exige uma migration `V{n+1}__*.sql` nova** — não editar `V1__baseline_schema.sql` retroativamente. Ver `playbooks/database-change.md`.

`stock_embeddings` continua fora do Flyway — gerida pelo `PgVectorEmbeddingStore` do LangChain4j (`createTable(true)`).

Entidades JPA (detalhe completo em `docs/PROJECT_DOCUMENTATION.md` seção 5):
- `users` (`UserEntity`) — sem senha, sem role/status, upsert por `googleId`.
- `portfolio_items` (`PortfolioItem`) — `@ManyToOne` para `UserEntity`, unique `(user_id, ticker)`.
- `score_history` (`ScoreHistoryEntity`) — índice composto `(ticker, analysisDate)`, `ticker` é string solta sem FK.
- `stock_alerts` (`StockAlertEntity`) — sem índice além da PK.
- `stock_embeddings` — **não é entidade JPA**, criada/gerida pelo `PgVectorEmbeddingStore` do LangChain4j (`createTable(true)`), fora do controle do Hibernate. Metadados `ticker`/`date`/`type` como colunas dedicadas (`COLUMN_PER_KEY`).

Repositórios: só derived queries (nenhum `@Query` customizado no projeto todo). Antes de adicionar um método novo, checar se um derived-query name já resolve.

## Configuração (`application.yml`)

Arquivo único, sem profiles (`application-dev.yml`/`application-prod.yml` não existem). Blocos: `spring.datasource`, `spring.jpa`, `spring.data.redis`, `spring.task.scheduling.pool.size: 2`, `spring.security.oauth2.client.registration.google`, `pgvector.*`, `python.sidecar.*` + `python.script.*` (paths dos scripts), `huggingface.token` (configurado, **sem consumidor no código atual**), `ollama.*`, `gemini.api-key`, `groq.api-key`, `embedding.store.table`, `jwt.secret` (sem default — falha no boot se ausente).

`server.port` não é setado — default 8080. Desde 2026-08-07, `app.cors.allowed-origins` (`CORS_ALLOWED_ORIGINS`) e `app.frontend.base-url` (`FRONTEND_BASE_URL`) externalizam CORS e o redirect pós-OAuth2 — default continua `http://localhost:4200` em dev. Ver `decisions.md`.

## Regras de qualidade

- Sempre `mvn clean compile` após alteração estrutural — corrigir erro antes de continuar.
- Nunca usar uma classe sem confirmar que ela existe na versão declarada da dependência.
- **Spring Boot 4 usa `tools.jackson.*`, não `com.fasterxml.jackson.*`** — erro comum de import ao copiar código de exemplos antigos.

## Testes existentes

3 classes só: `StockAiAnalyzerBackendApplicationTests` (smoke), `AnalysisParserTest` (parsing/clamp/threshold/sanitização), `SectorBenchmarksTest` (formatação/omissão de métrica/rejeição de amostra pequena). `ComparisonService`, `BacktestService`, `SectorClassifier` sem teste algum.
