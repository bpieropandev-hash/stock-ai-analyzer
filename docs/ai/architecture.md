# Arquitetura

## Visão geral

Monólito modular Spring Boot (backend) + SPA Angular standalone (frontend) + sidecar Python (FastAPI) para acesso a bibliotecas de dados de mercado sem equivalente maduro na JVM (yfinance/pandas). Não é microsserviços — o sidecar não tem lógica de negócio, só coleta dados.

```
Angular SPA ──HTTP/REST──▶ Spring Boot backend ──HTTP local──▶ FastAPI sidecar (Python)
                                  │                                    │
                                  ├─▶ PostgreSQL (relacional)          ├─▶ yfinance (mercado)
                                  ├─▶ PostgreSQL+pgvector (embeddings) ├─▶ CVM dados abertos (fundamentos)
                                  ├─▶ Redis (cache)                    ├─▶ BCB SGS/Olinda (macro)
                                  └─▶ Gemini / Groq (LLM, HTTPS)       └─▶ Google News RSS (notícias)
```

## Padrões de projeto em uso

- **Gateway**: `PythonDataGateway` — ponto único de acesso a dados Python. Prefere sidecar HTTP, cai para spawn de processo (`PythonScriptRunner`) com cooldown de 30s se sidecar cair. Nenhum consumidor chama `PythonScriptRunner` direto.
- **Cache-aside**: Redis para cotações (TTL curto), análises (30 min), benchmarks setoriais (24h).
- **Single-flight / lock por chave**: `ConcurrentHashMap<String, ReentrantLock>` em `StockAnalysisService` — requisições simultâneas do mesmo ticker compartilham uma análise.
- **Fallback em cadeia**: Gemini → Groq (LLM); CVM → yfinance (fundamentos); sidecar → spawn (dados Python); benchmark dinâmico → estático (setor).
- **RAG**: contexto histórico de fundamentos (nunca análises passadas) recuperado por busca vetorial e injetado no prompt.
- **DTO/Record**: records Java imutáveis para todo dado transitório (StockFundamentals, MacroData, TechnicalIndicators, SentimentResult, NewsItem...).
- **Strategy via mapa de config**: `SectorPromptConfig`/`SectorBenchmarks` mapeiam `SectorType` → texto/faixas, sem hierarquia de classes.

## Módulos (pacotes `com.stockai.*`, todos achatados — sem subpacotes)

| Pacote | Responsabilidade |
|---|---|
| `stock` | Cotações e leitura de cache de mercado |
| `analysis` | Orquestração do score de IA, parsing/validação, comparação, backtesting, embeddings, benchmarks setoriais, LangChain4j — módulo mais denso (~35 classes) |
| `scheduler` | `PythonDataGateway`, `PythonScriptRunner`, jobs agendados (`StockFetchJob`, `HistoricalIndexingJob`) |
| `cache` | Abstração sobre Redis (`RedisStockCache` — SCAN, nunca KEYS) |
| `auth` | OAuth2 **Client** Google + JWT próprio (ver nota abaixo — **não** é OAuth2 Resource Server) |
| `user` | Entidade de usuário |
| `portfolio` | Carteira: CRUD, avaliação por IA, sugestão de alocação |

## Fluxo geral

1. Job agendado (`@Scheduled`, 60s) busca cotações via `PythonDataGateway`, grava no Redis (TTL curto).
2. Frontend consome via polling REST a cada 30s — **não há WebSocket funcional** (ver `anti-patterns.md`).
3. Análise sob demanda: `StockAnalysisService` coleta dados em paralelo (virtual threads) → classifica setor → busca RAG → monta prompt → chama Gemini (fallback Groq) → valida/clampa (`AnalysisParser`) → persiste histórico + alerta + embedding → cacheia 30 min.
4. Carteira reusa o pipeline de análise por ticker para cada posição.

Diagramas de sequência completos (cotação, análise IA, login, avaliação de carteira): ver `docs/PROJECT_DOCUMENTATION.md` seção 7.

## Decisões arquiteturais importantes

Ver `decisions.md` para o porquê de cada uma. Resumo:
- `scoreGeral` nunca vem do LLM — sempre recalculado em Java.
- RAG exclui análises passadas — evita feedback loop.
- Temperature 0 nos dois LLMs — scoring determinístico.
- CVM como fonte primária de fundamentos, yfinance como fallback.
- Flyway versiona schema desde 2026-08-06 (`ddl-auto: validate`, era `update` — ver `decisions.md`).
- Sidecar Python em vez de reescrever coleta de dados em Java.
- Benchmarks setoriais dinâmicos porque o LLM "inventa" médias de memória sem eles.
- `Stock` como entidade canônica desde 2026-08-07 — `portfolio_items`/`score_history`/`stock_alerts` referenciam por FK.

## Autenticação: OAuth2 Client, não Resource Server

Fluxo real: `Google OAuth2 → spring-boot-starter-oauth2-client (login) → backend emite seu próprio JWT (jjwt/JwtService) → frontend usa esse JWT nas próximas chamadas`. O backend nunca valida token emitido por terceiro (Keycloak/Auth0/Okta/Cognito) — ele é o próprio emissor e validador do token que circula depois do login.

Isso é diferente de um **OAuth2 Resource Server** (`spring-boot-starter-oauth2-resource-server`/`spring-boot-starter-security-oauth2-resource-server`), que valida JWT emitido por um Identity Provider externo via `issuer-uri`/`jwk-set-uri` e não emite token próprio. Confundir os dois papéis leva a tentar configurar `issuer-uri` ou reescrever `JwtAuthenticationConverter` esperando um `Jwt` decodificado de IdP externo — não existe esse componente no fluxo real, e mexer nisso quebraria o login Google. Ver `security-reviewer.md` para os gaps reais desse fluxo (TTL, revogação, token em query string).

## Entidade `Stock` canônica (desde 2026-08-07)

`score_history`, `stock_alerts` e `portfolio_items` referenciam `Stock` por FK (`stock_id`), criada sob demanda no primeiro uso do ticker (`StockRepository.findOrCreate`). Ver `backend.md` e `decisions.md` para o desenho completo e o bug de formato de ticker que a migration corrigiu. **Continua string solta, deliberadamente**: serviços que só manipulam ticker em trânsito por requisição (`StockAnalysisService`, `ComparisonService`, `BacktestService`, `SectorClassifier`, `PythonDataGateway`) e os metadados de `stock_embeddings` no pgvector (mecanismo do LangChain4j, sem FK possível).
