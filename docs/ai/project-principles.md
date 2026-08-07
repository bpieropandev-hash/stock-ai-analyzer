# Princípios do projeto

## Objetivo principal

Este sistema existe para fornecer análises financeiras **consistentes, explicáveis e reproduzíveis** de ações da B3.

## Os 6 pilares

Toda alteração deve melhorar ao menos um destes pilares. Nunca sacrificar um pilar sem justificar explicitamente ao usuário antes de implementar.

1. **Precisão** — o dado e o score refletem a realidade financeira da empresa, não uma aproximação conveniente.
2. **Transparência** — proveniência do dado (`fundamentalsSource`, `statementDate`), `modelUsed`, `promptVersion` sempre expostos; explicação textual acompanha todo score.
3. **Performance** — resposta rápida sem custo desnecessário de LLM/dado externo (cache, single-flight, sidecar persistente).
4. **Escalabilidade** — decisão de hoje não pode travar o crescimento de amanhã sem que isso seja um trade-off consciente e documentado (ex.: lock em memória por ticker, ver `decisions.md`).
5. **Confiabilidade** — degradação graciosa sempre que possível (fallback de LLM, fallback de fonte de dado, fallback de benchmark); falha silenciosa nunca — erro visível é melhor que dado errado disfarçado de certo.
6. **Auditabilidade** — qualquer score deve poder ser explicado e, idealmente, reconstruído (hoje limitado pela ausência de tabela de auditoria completa — roadmap P2-10).

## Como isso muda decisão de implementação

- Uma feature que aumenta velocidade mas reduz precisão do dado (ex.: usar só yfinance e pular CVM) sacrifica Precisão por Performance — não fazer sem confirmar com o usuário.
- Um cálculo de score que "parece certo" mas não pode ser justificado por regra explícita da rubrica sacrifica Transparência — preferir sempre uma âncora objetiva (ver `financial-rules.md`, `prompts.md`) a um ajuste implícito.
- Uma otimização de custo de LLM que reduz a qualidade do prompt (menos contexto, rubrica mais vaga) sacrifica Precisão por Performance — avaliar o trade-off explicitamente, não cortar contexto silenciosamente.
- Uma mudança que funciona hoje mas quebra com múltiplas instâncias do backend (ex.: lock em memória, cache local) sacrifica Escalabilidade — sinalizar mesmo que não seja o momento de resolver.

## Regra de ouro

Se uma mudança melhora um pilar às custas de outro, isso é uma decisão do usuário, não do agente — apresentar o trade-off antes de implementar (ver `CLAUDE.md`, seção "Antes de escrever código").
