# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projeto

**stock-ai-analyzer** — Sistema de análise de ações da B3 com IA. Exibe cotações em tempo real e gera um score de investimento baseado em 6 dimensões: Fundamentos, Valuation, Regime/Momentum, Sentimento Institucional, Retorno ao Acionista e Gestão de Risco.

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Spring Boot 4, Java 26, Maven |
| Frontend | Angular 21, TypeScript |
| Banco de dados | PostgreSQL (JPA: alertas, score history, portfolio) + pgvector (embeddings), Redis (cache) |
| Fonte de dados | Dados abertos da CVM (ITR/DFP/FCA) para fundamentos contábeis (LTM); yfinance (Python) para cotações, dados de mercado e fallback; APIs abertas do BCB para macro (Selic, IPCA, USD/BRL) e expectativas Focus |
| IA | Gemini 2.5 Flash (primário) + Groq qwen3-32b (fallback) via LangChain4j, temperature 0; embeddings nomic-embed-text via Ollama local |

## Comandos

### Backend (`/backend`)
```bash
./mvnw spring-boot:run          # inicia o servidor
./mvnw test                     # todos os testes
./mvnw test -Dtest=NomeTest     # teste específico
./mvnw package -DskipTests      # build sem testes
```

### Sidecar Python (`/backend/scripts`) — opcional, recomendado em dev
```bash
pip install -r requirements.txt                                  # dependências (yfinance, pandas, fastapi, uvicorn)
python -m uvicorn sidecar_app:app --host 127.0.0.1 --port 8001   # rodar de dentro de backend/scripts
```
Sem o sidecar no ar, o backend funciona normalmente via spawn de processo (mais lento: 2–5s de import do yfinance/pandas por chamada).

### Frontend (`/frontend`)
```bash
npm install        # instalar dependências
npm start          # inicia em dev (ng serve)
npm test           # testes unitários (ng test)
npm run build      # build de produção
```

## Arquitetura

### Fluxo principal
1. **Acesso a dados Python** centralizado no `PythonDataGateway`: prefere o **sidecar FastAPI** (`scripts/sidecar_app.py`, HTTP local na porta 8001, módulos yfinance/pandas já carregados) e cai para spawn de processo via `PythonScriptRunner` (timeout + leitura concorrente de streams) com cooldown de 30s quando o sidecar está fora do ar. Os scripts continuam funcionando standalone via CLI.
2. **Job agendado** (Spring `@Scheduled`, a cada 60s) busca cotações B3 via gateway (yfinance, sufixo `.SA`); cada cotação vai para o Redis com TTL curto. O frontend consome via polling REST — **não há WebSocket**.
3. **Pipeline de IA** (`StockAnalysisService`): coleta fundamentos, macro, notícias e indicadores técnicos **em paralelo** (virtual threads), recupera contexto RAG (apenas `historical_fundamentals` — análises passadas são excluídas para evitar feedback loop), monta o prompt com rubrica de pontuação e benchmarks setoriais, chama Gemini com fallback Groq. Fundamentos contábeis vêm dos demonstrativos oficiais da CVM (`scripts/cvm_data.py` — DRE LTM, balanço, DFC; zips cacheados em `scripts/.cvm_cache/`) com yfinance como fallback; a proveniência (`fundamentalsSource`, `statementDate`) entra no prompt.
4. **Validação**: `AnalysisParser` clampa scores em 0–10, rejeita dimensões ausentes e calcula `scoreGeral` em Java (a aritmética do LLM é descartada). Cada análise registra `modelUsed` e `promptVersion` (constante `StockAnalysisService.PROMPT_VERSION` — incrementar a cada mudança de prompt).
5. **Persistência**: score history em tabela JPA (`score_history`), alertas em PostgreSQL (Δscore > 1.5), embeddings no pgvector. `BacktestService` cruza scores com retornos realizados 30/90 dias (`GET /api/stocks/{ticker}/backtest`).
6. **Single-flight**: requisições simultâneas do mesmo ticker compartilham uma análise (lock por ticker); resultado cacheado no Redis por 30 min.

### Score de investimento
O score é composto por 6 dimensões independentes, cada uma com peso e explicação em linguagem natural gerada pela IA:
- Fundamentos
- Valuation
- Regime / Momentum
- Sentimento Institucional
- Retorno ao Acionista
- Gestão de Risco

### Módulos (backend)
- `stock` — cotações e serviço de leitura do cache
- `analysis` — orquestração do score, parser/validação, comparação, backtesting, integração LangChain4j
- `scheduler` — `PythonDataGateway` (sidecar HTTP com fallback de spawn), `PythonScriptRunner` (execução com timeout — usar só via gateway) e jobs de atualização/indexação
- `cache` — abstração sobre Redis (SCAN, nunca KEYS)
- `auth` / `user` / `portfolio` — OAuth2 Google + JWT, carteira do usuário

## Convenções de código

- **Idioma do código**: inglês — nomes de variáveis, métodos, classes e pacotes sempre em inglês.
- **Idioma dos comentários**: português — todos os comentários inline e Javadoc em português.
- Comentários apenas quando o *porquê* não é óbvio; não descrever o que o código já expressa.

## Regras de Qualidade

### Dependências Maven
- NUNCA adicione uma dependência sem antes verificar a versão exata no Maven Central (https://central.sonatype.com)
- SEMPRE rode `mvn dependency:resolve` após alterar o pom.xml para confirmar que as dependências baixam corretamente
- NUNCA unifique versões de módulos LangChain4j em uma única propriedade se eles tiverem ciclos de release diferentes
- Se uma versão não for encontrada, pesquise a versão mais recente disponível antes de tentar outra

### Build
- SEMPRE verifique se o projeto compila com `mvn clean compile` após qualquer alteração estrutural
- Se houver erro de compilação, corrija antes de continuar

### Imports Java
- NUNCA use uma classe sem verificar se ela existe na versão da dependência declarada no pom.xml
- Spring Boot 4 usa `tools.jackson.*` e não `com.fasterxml.jackson.*`

## Diretrizes Visuais Frontend (OBRIGATÓRIAS)

### Design System
- Framework CSS: SCSS puro com variáveis CSS — SEM Tailwind, SEM Bootstrap
- Cores definidas em `styles.scss` como variáveis CSS:
  ```
  --color-bg: #0a0f1e
  --color-surface: #0d1929
  --color-surface-2: #111827
  --color-accent: #00d4aa
  --color-accent-2: #f59e0b
  --color-danger: #ef4444
  --color-text: #e2e8f0
  --color-text-muted: #94a3b8
  --color-border: rgba(255,255,255,0.08)
  ```
- Tipografia: Syne (títulos/números), Inter (corpo) — importadas do Google Fonts
- Border-radius padrão: 8px para cards, 6px para inputs, 20px para badges
- Sombra padrão: `0 4px 24px rgba(0,0,0,0.4)`

### Proibições absolutas
- NUNCA usar gradientes roxos ou azuis genéricos
- NUNCA usar `border-radius` > 12px em cards
- NUNCA usar `font-family` genérica (Arial, Roboto, system-ui)
- NUNCA usar cores hardcoded — sempre usar variáveis CSS
- NUNCA criar layouts sem `max-width` definido
- NUNCA deixar componente sem estado de loading

### Padrões obrigatórios
- Todos os cards: `background var(--color-surface)`, `border 1px solid var(--color-border)`
- Todos os títulos de página: `font-family` Syne, `font-size` 2rem, `font-weight` 700
- Todas as barras de score: `height 8px`, `border-radius 4px`, animação CSS de 0 até o valor
- Badges de recomendação: `padding 6px 16px`, `font-size 12px`, `font-weight 600`, uppercase
- Max-width do conteúdo: 1280px, `margin 0 auto`, `padding 0 24px`
- Gap entre cards: 16px
- Spacing vertical entre seções: 32px

### Componentes específicos
- **Score gauge**: SVG circle com `stroke-dasharray` animado, número centralizado em Syne bold
- **Score bar**: `div` com `transition width 0.8s ease`, cor baseada no valor (vermelho `<4`, amarelo `4–6.5`, verde `>6.5`)
- **Stock card no dashboard**: `height 120px`, mostrar ticker + preço + variação + setor
- **Recommendation badge**: linguagem descritiva (Res. CVM 20/2021 — nunca COMPRAR/VENDER); cores fixas — `ATRATIVO=#00d4aa`, `NEUTRO=#3b82f6`, `CAUTELA=#f59e0b`, `DESFAVORÁVEL=#ef4444`

### Processo obrigatório para mudanças visuais
1. Ler este CLAUDE.md antes de qualquer mudança de CSS
2. Verificar se a variável CSS existe antes de criar nova
3. Compilar com `ng build` após cada mudança
4. Reportar o que foi alterado e por quê
