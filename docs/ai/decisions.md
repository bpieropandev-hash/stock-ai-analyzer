# Decisões arquiteturais (ADR-lite)

Registro leve de decisões não-óbvias — cada uma existe porque a alternativa óbvia foi tentada/considerada e rejeitada por um motivo concreto. Não reverter sem entender o motivo.

## `scoreGeral` sempre recalculado em Java

**Decisão**: nunca usar o valor de `scoreGeral` retornado pelo LLM.
**Motivo**: LLMs erram aritmética simples de forma inconsistente; confiar nisso geraria scores incoerentes com as 6 dimensões individuais.
**Ver também**: `financial-rules.md`.

## RAG exclui análises passadas (`type=analysis`)

**Decisão**: contexto de RAG usa só `historical_fundamentals`, nunca `analysis`.
**Motivo**: recuperar análises passadas criaria feedback loop — o LLM ancoraria no próprio score anterior em vez de reavaliar fundamentos de forma independente, contaminando a série histórica.
**Ver também**: `rag.md`.

## Temperature 0 nos dois LLMs

**Decisão**: Gemini e Groq configurados com `temperature(0.0)`.
**Motivo**: scoring precisa ser determinístico; variância de amostragem dispararia alertas de mudança de score por ruído estatístico, não por fato novo.

## CVM como fonte primária de fundamentos, yfinance como fallback

**Decisão**: overlay dos dados CVM sobre o yfinance, não o contrário.
**Motivo**: múltiplos do yfinance para tickers B3 são frequentemente errados ou defasados (validado por checagem cruzada: lucro LTM da VALE3 calculado via CVM bateu com `netIncomeToCommon` do yfinance ao milhar).

## Sidecar Python (FastAPI) em vez de reescrever coleta em Java

**Decisão**: manter yfinance/pandas em Python, expostos via HTTP local persistente, em vez de portar para uma lib Java equivalente.
**Motivo**: não existe equivalente maduro de yfinance na JVM; reescrever seria caro e arriscado. Sidecar persistente elimina o custo de cold-start (medido: ~1,5s quente vs 3,5s+ frio via spawn de processo).
**Trade-off aceito**: dois runtimes para operar/deployar (Java + Python), sem Dockerfile ainda para nenhum dos dois (ver `anti-patterns.md`).

## Flyway substituindo `ddl-auto: update` (resolvido em 2026-08-06)

**Decisão**: migrar de `ddl-auto: update` para Flyway com `ddl-auto: validate`.
**Motivo original da dívida**: velocidade de iteração em fase de protótipo — aceito conscientemente, não por acidente (ver histórico abaixo).
**Como foi resolvido**: `spring-boot-starter-flyway` + `flyway-database-postgresql` (Spring Boot 4 exige o starter — `flyway-core` sozinho no classpath não roda migração automática). `V1__baseline_schema.sql` reproduz exatamente o schema que `ddl-auto: update` já tinha gerado. `spring.flyway.baseline-on-migrate: true` + `baseline-version: 1` cobre os dois casos: banco de dev já existente é marcado na v1 sem reexecutar o script; ambiente novo roda V1 normalmente. Verificado: schema vazio → V1 aplica → Hibernate `validate` passa sem erro → idempotente numa segunda execução.
**Consequência**: toda mudança de entidade agora exige uma migration `V{n+1}__*.sql` nova — `ddl-auto` não altera mais o banco. Ver `playbooks/database-change.md` e `backend.md`.

### Histórico da decisão original (`ddl-auto: update` por ora)
Documentado por completude — decisão já superada acima, mas o raciocínio original continua válido para decisões futuras do mesmo tipo (aceitar dívida conscientemente, com critério explícito de quando resolver): "sempre sugerir Flyway quando mudanças estruturais forem frequentes" (regra que motivou a resolução acima) é o tipo de critério que vale registrar de novo da próxima vez que uma dívida semelhante for aceita de propósito.

## FinBERT-PT-BR real com fallback léxico automático (resolvido em 2026-08-07)

**Decisão**: `sidecar_app.py` tenta classificar as manchetes via **FinBERT-PT-BR** (`lucas-leme/FinBERT-PT-BR`, Apache-2.0, 169k downloads/mês, treinado em 1,4M notícias financeiras PT-BR — confirmado real via busca antes de implementar, não inventado) na HF Inference API antes do léxico; sem token ou em qualquer falha, cai pro léxico existente (`analyze_sentiment.py`), que continua funcionando standalone e como fallback via spawn de processo.
**Motivo**: sentimento lexical é explicitamente rotulado no prompt como "sinal de confiança limitada" desde sempre — roadmap P1-9 pedia avaliar um classificador real. `huggingface.token` já existia em config, sem consumidor.
**Pesquisa antes de implementar**: modelo e formato de API (header `Authorization: Bearer`, payload `{"inputs": texto}`, resposta `[{label,score}]`) confirmados via busca na doc oficial da Hugging Face antes de escrever qualquer código — não adivinhado. A URL exata do endpoint (`router.huggingface.co/hf-inference/models/{id}`) não foi possível confirmar 100% via doc (componente React não renderiza em fetch de texto); fica para validação do usuário com token real, ver limitação abaixo.
**Design**: `_aggregate(labels, confidences)` extraído de `analyze_sentiment.analyze()` pra fórmula única compartilhada entre os dois caminhos — score cai na mesma escala 0-10 independente da fonte, comparável entre si. `finbert_sentiment.classify()` nunca lança — qualquer falha (sem token, timeout 8s, HTTP erro, schema inesperado) retorna `None`, e `sidecar_app.py` cai pro léxico automaticamente (mesmo padrão de resiliência de macro/notícias/técnico — invariante de funcionalidade opcional nunca bloquear).
**Novo campo `SentimentResult.source`** (`"finbert"`/`"lexical"`/`"unavailable"`) — `buildSentimentText` usa isso pra escolher o caveat certo no prompt (FinBERT = "sinal confiável", léxico = "confiança limitada"). **`PROMPT_VERSION` v2.3 → v2.4** — texto do prompt mudou (invariante #9).
**Escopo deliberadamente excluído**: fallback via spawn de processo (`PythonScriptRunner`, sidecar fora do ar) continua só léxico — já é caminho degradado, não estendido ao FinBERT.
**Config**: `HUGGINGFACE_TOKEN` precisa estar no ambiente do processo que sobe o sidecar Python — diferente do Java, `.env` não é carregado automaticamente pro Python (dois runtimes, dois mecanismos, ver decisão do sidecar Python). Documentado em `.env.example`.
**Verificado**: `mvn clean compile`/testes 14/14 verde, sintaxe Python válida (`py_compile`), smoke test do léxico refatorado confirma mesmo schema + `source: "lexical"`, `finbert_sentiment.classify()` confirmado retornando `None` sem token (fallback funciona). **Limitação real**: sem `HUGGINGFACE_TOKEN` válido nesta sessão, não foi possível confirmar que a chamada real à API funciona ponta a ponta (URL exata, formato de resposta do modelo específico) — precisa de teste do usuário com token real antes de considerar "funcionando em produção", não só "implementado".

## Tooling: Error Prone + AssertJ + Dependabot; NullAway/SpotBugs bloqueados (2026-08-07)

**Decisão**: Error Prone ativo em modo WARN em todo `mvn compile`, testes migrados pra AssertJ, `dependabot.yml` criado (maven backend + npm frontend, agrupando `dev.langchain4j:*` numa PR só dado o ciclo de release próprio já documentado). NullAway e SpotBugs **não** entraram — bloqueio real de versão com Java 26, não escolha de escopo.
**Motivo**: rodada de sugestões de tooling do GPT (skills/MCP/hooks/libs) — avaliada item a item por custo de manutenção vs. ganho técnico pra projeto solo, não squad. Detalhe dessa avaliação completa não está aqui (foi conversa, não decisão de código); o que importa registrar é o que foi de fato implementado e por quê os dois itens ficaram de fora.
**NullAway bloqueado, verificado, não suposto**: `nullaway:0.12.7` (mais recente disponível) espera uma classe (`com.google.errorprone.predicates.type.DescendantOf`) que não existe mais em `error_prone_core:2.50.0` (única versão de Error Prone que entende os internals do javac do Java 26 — testei `2.39.0` também, que falha diferente: `ClassNotFoundException` em `com.sun.tools.javac.code.Flags$Flag`, JDK novo demais pra ela). As duas ferramentas evoluem em ritmos diferentes e não convergiram ainda pro Java 26 (release muito recente — ~5 meses). Sem combinação de versão que funcione hoje.
**SpotBugs bloqueado, verificado**: `spotbugs-maven-plugin:4.9.3.0` carrega bytecode via uma versão do ASM que não reconhece class file major version 70 (Java 26). `IllegalArgumentException: Unsupported class file major version 70` ao tentar analisar as próprias classes compiladas do projeto. Plugin fica declarado no `pom.xml` (sem `<executions>`, não roda em build normal) pra reativar quando uma versão compatível sair — não é decisão de escopo, é bloqueio técnico de dependência externa.
**Erro de configuração real cometido e corrigido no processo**: primeira tentativa de configurar Error Prone passou `--add-exports`/`--add-opens` como `compilerArgs` do `maven-compiler-plugin` com prefixo `-J`, que falhou silenciosamente (zero diagnóstico, `Compilation failure` sem detalhe — sintoma de JVM forkada morrendo na largada). `--add-opens` é flag de JVM (acesso reflexivo em runtime), não de javac — não tem efeito nenhum em `compilerArgs` mesmo com fork. O processador de anotação roda na MESMA JVM que compila; com `fork=false` (padrão), isso é a própria JVM do Maven, que precisa dessas flags no seu próprio lançamento — daí `backend/.mvn/jvm.config`, não `compilerArgs`.
**AssertJ**: já vinha transitivo via `spring-boot-starter-test` (`assertj-core:3.27.7`, confirmado via `mvn dependency:tree`) — zero mudança de `pom.xml`, só troca de estilo. `AnalysisParserTest`/`SectorBenchmarksTest` migrados como referência.
**36 achados reais do Error Prone** (modo WARN, não corrigidos nesta tarefa — escopo era habilitar a ferramenta, não sanear achado por achado): 19× `JavaTimeDefaultTimeZone` (uso de `LocalDate.now()`/`LocalDateTime.now()` sem timezone explícito — relevante pra app financeiro), 7× `StringCaseLocaleUsage` (`.toUpperCase()` sem `Locale.ROOT` — inclusive em `TickerNormalizer`, a classe que existe justamente pra centralizar normalização de ticker), 3× `NonApiType`, 3× `MissingSummary`, 2× `JavaUtilDate`, 1× `UnusedVariable`, 1× `CanonicalDuration`. Corrigir isso é tarefa separada, não decidida ainda.
**Verificado**: `mvn clean compile`/testes 14/14 verde com Error Prone ativo. `dependabot.yml` não pode ser "testado" localmente — só ativa de verdade quando o repo tiver Dependabot habilitado nas configurações do GitHub (fora do meu alcance, é ação do usuário).

## Explicação de mudança de score (resolvido em 2026-08-07)

**Decisão**: `ScoreChangeExplanation`/`ScoreChangeCalculator` (função pura) compara o `scoreGeral`/dimensões da análise atual com o registro anterior em `score_history` do mesmo ativo, identifica a dimensão que mais moveu (maior `|delta|`) e reusa a `explicacao` que o LLM já dá pra essa dimensão — nenhum dado novo coletado, só cruzamento do que já existe. `null` quando não há análise anterior (primeira vez que o ticker é analisado) — sem "mudança" possível.
**Motivo**: último item do sprint 3 do GPT ("diferencial"). Diferente de `StockAlert` (que só dispara acima do threshold de 1.5 e guarda só magnitude/direção, sem explicar o porquê), isso mostra a comparação sempre que há histórico, com o texto de explicação da dimensão que mais pesou.
**Ordem de operações importante**: `getFullHistory(ticker)` é chamado **antes** de `scoreHistoryService.saveScore(...)` em `StockAnalysisService.doAnalyze` — se fosse depois, a análise atual já estaria no histórico e "anterior" seria ela mesma.
**Frontend**: card compacto (`change-card`) no `col-left`, entre o resumo e a lista de dimensões — score anterior → atual com seta colorida, e a dimensão que mais mudou com a explicação do LLM. Sem seção nova de "timeline" nem gráfico — isso seria a feature "timeline da empresa", bloqueada por gap de dado (P2-15), fora de escopo aqui.
**Verificado**: `mvn clean compile`/testes 14/14, `ng build --configuration production` limpo. **Mesma limitação das Fases 13/14/15**: sem credenciais de LLM válidas nesta sessão, não foi possível gerar duas análises reais em sequência pra ver o card renderizando com dado de verdade.

## Score Confidence + backtest visual (resolvido em 2026-08-07)

**Decisão**: `ScoreConfidenceCalculator` (função pura, mesmo estilo de `TickerNormalizer`) agrega 4 sinais de qualidade de dado já existentes no sistema numa métrica única 0-10, exposta em `AnalysisResponse.confidence`. `modelUsed`/`promptVersion`/resultado do backtest passam a ser exibidos na tela de análise (endpoint `GET /api/stocks/{ticker}/backtest` já existia, nunca consumido pelo frontend).
**Motivo**: GPT (ver histórico da sessão) apontou que os ingredientes pro Score Confidence já existiam no sistema (`fundamentalsSource`, `SentimentResult.confidence`/`source`, presença de `TechnicalIndicators`, dinamismo do benchmark setorial) — era agregação de dado existente, não coleta nova, baixo esforço real. Concordei e priorizei isso antes de features maiores do roadmap.
**Fórmula** (`ScoreConfidenceCalculator.calculate`): média de 4 componentes, escala 0-10 (mesma convenção do `scoreGeral`):
- `fundamentalsQuality`: 10 se `fundamentalsSource == "cvm+yfinance"`, 5 se só `"yfinance"`.
- `sentimentQuality`: 10×confiança se `source == "finbert"`, 5×confiança se `"lexical"`, 0 se `"unavailable"`.
- `technicalDataAvailable`: 10 se `TechnicalIndicators` não é `null`, senão 0.
- `sectorBenchmarkDynamic`: 10 se veio de medianas reais de pares, 4 se caiu pra faixa estática (ainda é sinal útil, só menos preciso).
**Mudança de suporte necessária em `SectorBenchmarks`**: `describe()` só retornava a string pro prompt, sem sinalizar se veio do caminho dinâmico ou do fallback estático. Adicionado `describeWithMeta()`/`BenchmarkInfo(text, dynamic)` — `describe()` continua existindo, delegando pra isso, sem quebrar nenhum call site. `buildPrompt` também mudou de assinatura pra receber o `benchmarkSection` já computado (evita chamar `describeWithMeta` duas vezes — uma pro prompt, outra pra confidence — o que dobraria chamadas ao sidecar/Redis em cache miss).
**Frontend**: badge de confiança (cor por faixa, breakdown no tooltip nativo) ao lado do `model-tag` existente; nova seção "BACKTEST" full-width abaixo do grid principal, carregada de forma assíncrona separada do `analyze()` principal — falha no backtest não afeta a análise (estado de erro próprio, visível, não silenciado — segue a regra do próprio `frontend.md` de sempre ter loading+erro). Sem lib de gráfico nova: reusa o padrão hand-rolled SVG/CSS já estabelecido (gauge, barras de dimensão) em vez de adicionar dependência npm.
**Ajuste incidental**: budget `anyComponentStyle` do Angular (8kB→10kB, `angular.json`) — o componente de análise já estava perto do limite antes dessa mudança; cresceu com feature real, não bloat acumulado.
**Verificado**: `mvn clean compile`/testes 14/14, `ng build --configuration production` limpo (zero warnings, inclusive o de budget que apareceu e foi corrigido). **Limitação**: sem credenciais de LLM válidas nesta sessão, não foi possível rodar uma análise real no navegador pra confirmar visualmente o badge/seção de backtest renderizando com dado de verdade — só a compilação/build foram verificados, não o resultado visual.

## Tabela de auditoria completa (resolvido em 2026-08-07)

**Decisão**: criar `analysis_audit` (`com.stockai.analysis`) — FK simples 1:1 pra `score_history.id` (não `@ManyToOne` mapeado), guardando `promptFull`, `rawResponse`, `reasoning`, `resumo` e a `explicacao` de cada uma das 6 dimensões.
**Motivo**: até então só `modelUsed`/`promptVersion` eram persistidos — impossível reconstruir por que um score específico saiu de um jeito sem o prompt/resposta exatos (roadmap P2-10, citado também em `project-principles.md` como limitador do pilar Auditabilidade).
**Achado incidental no levantamento pré-implementação**: o prompt já pede um campo `"analise"` (raciocínio antes de pontuar, texto livre) desde a v2 — mas `AnalysisParser.parse` nunca extraía esse campo do JSON, só `resumo`/`simpleSummary`. Corrigido junto: `ParsedAnalysis` ganhou um 3º campo `reasoning`, exposto só pra auditoria (não pra `AnalysisResponse`/frontend — isso seria a feature separada "explicação de score" do roadmap P3, fora de escopo aqui).
**Design**: FK simples (`Long scoreHistoryId`), não associação JPA — tabela de escrita frequente/leitura rara, não faz sentido carregar grafo de objeto pra isso (contraste deliberado com o `@ManyToOne EAGER` de `Stock` nas outras 3 entidades, onde o padrão de acesso é o oposto). Persistência best-effort (`AnalysisAuditService`, try/catch sem propagar) — mesmo padrão defensivo de `ScoreHistoryService`/`ScoreAlertService`, uma falha aqui nunca derruba uma análise que já foi gerada e paga (chamada de LLM).
**Escopo deliberadamente excluído**: sem endpoint de leitura/API — consulta é via SQL direto por enquanto. Expor via API (ex.: endpoint de debug/admin) é decisão futura separada, não antecipada aqui.
**Verificado**: migration `V3__create_analysis_audit.sql` (aditiva, só tabela nova) aplicada contra Postgres real, `ddl-auto: validate` passou, suíte 14/14 verde. **Limitação da verificação**: não foi possível exercitar uma chamada real ao Gemini/Groq nesta sessão (sem credenciais válidas — `.env` de teste ainda não restaurado, ver incidente da Fase 8 do histórico) — a gravação em `analysis_audit` foi verificada por leitura de código e pela migration/schema, não por um `POST /analysis` real ponta a ponta.

## Entidade `Stock` canônica substituindo ticker solto (resolvido em 2026-08-07)

**Decisão**: criar `Stock` (`com.stockai.stock`) — `id`, `ticker` (unique, canônico), `createdAt` — e trocar a coluna `ticker` de `portfolio_items`, `score_history` e `stock_alerts` por FK `stock_id`. Criação sob demanda via `StockRepository.findOrCreate`, nunca cadastro manual.
**Motivo**: as 3 tabelas guardavam ticker como string sem constraint entre si — sem integridade referencial, sem lugar único pra pendurar nome/setor/CNPJ/tipo de ativo quando o domínio crescer (FIIs, ETFs, BDRs, cripto). GPT (ver histórico da sessão) argumentou que adiar isso fica mais caro quanto mais o domínio cresce — concordei e priorizei essa entidade antes do JWT.
**Bug real descoberto no levantamento pré-implementação**: `portfolio_items`/`stock_alerts` guardavam ticker **sem** sufixo (`PETR4`), `score_history` guardava **com** sufixo `.SA` do yfinance (`PETR4.SA`) — mesmo ativo, dois formatos, nunca colidiu porque não havia FK unindo as tabelas. `TickerNormalizer.canonical()` (strip `.SA` + uppercase) virou o único ponto de normalização — antes estava duplicado solto em pelo menos 4 arquivos (`StockAnalysisService`, `SectorClassifier`, `BacktestService`, controllers).
**Escopo deliberadamente mínimo**: `Stock` só tem `ticker`, sem colunas de nome/setor/CNPJ/tipo ainda — nada no código popula esses dados hoje, adicionar colunas vazias violaria "sem implementação pela metade". A entidade existe pra dar lugar pra isso crescer depois, não pra antecipar.
**Escopo deliberadamente excluído**: `StockAnalysisService`, `ComparisonService`, `BacktestService`, `SectorClassifier`, `PythonDataGateway`, embeddings pgvector — todos continuam com `ticker: String`. Só manipulam ticker em trânsito por requisição, nunca persistem via JPA; forçar `Stock` neles seria acoplamento sem ganho. `stock_embeddings` (metadado pgvector do LangChain4j) também fica de fora — mecanismo de vetor não suporta FK relacional.
**Design técnico**: `@ManyToOne(fetch = EAGER)` para `Stock` nas 3 entidades (não LAZY como `PortfolioItem.user`) — `Stock` é pequeno e o ticker é lido em quase todo call site; LAZY exigiria `@Transactional` em métodos que hoje não têm (`ScoreAlertService.getRecentAlerts`/`getAlertsByTicker`), risco real de `LazyInitializationException`. Entidades mantêm `getTicker(): String` delegando pra `stock.getTicker()` — nenhum DTO, controller ou o frontend precisou mudar. Repositórios usam propriedade aninhada (`findByStock_Ticker`) — Spring Data resolve `stock_id` via JOIN automaticamente.
**Migration** (`V2__introduce_stock_entity.sql`): cria `stock`, faz backfill via `UNION DISTINCT` normalizado das 3 tabelas, popula `stock_id` via `UPDATE...FROM` com o mesmo `REGEXP_REPLACE` de normalização, torna `stock_id NOT NULL`, remove a coluna `ticker` antiga. Cutover completo numa migration só — aceitável porque é ambiente pessoal/dev, sem usuários reais e sem coluna `ticker` sendo lida por nada fora do Java (nenhuma dependência externa/BI direto no banco).
**Verificado**: dados sintéticos inseridos com os 3 formatos (`PETR4`, `petr4`, `PETR4.SA`) pro mesmo ativo — migration convergiu todos pro mesmo `stock_id`, provando o fix. `mvn clean compile` limpo, app sobe contra Postgres real com `ddl-auto: validate` passando, suíte 14/14 verde.

## JWT TTL reduzido de 7 dias para 24h (resolvido em 2026-08-07)

**Decisão**: `JwtService.TTL_MS` de 7 dias para 24h — sem implementar refresh token ainda.
**Motivo**: token de 7 dias (`anti-patterns.md`) deixava um vazamento (XSS, log de proxy — token vai por query string no redirect OAuth2) explorável por até uma semana. Refresh token/revogação de verdade é mudança maior (endpoint novo, rotação, storage) fora do escopo deste item — 24h reduz a janela de exposição em 7x sem essa complexidade.
**Efeito colateral corrigido na mesma tarefa**: `authInterceptor` (frontend) não tratava 401 — com TTL de 7 dias isso quase nunca era percebido; com 24h, expiração vira evento comum. `authInterceptor` agora limpa o token e redireciona pra `/login` em qualquer 401, em vez de deixar a tela quebrada silenciosamente.
**Trade-off aceito**: sem refresh, usuário reloga a cada 24h (antes: a cada 7 dias) — UX pior, aceito conscientemente até refresh token entrar (item de roadmap separado).
**Ver também**: `anti-patterns.md` (seção Segurança).

## URLs configuráveis substituindo hardcode de localhost (resolvido em 2026-08-07)

**Decisão**: externalizar CORS, redirect pós-OAuth2 e a URL base da API do frontend, em vez de deixá-las fixas em `localhost:4200`/`localhost:8080`.
**Motivo original da dívida**: fase de protótipo, um único ambiente (dev local) — aceito conscientemente (ver `anti-patterns.md`, item resolvido).
**Como foi resolvido**:
- Backend: `SecurityConfig.corsConfigurationSource()` e `OAuth2SuccessHandler.onAuthenticationSuccess()` passaram a ler `app.cors.allowed-origins` (`CORS_ALLOWED_ORIGINS`, lista separada por vírgula) e `app.frontend.base-url` (`FRONTEND_BASE_URL`) via `@Value`, default `http://localhost:4200` — comportamento de dev inalterado.
- Frontend: `src/environments/environment.ts` (dev) e `environment.prod.ts` (prod) trocados por `fileReplacements` no `angular.json`. Prod usa caminhos **relativos** (`apiUrl: '/api'`, `authUrl: ''`) assumindo backend na mesma origem via reverse proxy — não fixa um domínio de produção em tempo de build, o que teria só trocado um hardcode por outro.
- `stock.service.ts`, `portfolio.service.ts`, `auth.service.ts`, `simulator.ts` — as 4 URLs absolutas trocadas por `environment.apiUrl`/`environment.authUrl`.
**Escopo deliberadamente excluído**: URLs de vendor fixo (Gemini, Groq, CVM, BCB, Google News RSS) continuam hardcoded nos scripts Python e no `EmbeddingStoreConfig` — são endpoints públicos que não variam por ambiente de deploy; externalizar geraria indireção sem ganho real.
**Verificado**: `mvn clean compile` limpo; `ng build --configuration production` gera bundle com `apiUrl:"/api"`, sem `localhost:8080` no output; suíte backend 13/14 (o teste que falha exige Postgres local rodando, não regressão desta mudança).

## Benchmarks setoriais dinâmicos com fallback estático

**Decisão**: calcular medianas reais de pares via CVM+market cap antes de recorrer a faixa hardcoded.
**Motivo**: sem benchmark explícito, Gemini e Groq inventam médias setoriais diferentes um do outro a partir de memória de treino — inconsistência entre modelos que o benchmark dinâmico elimina.

## Rótulos descritivos em vez de imperativos (ATRATIVO/NEUTRO/CAUTELA/DESFAVORÁVEL)

**Decisão**: nunca usar COMPRAR/VENDER/MANTER como rótulo de recomendação.
**Motivo**: Resolução CVM 20/2021 restringe recomendação de investimento a analistas credenciados — é uma restrição regulatória, não estética.
**Ver também**: `financial-rules.md`.

## Tratamento diferenciado para bancos/seguradoras

**Decisão**: suprimir `totalDebt`/`debtToEquity` no prompt quando a CVM não mostra conta de empréstimos/financiamentos no balanço de uma financeira.
**Motivo**: captação e depósitos bancários não são dívida corporativa; o yfinance trata isso como se fosse, distorcendo `gestaoRisco` para o setor inteiro (ex.: ITUB4 aparecia com "dívida" de R$ 1,15 tri).

## Auditoria de Spring Boot Skills externas: zero instalação (resolvido em 2026-08-08)

**Decisão**: auditadas 5 Skills de `github.com/rrezartprebreza/spring-boot-skills` (MIT, ativo) — `spring-data-jpa`, `flyway-migrations`, `spring-data-redis`, `oauth2-resource-server`, `transactional-patterns`. Nenhuma foi instalada. 4 conhecimentos pontuais extraídos pros docs; o resto foi descartado por já estar coberto, ser inaplicável na escala atual, ou conflitar com decisão já tomada.
**Motivo de não instalar nenhuma**: Skills genéricas ensinam o padrão médio de mercado, não o contexto específico do projeto — instalar a Skill inteira arrisca um agente aplicar uma regra genérica (ex.: "sempre LAZY") por cima de uma decisão já pesada e documentada (`Stock` EAGER, ver decisão acima), ou resolver o problema errado (Resource Server num projeto que é OAuth2 Client). O conhecimento interno em `docs/ai/*` já é mais específico que a Skill genérica na maioria dos pontos — confirmado por essa auditoria, não suposto.
**Extraído (com verificação contra o código real, não copiado cego da Skill)**:
- **Flyway**: gotcha `CREATE INDEX CONCURRENTLY` não roda dentro da transação padrão do Flyway (precisa `.sql.conf` com `executeInTransaction=false`) + estratégia gradual add→backfill→drop pra rename de coluna. Ver `playbooks/database-change.md`.
- **Transactional**: padrão `REQUIRES_NEW`/`@TransactionalEventListener(AFTER_COMMIT)` pra escrita auxiliar que não pode derrubar o fluxo principal — `AnalysisAuditService` já resolve isso via try/catch (efeito equivalente), documentado como alternativa se o padrão se repetir. `@Retryable`/`@EnableResilientMethods` (Spring Framework 7, substitui Spring Retry) registrado pra quando `@Version` aparecer em alguma entidade — hoje nenhuma tem. Ver `backend.md`.
- **Redis**: verificado que `RedisStockCache` já serializa certo com Jackson 3 (`ObjectMapper` manual, tipo explícito, sem depender de default typing) — o gotcha da Skill (`GenericJacksonJsonRedisSerializer` sem `enableDefaultTyping` vira `LinkedHashMap`) não se aplica ao padrão atual, mas fica documentado pra se um cache futuro precisar de tipo polimórfico. Ver `coding-standards.md`.
- **JPA**: confirmado que nenhuma entidade sobrescreve `equals`/`hashCode` (identidade padrão do `Object`, correto sem chave natural) e que `GenerationType.IDENTITY` (`Stock`/`ScoreHistoryEntity`/`AnalysisAudit`) desabilita batch insert silenciosamente — sem problema hoje (sem bulk insert em lugar nenhum), documentado pra quando aparecer. Ver `coding-standards.md`.
**Rejeitado explicitamente**:
- JPA "`FetchType.LAZY` sempre" — conflita com o EAGER deliberado de `Stock` (ver decisão acima); resto da Skill (projections, keyset pagination, `@Query`) não tem uso no projeto hoje (só derived queries).
- `oauth2-resource-server` inteira — mismatch de arquitetura, não sobreposição. Ver nota nova em `architecture.md` ("Autenticação: OAuth2 Client, não Resource Server") pra essa distinção nunca ser perdida de vista.
**Ver também**: `security-reviewer.md`, `backend-architect.md` (agentes já cobrem boa parte do que as Skills genéricas tentariam ensinar, com mais contexto do projeto).

## Knowledge Guardian obrigatório ao final de tarefa não-trivial (resolvido em 2026-08-08)

**Decisão**: rodar `knowledge-guardian` ao final de qualquer tarefa não-trivial deixa de ser opcional/experimental — vira parte do processo, igual `mvn clean compile` já é.
**Motivo**: na auditoria de Skills externas (ver decisão acima), a primeira rodada do agente achou 2 drifts documentais **pré-existentes**, sem relação com a tarefa em andamento — `backend-architect.md` ainda citava `ddl-auto: update` (obsoleto desde 2026-08-06) e `PROJECT_DOCUMENTATION.md` listava métodos de repositório de antes da migration `Stock` (obsoleto desde 2026-08-07). Nenhum dos dois tinha sido pego por revisão manual nas duas tarefas que os causaram — prova de que drift documental passa despercebido sem uma checagem dedicada, e que o agente entrega valor real, não só teórico.
**Consequência prática**: `CLAUDE.md` já lista `knowledge-guardian` como "sempre, no fim de qualquer tarefa não-trivial" — essa decisão só confirma que a regra escrita corresponde a um resultado medido, não é aspiracional.

## Unificação de threshold de recomendação em `PortfolioService.evaluate()` (resolvido em 2026-08-08)

**Decisão**: `evaluate()` passou a usar `analysis.recommendation()` (rótulo já calculado por `AnalysisParser.deriveRecommendation` dentro de `StockAnalysisService`) em vez de reimplementar seu próprio corte (`≥7.0`/`≥5.0`, sem CAUTELA). `frontend/src/app/pages/portfolio/portfolio.ts` ganhou a 4ª variante `CAUTELA`/`wait` (cor `var(--amber)`) em `evalClass`/`formatAction`/CSS, que faltava.
**Motivo**: achado incidental do Knowledge Guardian numa varredura de consistência (não relacionado à tarefa que rodava) — mesma nota/score podia sair com rótulo diferente na tela de análise vs tela de carteira (ex.: score 7.2 = NEUTRO na análise, ATRATIVO na carteira; score 5.5 = CAUTELA na análise, mas CAUTELA nem existia em `evaluate()`).
**Processo — 3 agentes em sequência, nenhuma decisão de negócio tomada sozinho**:
- `financial-analyst`: julgou ser duplicação acidental, não decisão de negócio — reforçado pelo achado de que `PortfolioService.getPortfolio()` já usava o rótulo oficial (`analysis.recommendation()`), só `evaluate()` divergia. Recomendou unificar.
- `backend-architect`: achou que `AnalysisResponse` já trazia o rótulo pronto — zero dependência nova necessária, fix de 1 linha. Identificou o gap real de frontend (CAUTELA faltando em `portfolio.ts`) que precisava entrar na mesma tarefa pra não virar regressão visual (card sem cor/borda, rótulo cru "CAUTELA" sem formatação).
- Implementação: `PortfolioService.java` linha 106 (`String action = analysis.recommendation();`), `portfolio.ts` (`evalClass`/`formatAction`/CSS `.wait`).
**Verificado**: `mvn clean compile` limpo, `mvn test` 13/14 (única falha é `contextLoads` exigindo Postgres local rodando — infra, não regressão), `ng build --configuration production` limpo.
**Ver também**: `anti-patterns.md` (entrada riscada), `financial-rules.md`, `docs/PROJECT_DOCUMENTATION.md` seção 7.

## Ta4j homologado, não adotado (resolvido em 2026-08-08)

**Decisão**: `ta4j-core` (MIT, real, versão 0.18 confirmada no Maven Central, 2025-01-13) foi tecnicamente aprovado por spike isolado, mas **não entra no projeto agora**.
**Spike**: série sintética reproduzível de 10.000 candles (seed=42 — determinístico, não dado de mercado real; propósito era comparar implementações, não analisar um ticker), com segmentos deliberados de caso extremo (flat/zero volatilidade, alta monotônica, baixa monotônica, alta volatilidade, gap de -30% num candle). RSI(14) via `ta4j-core` `RSIIndicator` comparado ponto a ponto contra `fetch_technical_indicators.py::_rsi` (Wilder via `ewm(com=period-1)`) — **diferença máxima e média: 0.000000 em todos os 8 segmentos**, incluindo os extremos. Única diferença real: Ta4j não tem período de aquecimento (`min_periods`) — calcula desde o índice 0 com janela parcial, pandas devolve `NaN` até o 14º ponto. Irrelevante pro uso atual (`_rsi()` só lê o último valor de uma série de ~126 candles, sempre madura); importaria só se um dia quisessem série histórica de RSI via Ta4j.
**Motivo de não adotar apesar de aprovado**: `fetch_technical_indicators.py` calcula RSI **junto** com MACD/Bollinger/SMA/volume/sinal, numa única chamada ao sidecar Python. Trocar só o RSI por Ta4j não elimina essa chamada nem o Python — os outros 5 indicadores continuariam lá, e o projeto passaria a ter **duas fontes de verdade** pra indicador técnico (Java pro RSI, Python pro resto). Isso piora a arquitetura em vez de simplificar — ganho real só existiria se a decisão fosse migrar o pipeline de indicadores técnicos **inteiro** pra Java, questão arquitetural maior que "o RSI bate", não decidida aqui.
**Reavaliar quando**: houver interesse real em tirar a responsabilidade de indicadores técnicos do sidecar Python. Nesse caso, rodar um segundo experimento — pipeline completo (RSI+MACD+Bollinger+SMA+volume+sinal) em Python vs em Ta4j, medindo tempo total, não só RSI isolado; pergunta diferente da respondida aqui.
**Ver também**: spike não deixou rastro no repositório (arquivos ficaram fora do projeto, `pom.xml` do backend intocado) — reproduzível a partir desta entrada se precisar rodar de novo.

## Gate de plausibilidade pós-score (resolvido em 2026-08-08)

**Decisão**: `ScorePlausibilityGate.check(StockAnalysis, StockFundamentals, ScoreConfidence, SectorType)` — função pura, `com.stockai.analysis`, roda em `StockAnalysisService.doAnalyze` entre o parse da resposta do LLM e `saveScore`. Produz `List<PlausibilitySignal>` (enum, 5 valores) — só sinaliza, nunca corrige/bloqueia score, recomendação ou metodologia. Persistido em `analysis_audit.plausibility_signals` (`V4__add_plausibility_signals_to_analysis_audit.sql`) e logado em `WARN` quando não-vazio.
**Motivo**: item da auditoria de tooling financeiro (2026-08-08) — as regras de coerência entre dimensões já existiam como texto na rubrica do prompt (`prompts.md`), mas nunca eram verificadas depois que o LLM respondia; se o modelo ignorasse a própria rubrica, nada no pipeline percebia.
**Processo — 2 agentes validaram antes de qualquer código, nenhuma regra decidida sozinha**:
- `financial-analyst`: aprovou 3 das 4 regras propostas sem ressalva; rejeitou a versão original da regra de P/L negativo (disparava falso positivo em toda empresa com write-off pontual e P/VPA saudável — caso **esperado** pela própria rubrica, não inconsistência) e propôs o ajuste: só sinalizar quando não há P/VPA disponível pra justificar a substituição. Sugeriu alinhar o threshold de "score alto + confiança baixa" ao piso real de `ATRATIVO` (`>7.5`, `financial-rules.md`) em vez de um corte arbitrário (`8`) — adotado. Encontrou uma 5ª regra mais forte que as 4 propostas: o hard cap de `VAREJO` já escrito no prompt ("sem lucro consistente, retornoAcionista não pode passar de 4") — trava absoluta, violá-la é contradição mais direta que qualquer uma das outras 4. Confirmou que nenhuma das 5 regras precisa de exceção para `FINANCEIRO` (nenhuma toca `debtToEquity`/`totalDebt` diretamente).
- `backend-architect`: achou que `ScoreConfidence` já calculado em `doAnalyze` não dependia de nada posterior a `saveScore` (posição anterior era ordenação incidental) — moveu o cálculo pra antes, resolvendo a dependência da regra 4 sem reordenar nada que dependesse de fato de `saveScore`. Decidiu: enum (não record) pra `PlausibilitySignal` (checks fixos, não dado livre), persistência em `AnalysisAudit` por precedente direto (mesmo raciocínio write-heavy/read-rare já usado ali), coluna `TEXT` única em vez de uma coluna por regra (conjunto de sinais é aberto, cresce sem migration nova). Sinalizou risco real de NPE se `BigDecimal` (`priceToEarnings`/`dividendYield`) não fosse null-checado antes de `.signum()` — violaria a invariante de nunca interromper uma análise já paga.
**"Lucro inconsistente" (regra de `VAREJO`)**: não era definido numericamente em lugar nenhum do sistema — proxy determinístico escolhido: algum dos últimos 4 trimestres (`quarterlyResults`) com lucro ≤0, ou `earningsGrowth` negativo. Dado insuficiente (sem trimestres e sem `earningsGrowth`) nunca sinaliza — o gate não especula sobre lucro que não viu.
**Verificado**: `mvn clean compile` limpo, `ScorePlausibilityGateTest` 14/14 (casos positivos e negativos de cada regra, incluindo acúmulo de múltiplos sinais na mesma análise e não-disparo por falta de dado), suíte completa 27/28 (única falha é `contextLoads`, infra — `GOOGLE_CLIENT_ID` ausente no shell local, não regressão).
**Ver também**: `backend.md`, `financial-rules.md`, `prompts.md`.

## Single-flight por ticker (lock em memória)

**Decisão**: `ConcurrentHashMap<String, ReentrantLock>` por ticker em `StockAnalysisService`.
**Motivo**: evitar que requisições simultâneas do mesmo ticker disparem múltiplas chamadas de LLM (custo real por chamada) — a segunda requisição espera e reusa o resultado da primeira.
**Limite conhecido**: lock em memória de processo único — não coordena entre múltiplas instâncias do backend se o sistema escalar horizontalmente. Reavaliar (lock distribuído via Redis, por exemplo) antes de rodar mais de uma instância do backend.
