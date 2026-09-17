# promptfoo — regression testing do prompt de scoring

Ferramenta externa (Node/CLI, MIT), fora do backend Java. Não faz parte do
build (`mvn`), não roda em CI (projeto ainda não tem CI/CD — ver
`docs/ai/anti-patterns.md`). Uso manual, sob demanda.

## O que isto testa

Os 10 prompts reais capturados em `analysis_audit` na auditoria de
2026-08-08 (`PETR4, VALE3, WEGE3, ITUB4, BBAS3, ABEV3, LREN3, MGLU3, TAEE11,
VIVT3` — ver `docs/ai/decisions.md`), reenviados ao Gemini e ao Groq reais
com a config exata do `EmbeddingStoreConfig` (mesmo `baseUrl`, `modelName`,
`temperature=0`, `response_format=json_object`, `maxTokens=8192`).

Três assertions por caso:

1. **`is-json` contra `asserts/schema.json`** — schema espelha exatamente os
   campos que `AnalysisParser.requireDimension`/`.parse()` exige. Se o
   Gemini/Groq mudar formato de resposta, isto pega antes de virar
   `IllegalStateException` em produção.
2. **`asserts/score-drift.js`** — recalcula `scoreGeral` com a mesma fórmula
   de `AnalysisParser.computeScoreGeral` (média das 6 dimensões) e compara
   contra o baseline real capturado no banco. Falha se alguma dimensão
   desviar mais de 2.5 pontos ou o `scoreGeral` mais de 1.5 — regressão de
   prompt/modelo, não violação de regra de negócio.
3. **`asserts/coherence-rubric.txt` (`llm-rubric`)** — juiz LLM (Groq,
   fixado em `defaultTest.options.provider`) verifica se score e explicação
   de cada dimensão se contradizem. Isto é o mesmo padrão de falha real
   achado em produção (`MGLU3`: `retornoAcionista.score=9.0` com a própria
   explicação dizendo "crescimento de lucros negativo"; `WEGE3`:
   `fundamentos.score=8.0` com "crescimento negativo de receita e lucro" no
   texto) — ver `docs/ai/decisions.md` e `docs/ai/anti-patterns.md` item
   sobre `ScorePlausibilityGate`.

## O que isto **não** é

- **Não substitui o `ScorePlausibilityGate`** (Java, determinístico, roda em
  toda análise real). O gate continua sendo a única defesa em produção.
  Isto aqui é regression testing offline/manual, não um gate de runtime.
- **Não reimplementa nenhum threshold de regra financeira** (ex.: cap de
  VAREJO `retornoAcionista ≤ 4`). Isso é dívida documentada em
  `anti-patterns.md` ("thresholds duplicados sem vínculo em
  compilação/execução") — duplicar o número aqui de novo, numa terceira
  linguagem (JS), pioraria o problema em vez de resolver. O rubric de
  coerência é deliberadamente genérico (score contradiz o próprio texto),
  não hardcoda nenhum número de setor.
- **Juiz≠gerador só é garantido quando o provider testado é o Gemini.**
  Quando o provider testado é o próprio Groq, o juiz (também Groq) é o
  mesmo modelo — limitação conhecida, não bug. Trocar o juiz por provider
  dinamicamente exigiria um grader customizado; não fizemos isso ainda
  porque os 10 baselines reais vieram 100% do Gemini (nenhum fallback pro
  Groq foi necessário na auditoria).

## Como rodar

```bash
cd tools/promptfoo
npm install
GEMINI_API_KEY=... GROQ_API_KEY=... npm run eval
npm run view   # abre o dashboard local
```

**Custo real**: 10 casos × 2 providers × (1 chamada de geração + até 1
chamada de juiz llm-rubric) = até ~30 chamadas de LLM por rodada completa.
Rode sob demanda, não em loop.

## Recapturar o baseline

Se rodar uma nova auditoria real (novo conjunto de tickers, novo prompt
version), regrave `data/baseline_manifest.json` e os arquivos em
`data/prompts/*.txt` a partir de `analysis_audit`, depois:

```bash
npm run gen-tests
```

`tests.yaml` é gerado — não editar à mão (tem aviso no topo do arquivo).
