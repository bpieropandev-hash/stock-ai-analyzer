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
