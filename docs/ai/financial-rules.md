# Regras financeiras e de domínio

Estas regras existem por razão regulatória ou de qualidade de análise. Não alterar sem entender a motivação abaixo.

## Score geral nunca vem do LLM

`AnalysisParser.computeScoreGeral` recalcula `scoreGeral` em Java como **média aritmética das 6 dimensões**, arredondada a 1 casa decimal. O valor que o modelo eventualmente calcule sozinho é descartado. Motivo documentado no código: "LLMs erram aritmética e o valor retornado pelo modelo é descartado."

Isso vale para qualquer nova dimensão ou fórmula derivada que se adicionar — cálculo determinístico sempre em Java, nunca confiar em aritmética textual do modelo.

## Dimensão ausente é erro, não zero

Se o JSON do LLM não trouxer uma das 6 dimensões, `AnalysisParser` lança `IllegalStateException`, não assume 0.0. Um 0 falso derrubaria o `scoreGeral` e dispararia um alerta de queda de score por falha de parsing, não por mudança real de fundamento. Nunca "consertar" um erro de parsing preenchendo com valor default silencioso.

## Clamp de score

Toda dimensão é limitada a `[0, 10]` (`Math.max(0, Math.min(10, score))`) antes de entrar no `DimensionScore`. Fora de faixa é clampado, não rejeitado.

## Thresholds de recomendação (`AnalysisParser.deriveRecommendation`)

| Score | Rótulo |
|---|---|
| > 7.5 | ATRATIVO |
| 6.0 – 7.5 | NEUTRO |
| 4.5 – 5.9 | CAUTELA |
| < 4.5 | DESFAVORÁVEL |

## Linguagem CVM-compliant — não usar verbo imperativo

Resolução CVM 20/2021 restringe recomendação de investimento (linguagem tipo COMPRAR/VENDER) a analistas credenciados. O sistema usa rótulos **descritivos**: `ATRATIVO`/`NEUTRO`/`CAUTELA`/`DESFAVORÁVEL`. **Nunca reintroduzir COMPRAR/VENDER/MANTER como rótulo de recomendação em nenhuma camada** (backend, frontend, prompt) — isso quebra conformidade regulatória, não é só estilo. Cores fixas associadas: `ATRATIVO=#00d4aa`, `NEUTRO=#3b82f6`, `CAUTELA=#f59e0b`, `DESFAVORÁVEL=#ef4444`.

Elegibilidade de carteira/alocação usa **score ≥ 6.0** diretamente (piso do NEUTRO), não comparação de string de rótulo — isso torna a lógica imune a rótulos antigos ainda presentes em cache Redis (TTL 30 min).

## Bancos e financeiras — não penalizar alavancagem estrutural

Para `SectorType.FINANCEIRO`, quando o balanço CVM não tem conta de "Empréstimos e Financiamentos" (bancos, seguradoras), `totalDebt`/`debtToEquity` são **suprimidos** no dado enviado ao prompt — depósitos e captação não são dívida corporativa, e o yfinance trata isso incorretamente como dívida (ex.: ITUB4 aparecia com "dívida" de R$ 1,15 tri). O prompt instrui explicitamente não penalizar `gestaoRisco` por essa alavancagem bruta e avaliar por ROE/P-VPA/inadimplência em vez disso.

Empresas com dívida real (debêntures — ex.: B3SA3) mantêm a métrica normalmente; a supressão é condicional à ausência da conta contábil específica, não a todo o setor.

## Benchmarks setoriais — sempre dinâmicos quando possível

Sem benchmark explícito, os dois LLMs "inventam" médias setoriais de memória — e inventam valores **diferentes** entre si. `SectorBenchmarks` calcula medianas reais de pares líquidos (P/L, P/VPA, DY, ROE, margem líquida, dívida/PL) via CVM + market cap, exige ≥3 amostras válidas por métrica (`MIN_PEERS = 3`), cacheia 24h no Redis. Só cai para faixa estática hardcoded se o dinâmico falhar ou não tiver amostras suficientes. Não remover essa checagem de amostra mínima — é o que evita reportar uma "mediana" de 1 ou 2 pares como se fosse confiável.

## Fundamentos: CVM é fonte primária, yfinance é fallback

Múltiplos do yfinance para B3 são descritos no código como "frequentemente errados ou defasados". ROE, ROA, margens, dívida/PL, receita e crescimentos vêm da CVM quando disponível; P/L, P/VPA e DY são recalculados usando o market cap do yfinance sobre os dados contábeis da CVM. Campo ausente na CVM mantém o valor do yfinance. Ticker fora do cadastro CVM (ETFs, BDRs) cai 100% para yfinance. A proveniência (`fundamentalsSource`, `statementDate`) é exposta e entra no prompt — não omitir isso ao adicionar novo dado fundamentalista.

## Sentimento é sinal fraco, não é fato

O prompt rotula a seção de sentimento como "análise lexical de palavras-chave — sinal de confiança limitada" e instrui manter a dimensão próxima de 5 (neutro) quando há poucas notícias ou confiança baixa. Não tratar o score de sentimento como um dado forte em nenhuma lógica nova.
