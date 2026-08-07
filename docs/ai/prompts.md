# Prompts

Construído inline em Java (`StockAnalysisService.buildPrompt`) — **sem arquivos de template externos** (`.txt`/`.st`/`.md`). Se for extrair para template, avaliar impacto em `PROMPT_VERSION` e em testes.

`PROMPT_VERSION` atual: **`v2.4`**. Ver `ai.md` para a regra de quando incrementar.

## Estrutura do prompt, em ordem

1. Instrução de formato: só JSON, sem markdown, sem ```json.
2. Data da análise + flag de ano eleitoral: `(anoAtual - 2026) % 4 == 0`. **Constante mágica âncorada em 2026** — revisar se o projeto sobreviver a vários ciclos eleitorais.
3. Dados fundamentalistas (com omissão de dívida/patrimônio para bancos — ver `financial-rules.md`).
4. Contexto macroeconômico (Selic, IPCA, câmbio, Focus, Brent/WTI).
5. Indicadores técnicos (RSI, MACD, Bollinger, sinal composto).
6. Sentimento das manchetes — desde `v2.4`, o caveat é dinâmico por fonte (`buildSentimentText`): FinBERT-PT-BR real (sinal confiável) quando `HUGGINGFACE_TOKEN` está configurado e a chamada funciona, senão "análise lexical de palavras-chave — sinal de confiança limitada" (fallback automático), senão "sem notícias suficientes" quando não há manchete nenhuma. Ver `decisions.md`.
7. Fundamentos históricos (contexto RAG, ver `rag.md`).
8. Contexto e instruções setoriais (`SectorPromptConfig`).
9. Benchmarks do setor (dinâmico via CVM+market cap, ou faixa estática de fallback).
10. Rubrica de pontuação (âncoras objetivas por dimensão).
11. Bloco de calibração.
12. Schema JSON de saída, com campo `analise` (raciocínio livre) **obrigatoriamente primeiro** — chain-of-thought induzido antes dos scores.

## Rubrica de pontuação (texto literal, não parafrasear ao editar)

> - Fundamentos — 8-10: ROE acima da faixa setorial, margens estáveis ou crescentes, lucro crescente nos trimestres; 5-7: rentabilidade dentro da faixa setorial, resultados estáveis; 2-4: margens em queda ou lucro irregular; 0-1: prejuízo recorrente.
> - Valuation — 8-10: múltiplos claramente abaixo da faixa setorial com FCL positivo; 5-7: múltiplos dentro da faixa; 2-4: múltiplos acima da faixa sem crescimento que justifique; 0-1: múltiplos extremos ou P/L negativo sem perspectiva. Use P/VPA quando o P/L estiver distorcido por resultado negativo.
> - Regime/Momentum — use technicalSignal como base: STRONG_BUY≈8, BUY≈6.5, NEUTRAL≈5, SELL≈3.5, STRONG_SELL≈2; ajuste ±1 pelo contexto fundamentalista. Momentum ruim com fundamentos fortes não deve ficar abaixo de 3.5.
> - Sentimento Institucional — use o score lexical como base, ajustando por beta e amplitude 52 semanas. Com poucas notícias ou confiança baixa, mantenha próximo de 5 (neutro) — não invente sinal.
> - Retorno ao Acionista — 8-10: yield acima da faixa setorial com histórico consistente e FCL que o sustenta; 5-7: yield dentro da faixa; 0-4: sem dividendos e sem recompras. Se dividendYield = 0 mas o histórico mostra pagamentos reais, use o histórico — yield zero pode ser falha de coleta.
> - Gestão de Risco — 8-10: caixa líquido ou dívida/patrimônio baixo para o setor, exposição cambial administrada; 5-7: alavancagem típica do setor; 0-4: alavancagem alta E vulnerabilidade direta à Selic (varejo de crédito, imobiliário alavancado). A Selic afeta todas as empresas — penalize além de 1 ponto apenas as particularmente vulneráveis.

## Bloco de calibração

> - Score acima de 7.0: empresas com múltiplas dimensões fortes segundo a rubrica.
> - Score abaixo de 3.0: problemas graves e estruturais.
> - Use a escala inteira — empresas diferentes devem receber scores claramente diferentes.
> - Não deflacione scores por fatores macro que afetam o mercado inteiro.

## Schema JSON de saída exigido

```json
{
  "analise": "<raciocínio em 3-4 frases: pontos fortes, pontos fracos e o que pesa em cada dimensão>",
  "fundamentos": {"score": "<0-10>", "explicacao": "<1 frase citando o dado que sustenta o score>"},
  "valuation": {"score": "<0-10>", "explicacao": "..."},
  "regimeMomentum": {"score": "<0-10>", "explicacao": "..."},
  "sentimentoInstitucional": {"score": "<0-10>", "explicacao": "..."},
  "retornoAcionista": {"score": "<0-10>", "explicacao": "..."},
  "gestaoRisco": {"score": "<0-10>", "explicacao": "..."},
  "resumo": "<síntese em 2 frases>",
  "simpleSummary": "<explique em 2 frases simples como se falasse com alguém que nunca investiu, sem jargões financeiros>"
}
```

`analise` induz raciocínio antes do score. Desde 2026-08-07, é extraído por `AnalysisParser` (`ParsedAnalysis.reasoning`) e persistido em `analysis_audit` — mas continua **fora** de `AnalysisResponse`/API/frontend (só auditoria). `resumo` e `simpleSummary` são extraídos e persistidos (`resumo` também vai pra `analysis_audit`; `simpleSummary` só pra `AnalysisResponse`, não persistido).

## Instruções setoriais (`SectorPromptConfig`, excerto — 11 setores no total)

- **FINANCEIRO**: "Selic alta beneficia margens de intermediação... Para bancos e seguradoras, IGNORE dívida total e dívida/patrimônio: alavancagem é estrutural do negócio... NÃO penalize gestaoRisco por alavancagem bruta."
- **VAREJO**: "Selic alta é o maior inimigo... Sem lucro consistente, retornoAcionista não pode passar de 4."
- **ENERGIA**: "Brent e câmbio são determinantes. Em ano eleitoral, risco político deve penalizar gestaoRisco em 1-2 pontos."
- **IMOBILIARIO**: "FFO e dividend yield são as métricas centrais para FIIs."
- **OUTROS** (fallback): genérico, "analise com base exclusivamente nos fundamentos disponíveis".

Ao editar qualquer instrução setorial, checar se isso muda o comportamento esperado documentado em `financial-rules.md` — elas estão acopladas.

## Ao mudar o prompt

1. Incrementar `PROMPT_VERSION`.
2. Rodar `AnalysisParserTest` (garante que o schema de saída ainda parseia).
3. Atualizar este arquivo com o novo texto literal.
4. Considerar se scores históricos ficam incomparáveis (normalmente ficam — é esperado).
