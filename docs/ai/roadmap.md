# Roadmap

Fonte canônica: [`docs/ROADMAP.md`](../ROADMAP.md) — mantido separado por já ter fluxo próprio de atualização (um item por vez, autorização do usuário antes do próximo). Este arquivo é só um resumo de navegação; **para status atualizado, ler o `docs/ROADMAP.md` diretamente**, não confiar neste resumo se houver divergência de data.

## Concluído (overhaul 2026-06-12)

- P0-1: sidecar Python persistente (FastAPI).
- P0-2: dados oficiais CVM como fonte primária de fundamentos.
- P0-3: rótulos descritivos CVM-compliant (ATRATIVO/NEUTRO/CAUTELA/DESFAVORÁVEL).
- P0-4: benchmarks setoriais dinâmicos.
- P1-5: tratamento específico para bancos/financeiras.

## Concluído (2026-08-06)

- P2-13: Flyway substituindo `ddl-auto: update`. Ver `decisions.md` e `backend.md` para detalhe técnico completo.

## Concluído (2026-08-07)

- Sprint 1 (fora da numeração P0-P3 original, priorização acordada com GPT): URLs configuráveis (CORS/OAuth2 redirect/API base do frontend), entidade `Stock` canônica substituindo ticker solto, JWT TTL 7 dias→24h + tratamento de 401 no frontend.
- Sprint 2: P2-10 tabela de auditoria completa (`analysis_audit`, prompt + raw response + raciocínio + explicação por dimensão); P1-9 parcial — FinBERT-PT-BR real com fallback léxico automático (`PROMPT_VERSION` v2.3→v2.4). Ver `decisions.md`.
- Sprint 3: Score Confidence (meta-score de qualidade do dado — `ScoreConfidenceCalculator`, agrega sinais já existentes: fonte dos fundamentos, sentimento, indicadores técnicos, benchmark setorial); P3-16 — `modelUsed`/`promptVersion`/backtest expostos na tela de análise. Ver `decisions.md`.

## Backlog aberto (ver `docs/ROADMAP.md` para detalhe técnico de cada item)

**P1 — precisão por dimensão**
- P1-6: corrigir `SectorClassifier` (mapeamentos yfinance errados).
- P1-7: coletar EV/EBITDA, payout ratio, margem EBITDA.
- P1-8: benchmark relativo IBOV/CDI no momentum.
- P1-9: notícias melhores (corpo completo, dedup, filtro de data) — resto do item além do FinBERT, que já foi concluído 2026-08-07.

**P2 — robustez e operação**
- ~~P2-10: tabela de auditoria completa~~ — concluído 2026-08-07.
- P2-11: `@ControllerAdvice`.
- P2-12: rate limiting.
- ~~P2-13: Flyway em vez de `ddl-auto: update`~~ — concluído 2026-08-06.
- P2-14: fluxo estrangeiro real B3 / short interest / aluguel BTC.
- P2-15: calendário de resultados e eventos corporativos.

**P3 — produto**
- ~~P3-16: expor `modelUsed`/`promptVersion`/backtest no frontend.~~ — concluído 2026-08-07, junto com Score Confidence.
- P3-17: curva DI futuro.
- P3-18: mais testes (ComparisonService, BacktestService, SectorClassifier).

## Itens adicionais identificados fora do `ROADMAP.md` original (auditoria 2026-08-06)

Não têm número de prioridade formal ainda — levantados durante a geração de `docs/PROJECT_DOCUMENTATION.md`:
- Dockerfiles + pipeline de CI/CD.
- ~~Externalizar URLs de frontend/backend/CORS via config de ambiente.~~ Concluído 2026-08-07.
- Remover dependências mortas de WebSocket (`@stomp/stompjs`, `sockjs-client`).
- Aumentar cobertura de testes de frontend (hoje ~zero).
- Unificar `SimulatorPage` com `PortfolioService`.
- Avaliar refresh token / revogação de JWT — TTL reduzido 7 dias→24h em 2026-08-07 (mitigação parcial), refresh/revogação de verdade continua em aberto.

## Fluxo de trabalho do roadmap

Um item por vez. Ao concluir: atualizar `docs/ROADMAP.md` e o arquivo específico em `docs/ai/` afetado (ex.: item de RAG → atualizar `rag.md`), commitar, e **aguardar autorização explícita antes de iniciar o próximo item** — não encadear itens automaticamente.
