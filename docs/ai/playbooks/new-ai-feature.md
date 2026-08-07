# Playbook: nova funcionalidade de IA

Sequência de execução para qualquer mudança que toque o pipeline de IA (novo dado no prompt, nova dimensão, novo tipo de embedding, troca de modelo). Ler `docs/ai/invariants.md` inteiro antes de começar — várias etapas abaixo existem só pra proteger invariante já fixado.

## 1. Verificar se precisa de embeddings
- O dado novo precisa ser buscável por similaridade (RAG) ou só entra direto no prompt como texto formatado?
- Se for embedding: qual `type` de metadado (`fundamentals`/`analysis`/`historical_fundamentals`)? **Nunca criar um novo `type` que acabe sendo recuperado junto com `historical_fundamentals` sem decisão explícita** — a filtragem por `type` é o que protege o invariante #6 (RAG sem análises passadas).

## 2. Avaliar impacto no RAG
- Se a mudança toca `retrieveContext`/filtro de busca: ela está reintroduzindo `type=analysis` no contexto recuperado, mesmo que indiretamente? Esse é o erro mais fácil de cometer sem perceber (ver `rag.md`, "Não reverter essa decisão sem entender a consequência").
- Acionar `ai-engineer`.

## 3. Definir versão do prompt
- Mudou o texto do prompt? **`PROMPT_VERSION` incrementa, sempre** (invariante #9). Scores antes/depois deixam de ser comparáveis — isso é esperado, não um bug a esconder.
- Mudou o schema JSON de saída? `AnalysisParser` e `AnalysisParserTest` precisam de atualização correspondente antes de considerar a tarefa pronta.

## 4. Planejar fallback
- O dado novo é obrigatório ou opcional pra análise prosseguir? Só fundamentos é obrigatório hoje (invariante #13) — um dado novo virar obrigatório é uma mudança de comportamento que precisa ser explícita, não acidental.
- Se a fonte do dado novo falhar, o que acontece? Precisa de `safeGet` com fallback, igual ao padrão de macro/notícias/técnico.

## 5. Medir custo
- Isso aumenta `maxTokens` do prompt? Aumenta frequência de chamada de LLM (reduz cache hit, ou adiciona uma chamada nova por análise)?
- Acionar `performance-reviewer` se a resposta for sim a qualquer uma das duas.

## 6. Definir auditoria
- O dado novo entra em algum registro persistido, ou só passa pelo prompt e se perde? Desde 2026-08-07, `analysis_audit` persiste o prompt completo e a resposta bruta por análise (ver `decisions.md`) — mas campos individuais novos (ex.: um dado de fundamentals novo) só ficam registrados ali dentro do texto do prompt, não em coluna própria. Se o dado novo merece consulta estruturada própria (não só grep no prompt salvo), avaliar coluna dedicada.

## 7. Checar domínio financeiro
- O indicador/dado novo já tem seção em `domain-knowledge.md`? Se não, escrever antes de jogar o número cru no prompt — número sem contexto de interpretação é o motivo documentado de o LLM "inventar" comparação (ver `decisions.md`, motivação de `SectorBenchmarks`).
- Acionar `financial-analyst`.

## Agentes a acionar
`ai-engineer` sempre. `financial-analyst` se o dado for fundamentalista/setorial. `performance-reviewer` se afetar custo/latência.
