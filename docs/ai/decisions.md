# Decisões arquiteturais (ADR-lite)

Registro leve de decisões não-óbvias — cada uma existe porque a alternativa óbvia foi tentada/considerada e rejeitada por um motivo concreto. Não reverter sem entender o motivo.

## `scoreGeral` sempre recalculado em Java

**Decisão**: nunca usar o valor de `scoreGeral` retornado pelo LLM.
**Motivo**: LLMs erram aritmética simples de forma inconsistente; confiar nisso geraria scores incoerentes com as 6 dimensões individuais.
**Ver também**: `financial-rules.md`.

## RAG exclui análises passadas (`type=analysis`)

**Decisão**: contexto de RAG usa só `historical_fundamentals`, nunca `analysis`.
**Motivo**: recuperar análises passadas criaria feedback loop — o LLM ancoraria no próprio score anterior em vez de reavaliar fundamentos de forma independente, contaminando a série histórica.
**Ver também**: `rag.md`.

## Temperature 0 nos dois LLMs

**Decisão**: Gemini e Groq configurados com `temperature(0.0)`.
**Motivo**: scoring precisa ser determinístico; variância de amostragem dispararia alertas de mudança de score por ruído estatístico, não por fato novo.

## CVM como fonte primária de fundamentos, yfinance como fallback

**Decisão**: overlay dos dados CVM sobre o yfinance, não o contrário.
**Motivo**: múltiplos do yfinance para tickers B3 são frequentemente errados ou defasados (validado por checagem cruzada: lucro LTM da VALE3 calculado via CVM bateu com `netIncomeToCommon` do yfinance ao milhar).

## Sidecar Python (FastAPI) em vez de reescrever coleta em Java

**Decisão**: manter yfinance/pandas em Python, expostos via HTTP local persistente, em vez de portar para uma lib Java equivalente.
**Motivo**: não existe equivalente maduro de yfinance na JVM; reescrever seria caro e arriscado. Sidecar persistente elimina o custo de cold-start (medido: ~1,5s quente vs 3,5s+ frio via spawn de processo).
**Trade-off aceito**: dois runtimes para operar/deployar (Java + Python), sem Dockerfile ainda para nenhum dos dois (ver `anti-patterns.md`).

## Flyway substituindo `ddl-auto: update` (resolvido em 2026-08-06)

**Decisão**: migrar de `ddl-auto: update` para Flyway com `ddl-auto: validate`.
**Motivo original da dívida**: velocidade de iteração em fase de protótipo — aceito conscientemente, não por acidente (ver histórico abaixo).
**Como foi resolvido**: `spring-boot-starter-flyway` + `flyway-database-postgresql` (Spring Boot 4 exige o starter — `flyway-core` sozinho no classpath não roda migração automática). `V1__baseline_schema.sql` reproduz exatamente o schema que `ddl-auto: update` já tinha gerado. `spring.flyway.baseline-on-migrate: true` + `baseline-version: 1` cobre os dois casos: banco de dev já existente é marcado na v1 sem reexecutar o script; ambiente novo roda V1 normalmente. Verificado: schema vazio → V1 aplica → Hibernate `validate` passa sem erro → idempotente numa segunda execução.
**Consequência**: toda mudança de entidade agora exige uma migration `V{n+1}__*.sql` nova — `ddl-auto` não altera mais o banco. Ver `playbooks/database-change.md` e `backend.md`.

### Histórico da decisão original (`ddl-auto: update` por ora)
Documentado por completude — decisão já superada acima, mas o raciocínio original continua válido para decisões futuras do mesmo tipo (aceitar dívida conscientemente, com critério explícito de quando resolver): "sempre sugerir Flyway quando mudanças estruturais forem frequentes" (regra que motivou a resolução acima) é o tipo de critério que vale registrar de novo da próxima vez que uma dívida semelhante for aceita de propósito.

## URLs configuráveis substituindo hardcode de localhost (resolvido em 2026-08-07)

**Decisão**: externalizar CORS, redirect pós-OAuth2 e a URL base da API do frontend, em vez de deixá-las fixas em `localhost:4200`/`localhost:8080`.
**Motivo original da dívida**: fase de protótipo, um único ambiente (dev local) — aceito conscientemente (ver `anti-patterns.md`, item resolvido).
**Como foi resolvido**:
- Backend: `SecurityConfig.corsConfigurationSource()` e `OAuth2SuccessHandler.onAuthenticationSuccess()` passaram a ler `app.cors.allowed-origins` (`CORS_ALLOWED_ORIGINS`, lista separada por vírgula) e `app.frontend.base-url` (`FRONTEND_BASE_URL`) via `@Value`, default `http://localhost:4200` — comportamento de dev inalterado.
- Frontend: `src/environments/environment.ts` (dev) e `environment.prod.ts` (prod) trocados por `fileReplacements` no `angular.json`. Prod usa caminhos **relativos** (`apiUrl: '/api'`, `authUrl: ''`) assumindo backend na mesma origem via reverse proxy — não fixa um domínio de produção em tempo de build, o que teria só trocado um hardcode por outro.
- `stock.service.ts`, `portfolio.service.ts`, `auth.service.ts`, `simulator.ts` — as 4 URLs absolutas trocadas por `environment.apiUrl`/`environment.authUrl`.
**Escopo deliberadamente excluído**: URLs de vendor fixo (Gemini, Groq, CVM, BCB, Google News RSS) continuam hardcoded nos scripts Python e no `EmbeddingStoreConfig` — são endpoints públicos que não variam por ambiente de deploy; externalizar geraria indireção sem ganho real.
**Verificado**: `mvn clean compile` limpo; `ng build --configuration production` gera bundle com `apiUrl:"/api"`, sem `localhost:8080` no output; suíte backend 13/14 (o teste que falha exige Postgres local rodando, não regressão desta mudança).

## Benchmarks setoriais dinâmicos com fallback estático

**Decisão**: calcular medianas reais de pares via CVM+market cap antes de recorrer a faixa hardcoded.
**Motivo**: sem benchmark explícito, Gemini e Groq inventam médias setoriais diferentes um do outro a partir de memória de treino — inconsistência entre modelos que o benchmark dinâmico elimina.

## Rótulos descritivos em vez de imperativos (ATRATIVO/NEUTRO/CAUTELA/DESFAVORÁVEL)

**Decisão**: nunca usar COMPRAR/VENDER/MANTER como rótulo de recomendação.
**Motivo**: Resolução CVM 20/2021 restringe recomendação de investimento a analistas credenciados — é uma restrição regulatória, não estética.
**Ver também**: `financial-rules.md`.

## Tratamento diferenciado para bancos/seguradoras

**Decisão**: suprimir `totalDebt`/`debtToEquity` no prompt quando a CVM não mostra conta de empréstimos/financiamentos no balanço de uma financeira.
**Motivo**: captação e depósitos bancários não são dívida corporativa; o yfinance trata isso como se fosse, distorcendo `gestaoRisco` para o setor inteiro (ex.: ITUB4 aparecia com "dívida" de R$ 1,15 tri).

## Single-flight por ticker (lock em memória)

**Decisão**: `ConcurrentHashMap<String, ReentrantLock>` por ticker em `StockAnalysisService`.
**Motivo**: evitar que requisições simultâneas do mesmo ticker disparem múltiplas chamadas de LLM (custo real por chamada) — a segunda requisição espera e reusa o resultado da primeira.
**Limite conhecido**: lock em memória de processo único — não coordena entre múltiplas instâncias do backend se o sistema escalar horizontalmente. Reavaliar (lock distribuído via Redis, por exemplo) antes de rodar mais de uma instância do backend.
