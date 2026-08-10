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
| com.google.errorprone:error_prone_core | 2.50.0 (annotation processor, não dependency normal) | análise estática em modo WARN — ver `coding-standards.md`. Versão fixa: é a única que entende os internals do javac do Java 26 |
| com.github.spotbugs:spotbugs-maven-plugin | 4.9.3.0 | declarado mas **inerte** — ASM interno não lê bytecode Java 26, ver `coding-standards.md`/`decisions.md` |

`backend/.mvn/jvm.config` — `--add-exports`/`--add-opens` pros internals do javac, exigidos pelo Error Prone. Precisa estar aí (JVM do próprio Maven), não em `compilerArgs` — `--add-opens` não tem efeito nenhum passado pro javac.

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
- `stock` (`Stock`, `com.stockai.stock`) — desde 2026-08-07 (ver `decisions.md`). Identidade canônica de ativo: `id`, `ticker` (unique, forma canônica sem `.SA` — `TickerNormalizer.canonical`), `createdAt`. Criada sob demanda via `StockRepository.findOrCreate(rawTicker)`, nunca via `new Stock(...)` fora do pacote (construtor package-private).
- `portfolio_items` (`PortfolioItem`) — `@ManyToOne` para `UserEntity` (LAZY) e `Stock` (EAGER), unique `(user_id, stock_id)`.
- `score_history` (`ScoreHistoryEntity`) — `@ManyToOne` EAGER para `Stock`, índice composto `(stock_id, analysis_date)`.
- `stock_alerts` (`StockAlertEntity`) — `@ManyToOne` EAGER para `Stock`, sem índice além da PK/FK.
- `analysis_audit` (`AnalysisAudit`) — desde 2026-08-07 (ver `decisions.md`). FK simples `scoreHistoryId` (não associação JPA — tabela write-heavy/read-rare). Guarda prompt exato, resposta bruta do LLM, `reasoning` (campo `"analise"`, pedido no prompt mas não extraído até essa mudança) e `explicacao` das 6 dimensões. Escrita best-effort via `AnalysisAuditService`.
- `stock_embeddings` — **não é entidade JPA**, criada/gerida pelo `PgVectorEmbeddingStore` do LangChain4j (`createTable(true)`), fora do controle do Hibernate. Metadados `ticker`/`date`/`type` como colunas dedicadas (`COLUMN_PER_KEY`) — continua string solta, sem FK possível pro mecanismo de vetor; deliberadamente fora do escopo da entidade `Stock`.

`Stock` é EAGER nas 3 entidades (não LAZY como `PortfolioItem.user`) — é uma tabela pequena e o ticker é lido em praticamente todo call site; LAZY exigiria `@Transactional` em métodos que hoje não têm (ex.: `ScoreAlertService.getAlertsByTicker`), risco real de `LazyInitializationException`.

`StockAnalysisService`, `ComparisonService`, `BacktestService`, `SectorClassifier`, `PythonDataGateway` continuam recebendo ticker como `String` — só manipulam ticker em trânsito por requisição, nunca persistem diretamente. Não foram tocados pela introdução de `Stock` (ver `decisions.md`).

Repositórios: só derived queries (nenhum `@Query` customizado no projeto todo). Antes de adicionar um método novo, checar se um derived-query name já resolve.

## Score Confidence (desde 2026-08-07)

`ScoreConfidence`/`ScoreConfidenceCalculator` (função pura, `com.stockai.analysis`) — meta-score 0-10 de qualidade do dado (não confundir com `scoreGeral`, que mede qualidade da empresa). Agrega `fundamentalsSource`, `SentimentResult.source`/`confidence`, presença de `TechnicalIndicators`, e `SectorBenchmarks.describeWithMeta().dynamic()`. Exposto em `AnalysisResponse.confidence`. Ver `decisions.md` pra fórmula exata.

## Gate de plausibilidade pós-score (desde 2026-08-08)

`ScorePlausibilityGate`/`PlausibilitySignal` (função pura, `com.stockai.analysis`) — roda depois do parse da resposta do LLM e do cálculo de score, só **sinaliza** inconsistência numérica entre dimensões/`scoreGeral` e os dados que as sustentam (`StockAnalysis`, `StockFundamentals`, `ScoreConfidence`, `SectorType`). Nunca corrige nem bloqueia — não é lido de volta pelo LLM nem altera `AnalysisResponse`. As 6 regras espelham travas que já existem como texto na rubrica do prompt (ver `prompts.md`), verificadas em vez de só pedidas. Validado por `financial-analyst` (thresholds, exceção setorial) e `backend-architect` (ponto de integração, tipo de saída) antes da implementação — ver `decisions.md`.

6ª regra (`FUNDAMENTOS_ALTO_LUCRO_INCONSISTENTE`, desde 2026-08-08): generaliza a lógica da Regra 5 (antes só VAREJO) — bucket 8-10 de Fundamentos exige "lucro crescente nos trimestres" pela rubrica, qualquer setor. Reusa o mesmo `lucroInconsistente()` sem sector gate.

Persistido em `analysis_audit.plausibility_signals` (`V4__add_plausibility_signals_to_analysis_audit.sql`, coluna `TEXT`, nomes do enum separados por vírgula, `NULL` se nenhum sinal) e logado em `WARN` quando não-vazio. Sem endpoint de leitura ainda — mesmo escopo mínimo de `analysis_audit`.

**Confirmado em produção, 2026-08-08**: auditoria de validação financeira real (10 tickers, backend real + Gemini real) achou MGLU3 com `retornoAcionista.score=9.0` apesar de lucro trimestral inconsistente — checagem direta em `analysis_audit.plausibility_signals` confirmou que a Regra 5 disparou (`VAREJO_RETORNO_ALTO_SEM_LUCRO_CONSISTENTE`), sem alterar o score, exatamente como desenhado. Primeira confirmação empírica do gate funcionando em análise real, não só em teste unitário.

Consequência de integração: o cálculo de `ScoreConfidence` em `StockAnalysisService.doAnalyze` foi movido de depois de `saveScore` pra antes (os 4 inputs já existiam desde a Fase 1/2 — era ordenação incidental, não dependência real), pra alimentar o gate antes da persistência.

## Explicação de mudança de score (desde 2026-08-07)

`ScoreChangeExplanation`/`ScoreChangeCalculator` (função pura) — compara a análise atual com o registro anterior em `score_history` (buscado **antes** de `saveScore`), aponta a dimensão de maior `|delta|` e reusa a `explicacao` que o LLM já dá pra ela. `null` na primeira análise de um ticker. Exposto em `AnalysisResponse.scoreChange`. Diferente de `StockAlert`/`ScoreAlertService` (que só dispara acima de threshold 1.5, sem explicar o porquê) — isso mostra sempre que há histórico. Ver `decisions.md`.

## Padrões transacionais

`@Transactional` hoje só em 3 services: `AnalysisAuditService`, `ScoreHistoryService`, `PortfolioService` — propagação default (`REQUIRED`), sem `@Version`/lock otimista em nenhuma entidade (nenhum conflito concorrente a resolver ainda).

`AnalysisAuditService.save` já é best-effort (try/catch interno, log + segue, nunca propaga) — mesmo efeito prático de rodar a escrita fora da transação principal, só que sem usar `REQUIRES_NEW` explícito. Vale considerar `@Transactional(propagation = REQUIRES_NEW)` + `@TransactionalEventListener(phase = AFTER_COMMIT)` se esse padrão "escrita auxiliar que não pode derrubar o fluxo principal" se repetir em outro lugar — hoje só existe esse um caso, então não há necessidade de generalizar ainda.

Efeito colateral externo (email, webhook, publish) dentro de uma `@Transactional` que pode dar rollback: nunca disparar direto — publicar `ApplicationEvent` e escutar em `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`. Não há caso disso no projeto hoje (sem envio de email/webhook), mas é o padrão a seguir se aparecer.

Spring Framework 7 (Boot 4) move retry pra core (`@Retryable` + `@EnableResilientMethods`, pacote `org.springframework.resilience.annotation`), substituindo Spring Retry. Não usado hoje — não há `@Version`/otimista pra ter conflito pra retentar. Se um `@Version` for adicionado a alguma entidade no futuro (ex.: `PortfolioItem` sob edição concorrente), essa é a ferramenta certa, não `spring-retry` (dependência separada, redundante com o que já vem no framework).

## Configuração (`application.yml`)

Arquivo único, sem profiles (`application-dev.yml`/`application-prod.yml` não existem). Blocos: `spring.datasource`, `spring.jpa`, `spring.data.redis`, `spring.task.scheduling.pool.size: 2`, `spring.security.oauth2.client.registration.google`, `pgvector.*`, `python.sidecar.*` + `python.script.*` (paths dos scripts), `ollama.*`, `gemini.api-key`, `groq.api-key`, `embedding.store.table`, `jwt.secret` (sem default — falha no boot se ausente). `huggingface.token` **removido em 2026-08-07** — nunca teve consumidor Java; `HUGGINGFACE_TOKEN` (FinBERT, ver `decisions.md`) é lido direto do `os.environ` pelo sidecar Python, fora do Spring.

`server.port` não é setado — default 8080. Desde 2026-08-07, `app.cors.allowed-origins` (`CORS_ALLOWED_ORIGINS`) e `app.frontend.base-url` (`FRONTEND_BASE_URL`) externalizam CORS e o redirect pós-OAuth2 — default continua `http://localhost:4200` em dev. Ver `decisions.md`.

## Regras de qualidade

- Sempre `mvn clean compile` após alteração estrutural — corrigir erro antes de continuar.
- Nunca usar uma classe sem confirmar que ela existe na versão declarada da dependência.
- **Spring Boot 4 usa `tools.jackson.*`, não `com.fasterxml.jackson.*`** — erro comum de import ao copiar código de exemplos antigos.

## Testes existentes

3 classes só: `StockAiAnalyzerBackendApplicationTests` (smoke), `AnalysisParserTest` (parsing/clamp/threshold/sanitização), `SectorBenchmarksTest` (formatação/omissão de métrica/rejeição de amostra pequena). `ComparisonService`, `BacktestService`, `SectorClassifier` sem teste algum.
