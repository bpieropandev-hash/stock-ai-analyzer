# Coding Standards

## Idioma

- **Código** (variáveis, métodos, classes, pacotes): sempre inglês.
- **Comentários** (inline e Javadoc): sempre português.
- Comentário só quando o *porquê* não é óbvio — nunca descrever o que o código já expressa.

## Backend Java

- Java 26, Spring Boot 4 — `tools.jackson.*`, **não** `com.fasterxml.jackson.*` (erro comum ao reusar exemplos antigos). `RedisStockCache` já segue isso certo: `tools.jackson.databind.ObjectMapper` injetado, serialização manual (`writeValueAsString`/`readValue(json, StockQuote.class)`) com tipo explícito no ponto de leitura — não depende de default typing/polymorphism, então o gotcha comum de Jackson3+Redis (`GenericJacksonJsonRedisSerializer` sem `enableDefaultTyping` volta `LinkedHashMap` em vez do tipo certo) não se aplica aqui. Se um cache novo precisar guardar tipo polimórfico (hoje nenhum precisa), aí sim revisitar esse ponto.
- Records para DTOs imutáveis — é o padrão do projeto (`StockFundamentals`, `MacroData`, `TechnicalIndicators`, `SentimentResult`, `NewsItem`, etc.), não usar classe mutável para dado de passagem.
- `@Value` para config externalizada.
- Virtual threads para I/O paralelo (`Executors.newVirtualThreadPerTaskExecutor()`) — não usar `ExecutorService` de pool fixo para chamadas de rede.
- Nunca hardcodar credencial — tudo via `${VAR}` em `application.yml`, sem fallback fraco quando o segredo é sensível (padrão: `jwt.secret` não tem default).

## Entidades JPA

- Sem Lombok no projeto (confirmado — zero import `lombok.*`) — `@Data`/`@EqualsAndHashCode` não são um risco real hoje, mas se Lombok entrar algum dia: nunca `@Data` em entidade (gera `equals`/`hashCode` com campo mutável/associação, dispara lazy load, muda hash em memória).
- `equals`/`hashCode`: nenhuma entidade sobrescreve hoje (`Stock` e as demais usam identidade padrão do `Object`) — correto para entidade com ID gerado e sem chave natural estável. Só sobrescrever se a entidade ganhar uma chave natural (ex.: `ticker` em `Stock` já é `unique`, mas não é a PK) — nesse caso, base `equals`/`hashCode` só na chave natural, nunca em coleção/associação/campo mutável, e usar `instanceof` em vez de `getClass()` (proxy do Hibernate quebra `getClass()`).
- `GenerationType.IDENTITY` (`Stock`, `ScoreHistoryEntity`, `AnalysisAudit`) desabilita batch insert do Hibernate silenciosamente (precisa do ID gerado por linha antes do próximo insert) — `GenerationType.UUID` (`UserEntity`, `PortfolioItem`, `StockAlertEntity`) preserva batching. Não é problema hoje (sem insert em lote em lugar nenhum do código), só relevante se um fluxo de bulk-insert aparecer numa entidade `IDENTITY`.

## Dependências Maven

- Nunca adicionar sem checar versão exata no Maven Central antes.
- Sempre `mvn dependency:resolve` após alterar `pom.xml`.
- Nunca unificar versões de módulos LangChain4j numa propriedade única — ciclos de release independentes (ex.: `langchain4j-pgvector` fica pra trás de propósito).

## Testes Java

- **AssertJ** (`org.assertj.core.api.Assertions.assertThat`), não `org.junit.jupiter.api.Assertions` — desde 2026-08-07. Já vem transitivo via `spring-boot-starter-test` (`assertj-core`), sem mudança de `pom.xml`. `assertThatThrownBy(...).isInstanceOf(...)` no lugar de `assertThrows`. Testes antigos (`AnalysisParserTest`, `SectorBenchmarksTest`) já migrados — usar como referência de estilo.

## Análise estática (desde 2026-08-07)

- **Error Prone** ativo em todo `mvn compile`, modo **WARN** (`-XepAllErrorsAsWarnings`) — nunca falha o build. `error_prone_core:2.50.0` (única versão que entende os internals do javac do Java 26 — 2.39.0 e anteriores dão `ClassNotFoundException` em `com.sun.tools.javac.code.Flags$Flag`). Flags `--add-exports`/`--add-opens` para os internals do compiler vivem em `backend/.mvn/jvm.config`, **não** em `compilerArgs` do `maven-compiler-plugin` — `--add-opens` não tem nenhum efeito ali, precisa estar no lançamento da própria JVM que roda o processador de anotação (a do Maven, já que `fork=false`).
- **NullAway — NÃO incluído, bloqueio real verificado**: `nullaway:0.12.7` quebra contra `error_prone_core:2.50.0` (`ClassNotFoundException: com.google.errorprone.predicates.type.DescendantOf` — API interna do Error Prone mudou depois do release do NullAway). Não existe hoje uma combinação de versões que funcione com Java 26. Revisitar quando o NullAway lançar uma versão compatível com Error Prone 2.5x+.
- **SpotBugs — declarado no `pom.xml` mas inerte, bloqueio real verificado**: `spotbugs-maven-plugin:4.9.3.0` usa uma versão do ASM que não lê bytecode Java 26 (`IllegalArgumentException: Unsupported class file major version 70`). Sem `<executions>` vinculado — não roda no build padrão. Revisitar quando uma versão com ASM atualizado sair; testar via `mvn compile com.github.spotbugs:spotbugs-maven-plugin:4.9.3.0:check` antes de reativar.
- Nenhum desses achados foi promovido pra travar o build (`ERROR`) — codebase nunca teve disciplina de análise estática antes, então o primeiro passo é só visibilidade. Promover pra `ERROR` é decisão separada, deliberada, não silenciosa.

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
