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

## Sentimento — NÃO é FinBERT

Análise **lexical** por dicionário de palavras-chave pt/en (~50 termos positivos/negativos, com negação e intensificador), implementada em `analyze_sentiment.py`. Score normalizado 0–10, 5.0 = neutro. Explicitamente rotulado no prompt como "sinal de confiança limitada".

`huggingface.token` está configurado em `application.yml` mas **nenhum código consumidor foi encontrado** — é preparação antecipada para uma futura integração FinBERT-PT-BR real (ver `roadmap.md`), ainda não conectada. Não assumir que FinBERT já está em uso.

## PROMPT_VERSION

Constante `StockAnalysisService.PROMPT_VERSION`, hoje `"v2.3"`. **Incrementar sempre que o texto do prompt mudar** — scores de versões diferentes não são comparáveis entre si (o histórico em `score_history` guarda `promptVersion` por linha justamente por isso).

## Limitações conhecidas

- `SectorClassifier` tem mapeamentos yfinance→setor incorretos (Utilities deveria ir para ENERGIA, Technology para INDUSTRIA, Consumer Defensive para VAREJO) — ver `roadmap.md` P1.
- Sem coleta de EV/EBITDA, payout ratio, margem EBITDA — o prompt setorial de LOGISTICA pede EBITDA e o dado nunca chega.
- Sem benchmark relativo a IBOV/CDI no momentum — não distingue "a ação caiu" de "o mercado caiu".
- Sem tabela de auditoria completa (prompt + resposta bruta por análise) — só `modelUsed`/`promptVersion` são persistidos, dificultando reconstruir por que um score específico saiu de um jeito.
- Modelos hospedados por nome, não por hash — o provedor pode trocar o modelo por trás de `gemini-2.5-flash`/`qwen/qwen3-32b` sem aviso, quebrando comparabilidade histórica mesmo com `PROMPT_VERSION` fixo.
