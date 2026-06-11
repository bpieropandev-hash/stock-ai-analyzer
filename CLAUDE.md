# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projeto

**stock-ai-analyzer** — Sistema de análise de ações da B3 com IA. Exibe cotações em tempo real e gera um score de investimento baseado em 6 dimensões: Fundamentos, Valuation, Regime/Momentum, Sentimento Institucional, Retorno ao Acionista e Gestão de Risco.

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Spring Boot 4, Java 26, Maven |
| Frontend | Angular 21, TypeScript |
| Banco de dados | PostgreSQL + pgvector (embeddings), Redis (cache) |
| Fonte de dados | yfinance (Python) para cotações e fundamentos B3, finbr como fallback, BCB API aberta para dados macroeconômicos (Selic, IPCA, CDI) |
| IA | Anthropic Claude API + LangChain4j (RAG e embeddings) |

## Comandos

### Backend (`/backend`)
```bash
./mvnw spring-boot:run          # inicia o servidor
./mvnw test                     # todos os testes
./mvnw test -Dtest=NomeTest     # teste específico
./mvnw package -DskipTests      # build sem testes
```

### Frontend (`/frontend`)
```bash
npm install        # instalar dependências
npm start          # inicia em dev (ng serve)
npm test           # testes unitários (ng test)
npm run build      # build de produção
```

## Arquitetura

### Fluxo principal
1. **Job agendado** (Spring `@Scheduled`) invoca `scripts/fetch_stock.py` via `ProcessBuilder`. O script Python busca cotações e fundamentos B3 usando yfinance (sufixo `.SA`), retorna JSON no stdout. O backend lê o JSON e salva cada cotação no Redis como cache de curto prazo.
2. **WebSocket** (Spring WebSocket / STOMP) empurra atualizações de cotação para o frontend em tempo real.
3. **Pipeline de IA** analisa os dados de cada ação usando a Claude API via LangChain4j, gerando um score explicado por dimensão.
4. **pgvector** armazena embeddings para RAG — contexto histórico e fundamentalista é recuperado antes de cada análise.

### Score de investimento
O score é composto por 6 dimensões independentes, cada uma com peso e explicação em linguagem natural gerada pela IA:
- Fundamentos
- Valuation
- Regime / Momentum
- Sentimento Institucional
- Retorno ao Acionista
- Gestão de Risco

### Módulos esperados (backend)
- `stock` — entidades, repositórios e serviço de cotação
- `analysis` — orquestração do score, integração com LangChain4j
- `scheduler` — jobs de atualização de dados via script Python (yfinance)
- `websocket` — configuração STOMP e broadcasting
- `cache` — abstração sobre Redis

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
- **Recommendation badge**: cores fixas — `COMPRAR=#00d4aa`, `MANTER=#3b82f6`, `AGUARDAR=#f59e0b`, `EVITAR=#ef4444`

### Processo obrigatório para mudanças visuais
1. Ler este CLAUDE.md antes de qualquer mudança de CSS
2. Verificar se a variável CSS existe antes de criar nova
3. Compilar com `ng build` após cada mudança
4. Reportar o que foi alterado e por quê
