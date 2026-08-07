# IA — visão geral

Detalhe de prompt: ver `prompts.md`. Detalhe de RAG/embeddings: ver `rag.md`. Regras de negócio financeiras aplicadas na análise: ver `financial-rules.md`.

## Modelos

- **Gemini 2.5 Flash** — primário, via endpoint OpenAI-compatible do Google (`https://generativelanguage.googleapis.com/v1beta/openai/`). **Não** é o módulo Gemini nativo do LangChain4j.
- **Groq `qwen/qwen3-32b`** — fallback, via endpoint OpenAI-compatible da Groq (`https://api.groq.com/openai/v1`).
- Ambos: `dev.langchain4j.model.openai.OpenAiChatModel`, `responseFormat("json_object")`, `temperature(0.0)`, `maxTokens(8192)`.
- Bean config: `EmbeddingStoreConfig.java` (qualifiers `geminiChatModel` / `groqChatModel`).

Por que a mesma abstração (`OpenAiChatModel`) para os dois: trocar provedor primário/fallback no futuro é troca de config, não reescrita de integração.

## Por que temperature 0

Scoring precisa ser determinístico — variância de amostragem dispararia alertas de mudança de score por ruído estatístico, não por mudança real de fundamento. Comentário original no código: "scoring deve ser determinístico; variância de amostragem contamina o histórico e dispara alertas por ruído, não por fato novo."

## Sem tool-calling / function-calling

O LLM recebe um prompt de texto único e devolve JSON. Toda coleta de dado, RAG, classificação setorial e cálculo acontece em Java **antes** de chamar o modelo — não há framework de agentes nem o modelo pedindo mais dados. Se o contexto fornecido for insuficiente, o prompt instrui "não invente sinal" em vez de deixar o modelo decidir buscar mais.

## Fallback e resiliência

```
Gemini falha (qualquer exceção: rede, JSON malformado, dimensão ausente)
  → tenta Groq
    → Groq falha também
      → RuntimeException genérica pro usuário ("análise temporariamente indisponível")
```

Dados opcionais (macro, notícias, indicadores técnicos) degradam graciosamente via `safeGet` — só fundamentos é obrigatório, falha aí aborta a análise.

## Sentimento — FinBERT real com fallback léxico (desde 2026-08-07)

`sidecar_app.py` (`/sentiment`) tenta **FinBERT-PT-BR** (`lucas-leme/FinBERT-PT-BR`, classificador real via HF Inference API, `finbert_sentiment.py`) primeiro; sem `HUGGINGFACE_TOKEN` ou em qualquer falha (timeout 8s, erro HTTP, resposta inesperada) cai automaticamente pro **léxico** (`analyze_sentiment.py`, dicionário de palavras-chave pt/en, ~50 termos, com negação e intensificador — o que já existia antes). Ambos os caminhos produzem o mesmo schema via `_aggregate` compartilhado (score 0–10, 5.0 = neutro, `distribution`, `confidence`) — comparável independente da fonte.

`SentimentResult` (Java) ganhou o campo `source` (`"finbert"`/`"lexical"`/`"unavailable"`) — o prompt (`buildSentimentText`) usa esse campo pra escolher o caveat certo: FinBERT é apresentado como "sinal confiável", léxico como "sinal de confiança limitada". Nunca assumir qual fonte rodou sem checar `source` — depende só de `HUGGINGFACE_TOKEN` estar no ambiente do processo do sidecar (não é carregado do `.env` automaticamente, ver `decisions.md`).

Fallback via spawn de processo (`PythonScriptRunner`, sidecar fora do ar) continua **só léxico** — decisão deliberada, não estendida ao FinBERT (já é caminho degradado).

## PROMPT_VERSION

Constante `StockAnalysisService.PROMPT_VERSION`, hoje `"v2.4"` (bump em 2026-08-07 — caveat de sentimento dinâmico por fonte). **Incrementar sempre que o texto do prompt mudar** — scores de versões diferentes não são comparáveis entre si (o histórico em `score_history` guarda `promptVersion` por linha justamente por isso).

## Limitações conhecidas

- `SectorClassifier` tem mapeamentos yfinance→setor incorretos (Utilities deveria ir para ENERGIA, Technology para INDUSTRIA, Consumer Defensive para VAREJO) — ver `roadmap.md` P1.
- Sem coleta de EV/EBITDA, payout ratio, margem EBITDA — o prompt setorial de LOGISTICA pede EBITDA e o dado nunca chega.
- Sem benchmark relativo a IBOV/CDI no momentum — não distingue "a ação caiu" de "o mercado caiu".
- ~~Sem tabela de auditoria completa~~ Resolvido em 2026-08-07 — `analysis_audit` persiste prompt + resposta bruta + raciocínio (`"analise"`) + explicação por dimensão, FK 1:1 com `score_history`. Ver `decisions.md`. Descoberta incidental: o campo `"analise"` já era pedido no prompt mas `AnalysisParser` nunca o extraía — corrigido junto.
- Modelos hospedados por nome, não por hash — o provedor pode trocar o modelo por trás de `gemini-2.5-flash`/`qwen/qwen3-32b` sem aviso, quebrando comparabilidade histórica mesmo com `PROMPT_VERSION` fixo.
