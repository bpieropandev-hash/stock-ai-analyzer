# Roadmap — stock-ai-analyzer

> Estado em 2026-06-11, após o commit `5ce8686` (overhaul de precisão/confiabilidade/performance).

## ✅ Concluído (commit 5ce8686)

**Precisão dos scores**
- `scoreGeral` calculado em Java (`AnalysisParser`) — aritmética do LLM descartada; clamp 0–10; dimensão ausente é erro
- Prompt v2 (`PROMPT_VERSION = "v2.0"`): rubrica com âncoras objetivas, benchmarks setoriais, data + ano eleitoral, campo de raciocínio antes dos scores
- Removida alegação falsa de FinBERT (sentimento é lexical e o prompt agora diz isso)
- Temperature 0 nos dois modelos (Gemini 2.5 Flash / Groq qwen3-32b)
- RAG sem feedback loop — recupera só `historical_fundamentals`, nunca análises passadas
- Expectativas Focus (BCB/Olinda) no macro; `debtToEquity` normalizado na origem

**Confiabilidade**
- `PythonScriptRunner` com timeout + leitura concorrente de streams (fim do deadlock e threads penduradas)
- Score history em tabela JPA `score_history` com `modelUsed` e `promptVersion`
- Bug do cache key no `ComparisonService` corrigido; dedup de embeddings no job diário
- Single-flight por ticker; removidos bean morto e `fetch_foreign_flow.py` (dados simulados)
- JWT secret sem fallback fraco

**Performance**
- Coleta paralela com virtual threads; Redis SCAN; scheduler pool 2

**Novo**
- Backtesting: `GET /api/stocks/{ticker}/backtest` (Pearson score × retorno 30/90d)
- 9 testes unitários no `AnalysisParserTest`; suíte 10/10 verde

---

## 📋 Backlog priorizado

### P0 — maior impacto na qualidade da análise
1. **Sidecar Python persistente (FastAPI)** — cada script paga 2–5s de import do yfinance/pandas; um serviço HTTP local elimina o spawn por chamada. Maior alavanca de latência restante (~27s → meta <10s).
2. **Dados oficiais CVM (ITR/DFP via dados abertos)** como fonte primária de fundamentos — yfinance para B3 tem P/L, P/VPA e DY frequentemente errados/defasados. Manter yfinance como fallback.
3. **Rótulos COMPRAR/VENDER → linguagem descritiva** *(decisão de produto)* — Res. CVM 20/2021 restringe "recomendações de investimento"; afeta badges do frontend (cores em CLAUDE.md). Decidir antes de abrir para terceiros.
4. **Benchmarks setoriais dinâmicos** — hoje são faixas estáticas em `SectorBenchmarks`; calcular medianas reais dos pares do setor a partir dos dados já coletados.

### P1 — precisão por dimensão
5. **Tratamento específico para bancos** (ITUB4, BBDC4, B3SA3) — debt/equity e margens do yfinance não fazem sentido para financeiras; usar ROE, P/VPA, e injetar nota no prompt para ignorar alavancagem bruta.
6. **Corrigir `SectorClassifier`** — Utilities→ENERGIA, Technology→INDUSTRIA, Consumer Defensive→VAREJO geram instruções setoriais erradas; criar SectorTypes próprios (UTILITIES, TECNOLOGIA, CONSUMO_DEFENSIVO).
7. **EV/EBITDA, payout ratio, margem EBITDA** no `fetch_fundamentals.py` — o prompt setorial de LOGISTICA pede EBITDA mas o dado nunca é fornecido.
8. **IBOV/CDI como benchmark relativo** no momentum — "a ação caiu ou o mercado todo caiu?"; adicionar retorno relativo ao IBOV em `fetch_technical_indicators.py`.
9. **Notícias melhores** — buscar corpo (não só título), dedup, filtro de data, remover sufixo do veículo ("... - InfoMoney") que polui o léxico. Avaliar FinBERT-PT-BR real via HF Inference API.

### P2 — robustez e operação
10. **Tabela de auditoria completa** — persistir snapshot de input + prompt + output bruto por análise (hoje só modelUsed/promptVersion).
11. **`@ControllerAdvice`** — controllers engolem exceções e retornam 500 sem corpo.
12. **Rate limiting** nos endpoints públicos de análise (cada miss de cache custa chamada de LLM).
13. **Flyway** em vez de `ddl-auto: update` — schema versionado.
14. **Fluxo estrangeiro real da B3** (CSV diário publicado), short interest, aluguel BTC — substituir a dimensão "Sentimento Institucional" por dados institucionais de verdade.
15. **Calendário de resultados e eventos corporativos** — análise na véspera de balanço tem validade diferente.

### P3 — produto
16. **Frontend**: exibir `modelUsed`, `promptVersion` e resultados do backtest na tela de análise (transparência para o usuário).
17. **Curva DI futuro** para custo de capital (hoje só Selic spot + Focus).
18. **Mais testes**: ComparisonService, BacktestService (correlação), SectorClassifier.

---

## Notas para retomada
- Infra local: `podman machine start` → `podman compose up -d` (containers `stock-ai-postgres` e `stock-ai-redis` já existem).
- Ao mudar o prompt, **incrementar `StockAnalysisService.PROMPT_VERSION`** — scores de versões diferentes não são comparáveis.
- O backtest só terá significado estatístico com ~30+ análises por ticker acumuladas na `score_history` (correlação retorna null com <3 pares).
