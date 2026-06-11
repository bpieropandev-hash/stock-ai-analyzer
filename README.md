# Stock AI Analyzer

![Java](https://img.shields.io/badge/Java-26-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-brightgreen?logo=springboot)
![Angular](https://img.shields.io/badge/Angular-21-red?logo=angular)
![TypeScript](https://img.shields.io/badge/TypeScript-5-blue?logo=typescript)
![Python](https://img.shields.io/badge/Python-3.11+-yellow?logo=python)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791?logo=postgresql)

AI-powered investment analysis platform for Brazilian stocks (B3). Fetches quotes and fundamentals via yfinance, runs a multi-dimensional scoring pipeline using Gemini 2.5 Flash (with Groq fallback), and delivers structured analysis through a REST API. Quotes are refreshed every 60s by a scheduled job and served from Redis.

---

## Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 4, Java 26, Maven |
| Frontend | Angular 21, TypeScript |
| Database | PostgreSQL (JPA: alerts, score history, portfolio) + pgvector (embeddings), Redis (cache) |
| Data source | yfinance (Python) for B3 quotes and fundamentals; BCB open APIs for macro data (Selic, IPCA, USD/BRL) and Focus market expectations |
| LLM | Gemini 2.5 Flash (primary) with Groq `qwen/qwen3-32b` fallback, via LangChain4j OpenAI-compatible client, temperature 0 |
| Embeddings | Ollama `nomic-embed-text` (768-dim), local |
| Sentiment | Lexicon-based financial sentiment scoring over news headlines (PT/EN) |
| RAG | LangChain4j + pgvector for historical fundamentals retrieval |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Spring Boot Backend                     │
│                                                                 │
│  ┌──────────────┐   Python scripts (ProcessBuilder)            │
│  │ @Scheduled   │──► fetch_stock.py        → StockQuote        │
│  │ StockFetchJob│──► fetch_fundamentals.py → StockFundamentals │
│  └──────────────┘──► fetch_macro.py        → MacroData         │
│                  ──► fetch_news.py         → List<NewsItem>     │
│                  ──► analyze_sentiment.py  → SentimentResult   │
│                  ──► fetch_technical_indicators.py             │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ StockAnalysisService                                    │   │
│  │  1. Redis cache check  (analysis:{TICKER}, TTL 30 min)  │   │
│  │  2. Gather data        (fundamentals + macro + news +    │   │
│  │                         technicals, parallel via         │   │
│  │                         virtual threads)                 │   │
│  │  3. RAG retrieval      (pgvector — historical            │   │
│  │                         fundamentals only)               │   │
│  │  4. Build prompt       (sector-aware, scoring rubric,    │   │
│  │                         sector benchmarks, Focus data)   │   │
│  │  5. LLM call           (Gemini 2.5 Flash → Groq fallback)│   │
│  │  6. Parse + validate   (scores clamped 0-10; scoreGeral  │   │
│  │                         computed in Java, never by LLM)  │   │
│  │  7. Embed + store      (pgvector, nomic-embed-text)      │   │
│  │  8. Save history       (score_history table, JPA, with   │   │
│  │                         model + prompt version)          │   │
│  │  9. Persist alerts     (PostgreSQL, if Δscore > 1.5)     │   │
│  │ 10. Cache response     (Redis 30 min)                    │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  Frontend polls REST endpoints (no WebSocket)                   │
└─────────────────────────────────────────────────────────────────┘
```

### Investment Score

Each analysis produces six independent dimension scores (0–10), averaged into `scoreGeral`:

| Dimension | What it measures |
|---|---|
| Fundamentos | Earnings quality, margins, ROE/ROA, growth |
| Valuation | P/E, P/B vs sector benchmark ranges; FCF as fair-value support |
| Regime/Momentum | Technical signals (RSI, MACD, SMAs, Bollinger) |
| Sentimento Institucional | Lexicon-based news headline sentiment adjusted by beta |
| Retorno ao Acionista | Dividend yield, payout consistency, FCF |
| Gestão de Risco | Debt/equity, Selic impact, FX exposure |

`scoreGeral` is computed in Java as the arithmetic mean of the six dimensions (the LLM's own arithmetic is discarded). Score → recommendation mapping: `> 7.5` **COMPRAR** · `≥ 6.0` **MANTER** · `≥ 4.5` **AGUARDAR** · `< 4.5` **EVITAR**

Each persisted analysis records `modelUsed` and `promptVersion` for auditability — scores from different models/prompt versions are not directly comparable.

---

## How to Run Locally

### Prerequisites

- Java 26+
- Maven 3.9+
- Node.js 20+ and npm
- Python 3.11+ with `pip install yfinance pandas`
- [Ollama](https://ollama.ai) running locally (embeddings only):
  ```bash
  ollama pull nomic-embed-text
  ```
- Gemini API key (primary LLM) and Groq API key (fallback)
- Podman (for PostgreSQL + Redis)

### 1. Start infrastructure

```bash
# PostgreSQL with pgvector + Redis
podman compose up -d
```

Or manually:

```bash
podman run -d --name postgres \
  -e POSTGRES_DB=stockai -e POSTGRES_USER=stockai_user -e POSTGRES_PASSWORD=secret \
  -p 5432:5432 pgvector/pgvector:pg16

podman run -d --name redis -p 6379:6379 redis:7-alpine
```

### 2. Configure environment

Create a `.env` file at the project root (next to `/backend`):

```env
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=stockai
POSTGRES_USER=stockai_user
POSTGRES_PASSWORD=secret
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
OLLAMA_BASE_URL=http://localhost:11434
GEMINI_API_KEY=your_gemini_key
GROQ_API_KEY=your_groq_key
GOOGLE_CLIENT_ID=your_oauth_client_id
GOOGLE_CLIENT_SECRET=your_oauth_client_secret
JWT_SECRET=random_32_chars_minimum        # required — no fallback
```

### 3. Start the backend

```bash
cd backend
./mvnw spring-boot:run
# Listens on http://localhost:8080
```

### 4. Start the frontend

```bash
cd frontend
npm install
npm start
# Opens http://localhost:4200
```

---

## API Endpoints

### Stock quotes

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/stocks` | All cached quotes |

### Analysis

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/stocks/{ticker}/analysis` | Get or generate analysis (cache-first) |
| `POST` | `/api/stocks/{ticker}/analysis/refresh` | Force recompute, bypass cache |
| `GET` | `/api/stocks/{ticker}/score-history?days=30` | Historical score snapshots |
| `GET` | `/api/stocks/{ticker}/backtest` | Score vs realized 30/90-day forward returns (Pearson correlation) |

**Example:**
```bash
curl http://localhost:8080/api/stocks/PETR4/analysis
```

### Comparison

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/compare?tickers=PETR4,VALE3,ITUB4` | Compare up to 5 tickers side by side |

**Example response fields:** `ranking` (sorted by score), `bestForDividends`, `bestMomentum`, `lowestRisk`.

### Portfolio simulation

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/simulate` | Allocate a portfolio amount proportionally to scores |

**Request body:**
```json
{
  "amount": 10000.0,
  "tickers": ["PETR4", "VALE3", "ITUB4", "WEGE3"]
}
```
Omit `tickers` to use the 10 default monitored stocks. Only **COMPRAR** and **MANTER** stocks receive allocation; others appear in `excludedTickers`.

### Alerts

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/alerts?days=7` | Recent score alerts (default: last 7 days) |
| `GET` | `/api/alerts/{ticker}` | All alerts for a specific ticker |

An alert is triggered when `|Δscore| > 1.5` between consecutive daily analyses.

---

## How It Works

### RAG (Retrieval-Augmented Generation)

Before each LLM call, the service embeds the current fundamentals text using `nomic-embed-text` and queries pgvector for the 3 most similar **historical fundamentals** segments (filtered by ticker and `type=historical_fundamentals`). Past LLM analyses are deliberately excluded from retrieval — feeding the model its own previous scores would create an anchoring feedback loop.

### Embeddings

Vectors stored in the `stock_embeddings` table:

| Type | Content | Purpose |
|---|---|---|
| `historical_fundamentals` | Quarterly fundamentals snapshots | RAG retrieval (re-indexed daily with dedup) |
| `analysis` | Past analysis text | Stored for future use; excluded from RAG retrieval |

Score history lives in the relational `score_history` table (JPA), not in pgvector.

### Scoring pipeline

The LLM receives a structured prompt with fundamentals, macro data (Selic, IPCA, USD/BRL, Brent, Focus expectations), lexicon sentiment score, technical indicators, RAG context, sector-specific instructions, sector benchmark ranges, and an explicit scoring rubric with anchors per dimension. It reasons in an `analise` field before scoring, then returns six `{score, explicacao}` pairs plus a plain-language `simpleSummary`. The backend clamps each score to 0–10, rejects responses with missing dimensions, computes `scoreGeral` itself, derives the recommendation, and caches the full `AnalysisResponse` for 30 minutes. Concurrent requests for the same ticker share a single analysis (per-ticker lock).

### Sector awareness

Each of the 9 sectors (ENERGIA, FINANCEIRO, VAREJO, MINERACAO, BEBIDAS, INDUSTRIA, LOGISTICA, PAPEL_CELULOSE, OUTROS) has dedicated prompt instructions that tune how the LLM interprets metrics. For example, Selic rate increases are modeled as margin benefits for FINANCEIRO but as cost headwinds for VAREJO.

---

## Monitored Stocks

| Ticker | Company | Sector |
|---|---|---|
| PETR4 | Petrobras | Energy |
| VALE3 | Vale | Mining |
| ITUB4 | Itaú Unibanco | Financial |
| BBDC4 | Bradesco | Financial |
| WEGE3 | WEG | Industry |
| MGLU3 | Magazine Luiza | Retail |
| ABEV3 | Ambev | Beverages |
| B3SA3 | B3 Exchange | Financial |
| RENT3 | Localiza | Logistics |
| SUZB3 | Suzano | Pulp & Paper |
