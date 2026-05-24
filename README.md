# Stock AI Analyzer

![Java](https://img.shields.io/badge/Java-26-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-brightgreen?logo=springboot)
![Angular](https://img.shields.io/badge/Angular-21-red?logo=angular)
![TypeScript](https://img.shields.io/badge/TypeScript-5-blue?logo=typescript)
![Python](https://img.shields.io/badge/Python-3.11+-yellow?logo=python)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791?logo=postgresql)

AI-powered investment analysis platform for Brazilian stocks (B3). Fetches real-time quotes and fundamentals via yfinance, runs a multi-dimensional scoring pipeline using a local LLM (Ollama), and delivers structured buy/hold/avoid recommendations through a REST API and WebSocket feed.

---

## Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 4, Java 26, Maven |
| Frontend | Angular 21, TypeScript |
| Database | PostgreSQL + pgvector (embeddings), Redis (cache) |
| Data source | yfinance (Python) for B3 quotes and fundamentals; BCB open API for macro data (Selic, IPCA) |
| LLM | Ollama — `qwen2.5:7b` for analysis, `nomic-embed-text` (768-dim) for embeddings |
| Sentiment | FinBERT via HuggingFace for news sentiment scoring |
| RAG | LangChain4j + pgvector for historical context retrieval |

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
│  │  2. Gather data        (fundamentals + macro + news)     │   │
│  │  3. RAG retrieval      (pgvector similarity search)      │   │
│  │  4. Build prompt       (sector-aware, 6-dimension)       │   │
│  │  5. LLM call           (Ollama qwen2.5:7b)               │   │
│  │  6. Parse + score      (DimensionScore × 6 → scoreGeral) │   │
│  │  7. Embed + store      (pgvector, nomic-embed-text)      │   │
│  │  8. Save history       (ScoreSnapshot via pgvector)      │   │
│  │  9. Persist alerts     (PostgreSQL, if Δscore > 1.5)     │   │
│  │ 10. Cache response     (Redis 30 min)                    │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  WebSocket (STOMP) ──► pushes quote updates to Angular         │
└─────────────────────────────────────────────────────────────────┘
```

### Investment Score

Each analysis produces six independent dimension scores (0–10), averaged into `scoreGeral`:

| Dimension | What it measures |
|---|---|
| Fundamentos | Earnings quality, margins, ROE/ROA, growth |
| Valuation | P/E, P/B vs sector; FCF as fair-value support |
| Regime/Momentum | Technical signals (RSI, MACD, SMAs, Bollinger) |
| Sentimento Institucional | FinBERT news sentiment adjusted by beta |
| Retorno ao Acionista | Dividend yield, payout consistency, FCF |
| Gestão de Risco | Debt/equity, Selic impact, FX exposure |

Score → recommendation mapping: `> 7.5` **COMPRAR** · `≥ 6.0` **MANTER** · `≥ 4.5` **AGUARDAR** · `< 4.5` **EVITAR**

---

## How to Run Locally

### Prerequisites

- Java 26+
- Maven 3.9+
- Node.js 20+ and npm
- Python 3.11+ with `pip install yfinance finbr transformers torch`
- [Ollama](https://ollama.ai) running locally with models pulled:
  ```bash
  ollama pull qwen2.5:7b
  ollama pull nomic-embed-text
  ```
- Docker or Podman (for PostgreSQL + Redis)

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
HUGGINGFACE_TOKEN=hf_your_token_here   # optional, for FinBERT
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

Before each LLM call, the service embeds the current fundamentals text using `nomic-embed-text` and queries pgvector for the 3 most similar historical segments (filtered by ticker). This historical context is injected into the prompt so the model can reason about trends, not just today's snapshot.

### Embeddings

Two types of vectors are stored in the `stock_embeddings` table:

| Type | Content | Purpose |
|---|---|---|
| `analysis` | Fundamentals text | RAG retrieval for future analyses |
| `score_history` | JSON ScoreSnapshot | Historical score querying |

Each vector carries metadata (`ticker`, `date`, `type`) enabling precise filtered search via pgvector.

### Scoring pipeline

The LLM receives a structured prompt with fundamentals, macro data (Selic, IPCA, USD/BRL, Brent), FinBERT sentiment score, technical indicators, RAG context, and sector-specific instructions. It returns raw JSON with six `{score, explicacao}` pairs plus a plain-language `simpleSummary`. The backend validates the JSON, derives the recommendation from `scoreGeral`, and caches the full `AnalysisResponse` for 30 minutes.

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
