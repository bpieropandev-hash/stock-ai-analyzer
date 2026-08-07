# Anti-padrões e débitos técnicos conhecidos

Coisas encontradas no código que **não devem ser copiadas como padrão** para código novo, e itens que merecem alerta antes de qualquer trabalho relacionado.

## Segurança

- **`.anyRequest().permitAll()` no final da cadeia de segurança** — postura fail-open: endpoint novo sem regra explícita fica público por padrão. Ao adicionar endpoint, sempre declarar a regra explicitamente, não confiar no catch-all.
- **JWT sem revogação, sem refresh token** — token de 7 dias fixo (`TTL_MS` hardcoded), logout é só client-side (`localStorage.removeItem`). Um token vazado continua válido até expirar naturalmente. Não propor "logout" como solução de segurança real sem lembrar disso.
- **Token entregue via query string em redirect HTTP** (`OAuth2SuccessHandler` → `?token=...`) — risco de exposição em histórico de navegador e logs de proxy. Alternativa mais segura (cookie httpOnly, ou POST em vez de redirect GET) não implementada ainda.
- **Token em `localStorage`**, não em cookie httpOnly — exposto a qualquer XSS na SPA.
- **Sem rate limiting em nenhum endpoint público** — `/api/stocks/**`, `/api/compare`, `/api/simulate`, `/api/alerts/**` disparam chamada de LLM (custo real) sem limite algum. Não assumir que isso está coberto antes de expor a API publicamente.
- **`JwtService.isTokenValid` engole `Exception` genérica** — não diferencia token expirado de malformado de assinatura inválida, limitando auditoria de segurança.

## Arquitetura / operação

~~**URLs hardcoded em vários pontos**~~ — resolvido em 2026-08-07, ver `decisions.md`. CORS e redirect OAuth2 vêm de `app.cors.allowed-origins`/`app.frontend.base-url` (env var), frontend usa `environment.ts`/`environment.prod.ts` com `fileReplacements`. Vendor endpoints fixos (Gemini/Groq/CVM/BCB/News) continuam hardcoded por decisão — não variam por ambiente.
- **Sem Dockerfile de aplicação** (nem backend nem frontend) — `docker-compose.yml` só sobe infra (Postgres+pgvector, Redis).
- **Sem CI/CD** — nenhum workflow configurado.
- ~~**`ddl-auto: update` sem Flyway**~~ — resolvido em 2026-08-06, ver `decisions.md`. Schema agora versionado via `db/migration/V{n}__*.sql`, `ddl-auto: validate`.

## Frontend

- **`@stomp/stompjs`/`sockjs-client` no `package.json` sem uso real** — WebSocket foi abandonado (`/ws` retornava 404), substituído por polling HTTP a cada 30s em `StockService`. Não assumir que WebSocket está funcional; não construir feature nova sobre essas libs sem primeiro confirmar com o time se serão reativadas ou removidas.
- **`SimulatorPage` bypassa `PortfolioService`** — injeta `HttpClient` direto, chama endpoint diferente (`/api/simulate`) do usado por `suggestAllocation` (`/api/portfolio/suggest-allocation`). Não replicar esse padrão em página nova; usar sempre o service layer.
- **`ScoreBar` parece componente órfão** — páginas reimplementam a barra de score inline em vez de reusar o componente compartilhado. Antes de criar uma barra de score nova, verificar se dá pra finalmente usar `ScoreBar` em vez de duplicar de novo.

## Testes

- **Cobertura de teste muito baixa** — backend: 3 classes (nenhum teste de controller/repositório/integração). Frontend: só o spec default do Angular CLI, sem nenhum teste real de página/serviço/guard. Não assumir que uma mudança está "coberta" — checar manualmente.
- **`ComparisonService`, `BacktestService`, `SectorClassifier` sem teste algum.**

## IA

- **`SectorClassifier` com mapeamentos yfinance→setor conhecidamente incorretos** (Utilities/Technology/Consumer Defensive mal mapeados) — afeta a qualidade das instruções de prompt e dos benchmarks para os setores errados. Corrigir é item de roadmap (P1), não fazer half-fix local sem also revisar `SectorPromptConfig`/`SectorBenchmarks`.
- **Sem tabela de auditoria** — impossível reconstruir o prompt/resposta exatos de uma análise passada, só se sabe `modelUsed`/`promptVersion`.
- **`huggingface.token` configurado sem consumidor** — não assumir que há integração FinBERT ativa.

## Regra geral

Ao tocar em qualquer arquivo relacionado a um item desta lista, mencionar o débito encontrado ao usuário antes de decidir se corrige, contorna ou ignora — não corrigir silenciosamente um item de roadmap não solicitado, e não repetir o padrão em código novo.
