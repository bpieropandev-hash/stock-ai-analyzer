# Coding Standards

## Idioma

- **Código** (variáveis, métodos, classes, pacotes): sempre inglês.
- **Comentários** (inline e Javadoc): sempre português.
- Comentário só quando o *porquê* não é óbvio — nunca descrever o que o código já expressa.

## Backend Java

- Java 26, Spring Boot 4 — `tools.jackson.*`, **não** `com.fasterxml.jackson.*` (erro comum ao reusar exemplos antigos).
- Records para DTOs imutáveis — é o padrão do projeto (`StockFundamentals`, `MacroData`, `TechnicalIndicators`, `SentimentResult`, `NewsItem`, etc.), não usar classe mutável para dado de passagem.
- `@Value` para config externalizada.
- Virtual threads para I/O paralelo (`Executors.newVirtualThreadPerTaskExecutor()`) — não usar `ExecutorService` de pool fixo para chamadas de rede.
- Nunca hardcodar credencial — tudo via `${VAR}` em `application.yml`, sem fallback fraco quando o segredo é sensível (padrão: `jwt.secret` não tem default).

## Dependências Maven

- Nunca adicionar sem checar versão exata no Maven Central antes.
- Sempre `mvn dependency:resolve` após alterar `pom.xml`.
- Nunca unificar versões de módulos LangChain4j numa propriedade única — ciclos de release independentes (ex.: `langchain4j-pgvector` fica pra trás de propósito).

## Build

- Sempre `mvn clean compile` após alteração estrutural — zero warning antes de reportar conclusão.
- Nunca considerar tarefa finalizada só porque compilou — rodar testes afetados, checar imports, checar documentação.

## Frontend Angular

- Standalone components, sem NgModules — não introduzir NgModule novo.
- SCSS puro com variáveis CSS — sem Tailwind, sem Bootstrap (ver `frontend.md` para o design system completo).
- Sem biblioteca de UI/chart — componentes são hand-rolled (SVG/CSS inline). Antes de adicionar uma lib nova (Material, Chart.js etc.), confirmar que é intencional — não é o padrão atual do projeto.

## Git

- Commitar quando algo funciona.
- Mensagens em inglês, prefixo `feat`/`fix`/`chore`/`refactor`/`docs`.

## Erros

- Nunca ignorar warning.
- Sempre mostrar causa raiz, não só sintoma.
- Compilar e testar antes de reportar conclusão.

## Ao mudar arquitetura

Atualizar `docs/ai/architecture.md` (e o arquivo específico afetado — `backend.md`, `frontend.md`, `ai.md` etc.) na mesma tarefa, não depois.
