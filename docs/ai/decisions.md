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

## Single-flight por ticker (lock em memória)

**Decisão**: `ConcurrentHashMap<String, ReentrantLock>` por ticker em `StockAnalysisService`.
**Motivo**: evitar que requisições simultâneas do mesmo ticker disparem múltiplas chamadas de LLM (custo real por chamada) — a segunda requisição espera e reusa o resultado da primeira.
**Limite conhecido**: lock em memória de processo único — não coordena entre múltiplas instâncias do backend se o sistema escalar horizontalmente. Reavaliar (lock distribuído via Redis, por exemplo) antes de rodar mais de uma instância do backend.
