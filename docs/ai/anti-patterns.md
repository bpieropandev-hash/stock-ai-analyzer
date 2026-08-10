# Anti-padrões e débitos técnicos conhecidos

Coisas encontradas no código que **não devem ser copiadas como padrão** para código novo, e itens que merecem alerta antes de qualquer trabalho relacionado.

## Segurança

- **`.anyRequest().permitAll()` no final da cadeia de segurança** — postura fail-open: endpoint novo sem regra explícita fica público por padrão. Ao adicionar endpoint, sempre declarar a regra explicitamente, não confiar no catch-all.
- **JWT sem revogação, sem refresh token** — `TTL_MS` reduzido de 7 dias para 24h em 2026-08-07 (mitigação parcial, ver `decisions.md`), mas continua sem revogação e sem refresh. Logout é só client-side (`localStorage.removeItem`). Um token vazado continua válido até expirar (agora no máximo 24h, não mais 7 dias). Não propor "logout" como solução de segurança real sem lembrar disso — revogação de verdade continua não implementada.
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
- **Design system de `frontend.md` desincronizado do `styles.scss` real** (achado pelo Knowledge Guardian, 2026-08-08) — o bloco "Design system" documenta variáveis que não existem (`--color-bg`, `--color-accent`, `--color-danger` etc.) e hex da paleta antiga (`#00d4aa`/`#f59e0b`/`#ef4444`); as reais são `--bg-base`, `--accent`, `--danger` etc., paleta `#00e5c3`/`#f0a020`/`#ff3b5c`. Corrigir é troca de texto simples, baixo risco — não feito ainda, fora do escopo da tarefa que achou.
- **Cor hardcoded na paleta antiga espalhada por 6+ páginas** (achado pelo Knowledge Guardian, 2026-08-08) — `dashboard.ts`, `analysis.ts`, `compare.ts`, `simulator.ts`, `portfolio.ts`, `ticker-select.ts` cada um redeclara sua própria custom property local (`--a: #00d4aa`) e literais soltos (`#ef4444`/`#f59e0b`), todos na paleta antiga (nem sequer bate com `--accent`/`--danger`/`--amber` reais). Adicional: 4 implementações quase-duplicadas de `scoreColor`/`barColor`/`ringColor` (`analysis.ts`, `compare.ts`, `simulator.ts`, `portfolio.ts`) com thresholds divergentes entre si (`≥6.5`/`≥4` vs `≥7`/`≥5` do `ScoreBar` compartilhado, que é órfão — ver item abaixo). Escopo grande (6+ arquivos) — **não corrigir sem avisar antes**, é refactor real, não fix pontual.
- **`SimulatorPage` bypassa `PortfolioService`** — injeta `HttpClient` direto, chama endpoint diferente (`/api/simulate`) do usado por `suggestAllocation` (`/api/portfolio/suggest-allocation`). Não replicar esse padrão em página nova; usar sempre o service layer.
- **`ScoreBar` parece componente órfão** — páginas reimplementam a barra de score inline em vez de reusar o componente compartilhado. Antes de criar uma barra de score nova, verificar se dá pra finalmente usar `ScoreBar` em vez de duplicar de novo.

## Testes

- **Cobertura de teste muito baixa** — backend: 3 classes (nenhum teste de controller/repositório/integração). Frontend: só o spec default do Angular CLI, sem nenhum teste real de página/serviço/guard. Não assumir que uma mudança está "coberta" — checar manualmente.
- **`ComparisonService`, `BacktestService`, `SectorClassifier` sem teste algum.**

## IA

~~**`PortfolioService.evaluate()` tinha thresholds de recomendação próprios, divergentes de `AnalysisParser.deriveRecommendation`**~~ — resolvido em 2026-08-08. Achado pela auditoria de Skills externas, confirmado como duplicação acidental (não decisão de negócio) por `financial-analyst`, desenhado por `backend-architect`: `evaluate()` reimplementava 3 faixas (`≥7.0`/`≥5.0`/resto, sem CAUTELA) em vez de usar o `recommendation` que `AnalysisResponse` já trazia pronto (calculado por `deriveRecommendation` em `StockAnalysisService`). Achado extra que reforçou a urgência: `PortfolioService.getPortfolio()` já usava o rótulo oficial via `analysis.recommendation()` — só `evaluate()` divergia, ou seja a própria tela de carteira já discordava de si mesma. Fix de 1 linha (`String action = analysis.recommendation();`), zero dependência nova. `suggestAllocation`/`PortfolioSimulator` continuam com critério binário próprio (`≥6.0`) — não é rótulo, é elegibilidade, não colide.
~~**`recommendation-badge.ts` usa cores hardcoded no `styles:` inline**~~ — resolvido em 2026-08-08. `color:` de cada variante agora usa `var(--accent)`/`var(--blue)`/`var(--amber)`/`var(--danger)`/`var(--text-secondary)` (já existiam em `styles.scss` com os valores exatos que estavam hardcoded). `background`/`border-color` continuam `rgba(...)` literal — mesmo padrão já usado pelas classes `.badge-*` globais em `styles.scss` (tint derivado, não cor de marca). `ng build --configuration production` limpo.
- **`SectorClassifier` com mapeamentos yfinance→setor conhecidamente incorretos** (Utilities/Technology/Consumer Defensive mal mapeados) — afeta a qualidade das instruções de prompt e dos benchmarks para os setores errados. Corrigir é item de roadmap (P1), não fazer half-fix local sem also revisar `SectorPromptConfig`/`SectorBenchmarks`.
- ~~**Sem tabela de auditoria**~~ Resolvido em 2026-08-07 — `analysis_audit`, ver `decisions.md`.
- ~~**`huggingface.token` configurado sem consumidor**~~ Resolvido em 2026-08-07 — property Spring removida (código morto de verdade); consumidor real é `finbert_sentiment.py` (Python, lê `os.environ` direto). Ver `decisions.md`.

- **Thresholds do `ScorePlausibilityGate` duplicados do texto do prompt, sem vínculo em compilação/execução** (achado na revisão final de 2026-08-08, ver `decisions.md`) — os 6 números hardcoded no gate (`7`, `3.5`, `2`, `7.5`, `4`, `8`) espelham regras que também existem como texto em `prompts.md`/`SectorPromptConfig`/`financial-rules.md`. Se o threshold do prompt mudar (ex.: cap de VAREJO 4→3), o gate não quebra nem avisa — fica desatualizado silenciosamente. Não é defeito atual (gate é warn-only, não corrompe score), é dívida de manutenção. **Direção futura, não decidida ainda**: extrair a regra financeira pra uma fonte única que alimente tanto o texto do prompt quanto o gate, em vez de duplicar o número nos dois lugares — evita divergência silenciosa entre "o que o LLM é instruído a fazer" e "o que o gate verifica que ele fez". Não implementar sem decisão explícita — é reformulação de como regra financeira é representada no sistema, não um fix pontual.

## Regra geral

Ao tocar em qualquer arquivo relacionado a um item desta lista, mencionar o débito encontrado ao usuário antes de decidir se corrige, contorna ou ignora — não corrigir silenciosamente um item de roadmap não solicitado, e não repetir o padrão em código novo.
