# RAG (Retrieval-Augmented Generation)

## Embeddings

- Modelo: `nomic-embed-text` via **Ollama local** (`http://localhost:11434` default), 768 dimensões.
- Dimensão fixada em config (`ollama.embedding.dimension: 768`) deliberadamente, para não depender de round-trip HTTP ao Ollama no startup do bean.
- Bean: `EmbeddingModel embeddingModel()` em `EmbeddingStoreConfig.java` — `OllamaEmbeddingModel`, timeout 3 min.

## Vector store

`PgVectorEmbeddingStore` (LangChain4j), tabela `stock_embeddings`, **auto-criada** (`createTable(true)`) — não é gerida pelo Hibernate nem por migration alguma. Metadados armazenados como colunas dedicadas (`MetadataStorageMode.COLUMN_PER_KEY`: `ticker TEXT`, `date TEXT`, `type TEXT`), não como blob JSON — é isso que permite os filtros SQL usados na busca.

## Três tipos de conteúdo indexado (`StockEmbeddingService`)

| `type` | Quando é gravado | É recuperado para RAG? |
|---|---|---|
| `fundamentals` | A cada snapshot atual de fundamentos | Não |
| `analysis` | A cada análise completa (6 dimensões + resumo) | **Não — exclusão deliberada** |
| `historical_fundamentals` | Snapshots de períodos passados | **Sim — único tipo usado como contexto** |

## Por que `analysis` é excluído do RAG

Decisão arquitetural explícita, documentada em comentário no código: recuperar análises passadas como contexto criaria **feedback loop** — o LLM ancoraria no próprio score anterior em vez de reavaliar os fundamentos de forma independente. Isso contaminaria a série histórica de score (ela deixaria de refletir mudança real de fundamento, e passaria a refletir "o que eu disse da última vez").

**Não reverter essa decisão sem entender a consequência.** Se alguém propuser "usar análises passadas como contexto para dar mais consistência", isso é exatamente o anti-padrão que a exclusão evita.

## Fluxo de busca (`StockAnalysisService.retrieveContext`)

1. Fundamentos atuais são renderizados em texto descritivo (mesmo formato usado para indexar).
2. Texto é embedado (`embeddingModel.embed(...)`).
3. Busca filtrada por `ticker == atual` **e** `type == "historical_fundamentals"`, top 3 resultados.
4. Resultados concatenados com separador `---`.
5. Falha (pgvector fora do ar, exceção qualquer) → fallback para texto `"Sem contexto histórico disponível."`, análise **continua** (RAG é degradação graciosa, não bloqueante).

## Limitações

- Sem reranking, sem MMR (maximal marginal relevance) — busca vetorial pura top-k.
- Sem chunking sofisticado — cada snapshot é um único `TextSegment`.
- Sem avaliação/métrica de qualidade de recuperação (recall/precision do RAG não é medido).
