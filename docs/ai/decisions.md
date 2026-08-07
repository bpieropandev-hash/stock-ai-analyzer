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

## Entidade `Stock` canônica substituindo ticker solto (resolvido em 2026-08-07)

**Decisão**: criar `Stock` (`com.stockai.stock`) — `id`, `ticker` (unique, canônico), `createdAt` — e trocar a coluna `ticker` de `portfolio_items`, `score_history` e `stock_alerts` por FK `stock_id`. Criação sob demanda via `StockRepository.findOrCreate`, nunca cadastro manual.
**Motivo**: as 3 tabelas guardavam ticker como string sem constraint entre si — sem integridade referencial, sem lugar único pra pendurar nome/setor/CNPJ/tipo de ativo quando o domínio crescer (FIIs, ETFs, BDRs, cripto). GPT (ver histórico da sessão) argumentou que adiar isso fica mais caro quanto mais o domínio cresce — concordei e priorizei essa entidade antes do JWT.
**Bug real descoberto no levantamento pré-implementação**: `portfolio_items`/`stock_alerts` guardavam ticker **sem** sufixo (`PETR4`), `score_history` guardava **com** sufixo `.SA` do yfinance (`PETR4.SA`) — mesmo ativo, dois formatos, nunca colidiu porque não havia FK unindo as tabelas. `TickerNormalizer.canonical()` (strip `.SA` + uppercase) virou o único ponto de normalização — antes estava duplicado solto em pelo menos 4 arquivos (`StockAnalysisService`, `SectorClassifier`, `BacktestService`, controllers).
**Escopo deliberadamente mínimo**: `Stock` só tem `ticker`, sem colunas de nome/setor/CNPJ/tipo ainda — nada no código popula esses dados hoje, adicionar colunas vazias violaria "sem implementação pela metade". A entidade existe pra dar lugar pra isso crescer depois, não pra antecipar.
**Escopo deliberadamente excluído**: `StockAnalysisService`, `ComparisonService`, `BacktestService`, `SectorClassifier`, `PythonDataGateway`, embeddings pgvector — todos continuam com `ticker: String`. Só manipulam ticker em trânsito por requisição, nunca persistem via JPA; forçar `Stock` neles seria acoplamento sem ganho. `stock_embeddings` (metadado pgvector do LangChain4j) também fica de fora — mecanismo de vetor não suporta FK relacional.
**Design técnico**: `@ManyToOne(fetch = EAGER)` para `Stock` nas 3 entidades (não LAZY como `PortfolioItem.user`) — `Stock` é pequeno e o ticker é lido em quase todo call site; LAZY exigiria `@Transactional` em métodos que hoje não têm (`ScoreAlertService.getRecentAlerts`/`getAlertsByTicker`), risco real de `LazyInitializationException`. Entidades mantêm `getTicker(): String` delegando pra `stock.getTicker()` — nenhum DTO, controller ou o frontend precisou mudar. Repositórios usam propriedade aninhada (`findByStock_Ticker`) — Spring Data resolve `stock_id` via JOIN automaticamente.
**Migration** (`V2__introduce_stock_entity.sql`): cria `stock`, faz backfill via `UNION DISTINCT` normalizado das 3 tabelas, popula `stock_id` via `UPDATE...FROM` com o mesmo `REGEXP_REPLACE` de normalização, torna `stock_id NOT NULL`, remove a coluna `ticker` antiga. Cutover completo numa migration só — aceitável porque é ambiente pessoal/dev, sem usuários reais e sem coluna `ticker` sendo lida por nada fora do Java (nenhuma dependência externa/BI direto no banco).
**Verificado**: dados sintéticos inseridos com os 3 formatos (`PETR4`, `petr4`, `PETR4.SA`) pro mesmo ativo — migration convergiu todos pro mesmo `stock_id`, provando o fix. `mvn clean compile` limpo, app sobe contra Postgres real com `ddl-auto: validate` passando, suíte 14/14 verde.

## JWT TTL reduzido de 7 dias para 24h (resolvido em 2026-08-07)

**Decisão**: `JwtService.TTL_MS` de 7 dias para 24h — sem implementar refresh token ainda.
**Motivo**: token de 7 dias (`anti-patterns.md`) deixava um vazamento (XSS, log de proxy — token vai por query string no redirect OAuth2) explorável por até uma semana. Refresh token/revogação de verdade é mudança maior (endpoint novo, rotação, storage) fora do escopo deste item — 24h reduz a janela de exposição em 7x sem essa complexidade.
**Efeito colateral corrigido na mesma tarefa**: `authInterceptor` (frontend) não tratava 401 — com TTL de 7 dias isso quase nunca era percebido; com 24h, expiração vira evento comum. `authInterceptor` agora limpa o token e redireciona pra `/login` em qualquer 401, em vez de deixar a tela quebrada silenciosamente.
**Trade-off aceito**: sem refresh, usuário reloga a cada 24h (antes: a cada 7 dias) — UX pior, aceito conscientemente até refresh token entrar (item de roadmap separado).
**Ver também**: `anti-patterns.md` (seção Segurança).

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
