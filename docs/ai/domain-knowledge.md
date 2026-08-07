# Domain Knowledge — manual do analista financeiro

Conhecimento de domínio financeiro desacoplado da implementação. Objetivo: qualquer pessoa (ou modelo) mexendo no prompt, na rubrica, no `SectorPromptConfig` ou em coleta de dado novo entende **o que o indicador significa e onde ele quebra**, não só onde ele é lido no código.

Cada seção referencia qual das 6 dimensões do score ela afeta (`Fundamentos`, `Valuation`, `Regime/Momentum`, `Sentimento Institucional`, `Retorno ao Acionista`, `Gestão de Risco` — ver `financial-rules.md`) e onde no sistema o dado é hoje coletado/usado.

---

## Múltiplos de valuation

### P/L (Preço/Lucro)
Preço da ação dividido pelo lucro por ação. Quanto menor, "mais barata" a ação em relação ao lucro atual — mas só faz sentido comparado à faixa do próprio setor (`SectorBenchmarks`), nunca em isolado. Um P/L de 15 é caro para banco (faixa típica 6-10) e barato para varejo de crescimento.

**Armadilha**: P/L fica sem sentido (ou negativo) quando a empresa tem lucro distorcido por evento não-recorrente (venda de ativo, write-off, resultado financeiro atípico). Nesse caso o prompt já instrui usar **P/VPA no lugar do P/L** (ver `prompts.md`, seção Valuation da rubrica) — não insistir em P/L negativo como sinal de "ação parece cara/barata".

**Usado em**: dimensão `Valuation`. Coletado em `fetch_fundamentals.py`, recalculado com market cap do yfinance sobre lucro CVM.

### P/VPA (Preço/Valor Patrimonial por Ação)
Preço dividido pelo patrimônio líquido por ação. Mais estável que P/L porque não depende do lucro do período — é a métrica de referência para bancos e para qualquer empresa com lucro volátil/negativo.

**Armadilha**: patrimônio líquido inflado por reavaliação de ativos (comum em imobiliário/holdings) faz o P/VPA parecer artificialmente baixo. Não é um problema tratado hoje pelo sistema — sinalizar se aparecer em análise de holding/imobiliária.

**Usado em**: `Valuation`, especialmente para `FINANCEIRO` e quando P/L não é confiável.

### EV/EBITDA
Enterprise Value (valor de mercado + dívida líquida) dividido pelo EBITDA. Serve para comparar empresas com estruturas de capital (alavancagem) diferentes — o P/L não faz isso, porque ignora a dívida.

**Status no sistema**: **não é coletado hoje** (`fetch_fundamentals.py` não traz EBITDA nem EV). O prompt setorial de `LOGISTICA` pede EBITDA e o dado nunca chega — bug conhecido, roadmap P1-7. Ao implementar, atenção: EV/EBITDA só é comparável dentro do mesmo setor de capital intensivo (logística, energia, siderurgia); não faz sentido para bancos (que não têm "dívida operacional" no sentido usado na fórmula — ver seção Bancos abaixo) nem para empresas asset-light (tech, serviços) onde EBITDA já é quase o lucro operacional.

**Quando não usar**: setor financeiro (dívida não é comparável, ver `financial-rules.md`); empresa com EBITDA negativo ou marginal (múltiplo perde significado); comparação entre setores diferentes.

---

## Rentabilidade

### ROE (Retorno sobre Patrimônio Líquido)
Lucro líquido dividido pelo patrimônio líquido. É a métrica central da dimensão `Fundamentos` na rubrica atual (ver `prompts.md`).

**Quando ROE alto é sinal de risco, não de qualidade**: ROE = margem × giro de ativos × alavancagem (decomposição DuPont). Um ROE de 25% pode vir de uma empresa genuinamente eficiente **ou** de uma empresa com patrimônio líquido pequeno/alavancagem alta — a mesma fórmula não distingue as duas. Antes de tratar ROE alto como positivo, cruzar com `Gestão de Risco` (dívida/patrimônio): ROE alto **e** alavancagem alta é bandeira amarela, não dupla confirmação positiva. O sistema hoje não faz essa decomposição automaticamente — é um julgamento que cabe à instrução de prompt/rubrica, não a um cálculo isolado.

**Caso especial bancos**: ROE de banco é estruturalmente mais alto que de empresa não-financeira (patrimônio é uma fração pequena dos ativos totais por desenho regulatório) — comparar ROE de banco com ROE de indústria é comparação inválida. Sempre comparar dentro do próprio setor (`SectorBenchmarks`).

### ROA (Retorno sobre Ativos)
Lucro líquido dividido pelo ativo total. Menos sensível a alavancagem que o ROE — é o complemento que mostra se o retorno alto do ROE vem de eficiência operacional ou de dívida. Útil especificamente para checar a armadilha do ROE descrita acima.

### Margens (bruta, EBITDA, líquida)
Indicam eficiência operacional e poder de precificação. Margem em queda trimestre a trimestre é sinal mais forte de deterioração do que margem baixa em nível absoluto (nível varia muito por setor — margem líquida de 3% é normal em varejo, péssima em software).

---

## Setores especiais

### Bancos e seguradoras (`SectorType.FINANCEIRO`)
Ver `financial-rules.md` para a regra de supressão de dívida já implementada. Pontos adicionais de interpretação:
- Margem de intermediação (spread entre captação e crédito) não é comparável a margem operacional de empresa não-financeira — não usar a mesma âncora da rubrica de `Fundamentos` sem ajuste.
- Selic alta **beneficia** bancos (spread maior), ao contrário de setores endividados/varejo de crédito — ver seção Selic abaixo.
- Inadimplência (não coletada estruturalmente hoje — só aparece via sentimento de notícias) é o principal risco operacional de um banco; ausência desse dado é uma limitação real da dimensão `Gestão de Risco` para o setor.
- ROE de banco é estruturalmente alto — ver seção ROE acima. Instrução atual do prompt: avaliar por "estabilidade do ROE, P/VPA, beta e sinais de inadimplência nas notícias" em vez de alavancagem bruta.

### FIIs (Fundos de Investimento Imobiliário) — `SectorType.IMOBILIARIO`
FFO (Funds From Operations — lucro operacional ajustado por itens não-caixa, como depreciação de imóvel) é a métrica central, **não** lucro líquido contábil puro. Dividend yield de FII costuma ser mais alto e mais estável que de ação porque a legislação obriga distribuição mínima de 95% do resultado — um yield "normal" de FII (8-12%) seria excepcional para uma ação operacional e não deve ser comparado na mesma escala.

**Status no sistema**: FFO não é coletado (fundamentos vêm de CVM/yfinance pensados para ações, não para FIIs). Se o sistema for estender cobertura a FIIs de fato, isso é gap de dado, não só de prompt.

### Empresas cíclicas (mineração, siderurgia, papel & celulose, commodities em geral)
Lucro e margem variam fortemente com o preço da commodity subjacente (minério, celulose, aço) e com câmbio (a maioria exporta e fatura em USD). P/L baixo em pico de ciclo de commodity é armadilha clássica — o lucro do topo do ciclo não é sustentável, e o P/L parece artificialmente barato exatamente no pior momento para comprar. P/L alto no fundo do ciclo tem o problema espelhado.

**Como mitigar**: preferir P/VPA ou métricas normalizadas de ciclo a P/L simples para esse grupo; considerar a fase do ciclo de commodity (não modelada hoje no sistema) antes de confiar cegamente no múltiplo corrente.

### Varejo (`SectorType.VAREJO`)
Selic alta é o principal risco — encarece o crédito ao consumidor (reduz vendas) e o próprio custo de capital de giro da empresa. Regra de prompt já em produção: sem lucro consistente, `Retorno ao Acionista` não passa de 4 (ver `prompts.md`).

---

## Retorno ao acionista

### Dividend Yield
Dividendos (+ JCP) pagos nos últimos 12 meses dividido pelo preço atual. Comparar sempre contra a faixa setorial — FII/banco têm yield estruturalmente maior que tech/varejo de crescimento.

**Armadilha operacional já tratada no sistema**: se `dividendYield` vier 0 mas o histórico de dividendos (`DividendEntry`) mostra pagamentos reais, o prompt instrui usar o histórico — yield zero é mais provável falha de coleta do que corte real de dividendo (ver `prompts.md`, rubrica de `Retorno ao Acionista`). Não tratar yield 0 como fato definitivo sem checar o histórico primeiro.

**Armadilha de yield alto**: yield muito acima da faixa setorial pode ser sinal de queda recente do preço (yield = dividendo/preço; preço caindo infla o yield artificialmente) e não de política de distribuição generosa — sempre olhar se o yield alto acompanha preço em queda antes de tratar como positivo.

### Payout Ratio
Percentual do lucro distribuído como dividendo. Payout muito alto (>90-100%) sustentado por vários anos é bandeira — sinaliza que a empresa não está reinvestindo o suficiente em crescimento, ou que o lucro contábil não reflete o fluxo de caixa real disponível para distribuir (possível em empresas com resultado inflado por itens não-caixa).

**Status no sistema**: não coletado hoje (roadmap P1-7, junto com EV/EBITDA e margem EBITDA).

---

## Momentum e técnico

### Beta
Sensibilidade do retorno da ação ao retorno do mercado (IBOV). Beta > 1 amplifica movimento do mercado (para cima e para baixo); beta < 1 amortece. Usado hoje para ajustar a dimensão `Sentimento Institucional` (ponderar sentimento pela volatilidade típica do papel) e mencionado na rubrica de `Gestão de Risco`.

### Quando ignorar o sinal técnico (`technicalSignal`)
A rubrica hoje usa `technicalSignal` (STRONG_BUY/BUY/NEUTRAL/SELL/STRONG_SELL) como âncora primária de `Regime/Momentum`, com ajuste de ±1 pelo fundamentalista, e trava explícita: "momentum ruim com fundamentos fortes não deve ficar abaixo de 3.5" (ver `prompts.md`). Ou seja, o próprio prompt já reconhece o caso central onde o técnico deveria pesar menos: **queda de preço por sentimento de mercado geral (setor inteiro caindo, mercado em correção) não é o mesmo que deterioração da empresa específica**. Isso é agravado pela ausência de benchmark relativo a IBOV (roadmap P1-8) — hoje o sistema não distingue "esta ação caiu" de "o mercado todo caiu", então o ajuste fica só na trava textual da rubrica, sem dado que a sustente.

### Sinal técnico em empresa de baixa liquidez
RSI/MACD/Bollinger (calculados em `fetch_technical_indicators.py` sobre 6 meses de preço) ficam ruidosos em papéis pouco negociados — poucos negócios por dia distorcem médias móveis e osciladores. Não é tratado hoje; sinal técnico de ticker de baixa liquidez merece desconto de confiança maior do que a rubrica aplica atualmente.

---

## Macroeconomia

Todos os dados desta seção vêm de `fetch_macro.py` (BCB SGS + Focus/Olinda + yfinance para Brent/WTI). Nenhum é específico de uma ação — afetam o mercado inteiro, e a rubrica de calibração já instrui explicitamente "não deflacionar scores por fatores macro que afetam o mercado inteiro" (ver `prompts.md`). Ou seja: usar macro para **contextualizar diferença entre empresas do mesmo cenário**, não para punir todas as análises igualmente quando o cenário piora.

### Selic
Taxa básica de juros. Selic alta: beneficia bancos (spread), pressiona varejo/crédito ao consumidor, eleva o custo de capital de empresas alavancadas (relevante para `Gestão de Risco` — "a Selic afeta todas as empresas — penalize além de 1 ponto apenas as particularmente vulneráveis", regra já na rubrica), e aumenta a atratividade de renda fixa frente a dividendos (pressiona yield exigido de ações pagadoras de dividendo).

### IPCA (inflação)
Erosão de margem em setores que não conseguem repassar preço (geralmente varejo de bens não essenciais); setores com poder de repasse (concessões com reajuste indexado, utilities) são menos afetados. Inflação alta e persistente também pressiona a Selic para cima (mandato do Banco Central) — os dois indicadores não são independentes.

### Câmbio (USD/BRL)
Câmbio depreciado (real mais fraco): beneficia exportadoras que faturam em USD com custo em BRL (mineração, papel & celulose, parte do agro); prejudica importadoras e empresas com dívida em moeda estrangeira sem hedge (relevante para `Gestão de Risco` — "exposição cambial administrada" é um dos critérios de nota alta na rubrica).

### Expectativas Focus
Medianas de mercado para Selic/IPCA do ano corrente e do próximo, coletadas via BCB Olinda. Servem para avaliar se a política monetária atual (Selic spot) já está precificada ou se o mercado espera mudança — Selic spot sozinha, sem Focus, não diz se juro vai subir ou cair.

### Curva DI futuro
**Não coletado hoje** — só Selic spot + Focus (roadmap P3-17). A curva DI (estrutura a termo de juros) é o proxy de custo de capital mais correto para desconto de fluxo de caixa futuro; Selic spot é só o ponto presente da curva, insuficiente para avaliar valuation de empresas de crescimento (fluxo de caixa concentrado no futuro) com precisão.

### Brent/WTI (petróleo)
Coletado via futuros yfinance (`BZ=F`, `CL=F`). Determinante direto para `SectorType.ENERGIA` (petroleiras, distribuidoras de combustível) e indireto para custo de transporte/logística em geral.

### Ano eleitoral
Flag calculada no prompt (`(anoAtual - 2026) % 4 == 0` — ver ressalva de constante mágica em `prompts.md`). Instrução atual: em ano eleitoral, risco político deve penalizar `Gestão de Risco` em 1-2 pontos, especialmente para `ENERGIA` (setor historicamente sensível a política de preços de combustível/estatais). Não é uma regra universal para todo setor — aplicar com critério setorial, não como desconto genérico.

### Juros americanos (Fed Funds) e recessão global
**Não coletado hoje.** Juro americano alto tipicamente: fortalece o dólar (pressiona câmbio BRL), reduz apetite por risco emergente (pressiona fluxo estrangeiro para B3 — dimensão `Sentimento Institucional`, hoje sem dado real de fluxo estrangeiro, ver roadmap P2-14), e eleva custo de capital global. Sinal de recessão nos EUA/globalmente tende a antecipar queda de commodities (afeta cíclicas exportadoras) antes de afetar a economia doméstica diretamente. Gap de dado — mencionar se for relevante à análise textual, sem inventar número.

---

## Regra geral de uso deste documento

Ao adicionar um indicador novo ao sistema (fundamentos, macro, técnico), documentar aqui: o que significa, quando é enganoso, e para qual dimensão do score ele deveria pesar — antes de simplesmente jogar o número cru no prompt. Um dado sem contexto de interpretação no prompt é a razão pela qual o LLM "inventa" comparação (ver motivação de `SectorBenchmarks` em `decisions.md`) — o mesmo risco vale para qualquer indicador novo sem uma seção equivalente a estas.
