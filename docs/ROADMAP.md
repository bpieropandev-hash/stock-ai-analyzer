# Roadmap — stock-ai-analyzer

> Estado em 2026-06-12.

## ✅ Concluído (2026-06-12) — Linguagem descritiva nos rótulos (P0-3)

- Rótulos da análise: COMPRAR/MANTER/AGUARDAR/EVITAR → **ATRATIVO/NEUTRO/CAUTELA/DESFAVORÁVEL** (mesmas faixas de score em `AnalysisParser.deriveRecommendation`); ações de carteira COMPRAR_MAIS/MANTER/VENDER → ATRATIVO/NEUTRO/DESFAVORÁVEL — Res. CVM 20/2021 restringe recomendações imperativas a analistas credenciados
- Elegibilidade na alocação (`PortfolioService`/`PortfolioSimulator`) agora compara **score ≥ 6.0** (piso do NEUTRO) em vez de rótulo — imune a análises com grafia antiga no cache Redis
- Frontend: badge e portfolio mapeiam os rótulos novos com as mesmas cores (`ATRATIVO=#00d4aa`, `NEUTRO=#3b82f6`, `CAUTELA=#f59e0b`, `DESFAVORÁVEL=#ef4444`); rótulos antigos vindos do cache (TTL 30 min) exibem o texto novo — esses mapeamentos legados podem ser removidos depois
- `PROMPT_VERSION` inalterado — o rótulo é derivado em Java a partir do score; o prompt não muda

## ✅ Concluído (2026-06-12) — Dados oficiais CVM como fonte primária de fundamentos (P0-2)

- `scripts/cvm_data.py` — fundamentos contábeis direto dos demonstrativos da CVM (dados abertos): DRE em janela **LTM** (YTD do ITR + exercício DFP − YTD anterior), balanço mais recente, dividendos+JCP pagos via DFC; consolidado com fallback individual; lucro atribuído aos sócios da controladora (3.11.01) preferido sobre o consolidado
- Mapa ticker→CNPJ via FCA (`valor_mobiliario`); zips anuais cacheados em `scripts/.cvm_cache/` (ano corrente renova a cada 24h; cooldown de 10 min após falha de download)
- `fetch_fundamentals.py` — overlay CVM sobre o yfinance: ROE, ROA, margens, dívida/PL, receita e crescimentos vêm da CVM; P/L, P/VPA e DY recalculados com market cap do yfinance; campo ausente na CVM mantém o valor yfinance; ticker fora do cadastro (ETFs, BDRs) cai integralmente para yfinance
- Proveniência exposta: `fundamentalsSource` ("cvm+yfinance" | "yfinance") e `statementDate` no JSON, no record `StockFundamentals` e no prompt (**PROMPT_VERSION v2.0 → v2.1**)
- Sidecar pré-baixa os datasets no startup (thread de fundo via lifespan) e mantém DataFrames em memória
- Validação cruzada: lucro LTM da VALE3 calculado da CVM = `netIncomeToCommon` do yfinance ao milhar (15,603 bi); PETR4/ITUB4/WEGE3/MGLU3 com ratios plausíveis; bancos ficam sem dívida bruta (correto — alavancagem bancária é o item P1-5)

## ✅ Concluído (2026-06-12) — Sidecar Python persistente (P0-1)

- `scripts/sidecar_app.py` — FastAPI expondo as funções dos scripts existentes via HTTP local (porta 8001); os scripts continuam funcionando standalone
- `PythonDataGateway` — ponto único de acesso aos dados Python: prefere o sidecar, cai para spawn de processo (`PythonScriptRunner`) com cooldown de 30s quando o sidecar está fora do ar
- Todos os consumidores migrados: `StockAnalysisService`, `StockFetchJob`, `HistoricalIndexingJob`, `BacktestService` — nenhum usa `PythonScriptRunner` direto
- Config em `application.yml`: `python.sidecar.enabled` / `python.sidecar.base-url`
- `scripts/requirements.txt` criado (yfinance, pandas, fastapi, uvicorn)
- Subir o sidecar: `cd backend/scripts && python -m uvicorn sidecar_app:app --host 127.0.0.1 --port 8001`
- Medido: fundamentos em ~1,5s via sidecar quente (vs 3,5s+ com import frio por spawn)

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
1. ~~**Sidecar Python persistente (FastAPI)**~~ ✅ concluído em 2026-06-12 (ver seção acima)
2. ~~**Dados oficiais CVM (ITR/DFP via dados abertos)**~~ ✅ concluído em 2026-06-12 (ver seção acima)
3. ~~**Rótulos COMPRAR/VENDER → linguagem descritiva**~~ ✅ concluído em 2026-06-12 (ver seção acima)
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
- Sidecar Python é opcional em dev — se não estiver no ar, o `PythonDataGateway` cai automaticamente para spawn de processo (mais lento, mas funcional).
- Ao mudar o prompt, **incrementar `StockAnalysisService.PROMPT_VERSION`** — scores de versões diferentes não são comparáveis.
- O backtest só terá significado estatístico com ~30+ análises por ticker acumuladas na `score_history` (correlação retorna null com <3 pares).
