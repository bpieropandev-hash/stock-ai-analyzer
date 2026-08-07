# Invariantes do sistema

Verdades que nunca podem ser quebradas, independente de refatoração, troca de LLM, troca de banco, ou pressa. Diferente de `decisions.md` (explica o *porquê* de uma escolha, revisável se o contexto mudar), isto aqui é lei — quebrar um item desta lista muda a identidade do produto, não é um trade-off de engenharia normal.

**Antes de aprovar qualquer mudança, checar se ela viola algo aqui.** Os 5 agentes em `.claude/agents/` devem tratar item quebrado desta lista como bloqueante, não como ressalva.

---

## Score

1. **`scoreGeral` nunca vem do LLM.** É sempre a média das 6 dimensões, recalculada em Java (`AnalysisParser.computeScoreGeral`). O modelo pode errar aritmética; o sistema não confia nisso.
2. **Dimensão ausente na resposta do LLM é erro, não zero.** `AnalysisParser` lança exceção — nunca preencher com 0.0 silencioso (um 0 falso derruba o score e dispara alerta por falha de parsing, não por fato real).
3. **Score de dimensão é sempre clampado em [0, 10].** Nunca propagar valor fora de faixa adiante.

## Recomendação

4. **Toda recomendação exposta ao usuário usa linguagem descritiva, nunca imperativa.** `ATRATIVO`/`NEUTRO`/`CAUTELA`/`DESFAVORÁVEL` — nunca `COMPRAR`/`VENDER`/`MANTER` em nenhuma camada (prompt, backend, frontend). Isso é conformidade com a Resolução CVM 20/2021, não estilo — quebrar isso é risco regulatório, não só inconsistência visual.
5. **Elegibilidade de carteira/alocação usa score numérico (≥ 6.0), nunca comparação de string de rótulo.** Torna a lógica imune a rótulo antigo em cache.

## RAG

6. **Apenas `type=historical_fundamentals` participa do contexto RAG.** `type=analysis` (análises passadas) nunca é recuperado — evita o LLM ancorar no próprio score anterior (feedback loop que contaminaria a série histórica). Ver `rag.md`/`decisions.md` para o raciocínio completo.

## Determinismo e reprodutibilidade

7. **`temperature=0` nos dois LLMs de scoring.** Scoring precisa ser determinístico — variância de amostragem gera alerta de mudança de score por ruído, não por fato novo.
8. **Toda análise registra `modelUsed` e `promptVersion`.** Reprodutibilidade só é possível sabendo exatamente qual modelo e qual versão de prompt geraram um score — nunca persistir um score sem essa proveniência.
9. **`PROMPT_VERSION` é incrementada a cada mudança de texto do prompt.** Scores de versões diferentes não são comparáveis entre si — nunca comparar histórico entre `promptVersion`s diferentes como se fossem a mesma escala.

## Fonte de dado

10. **CVM é sempre a fonte primária de fundamentos contábeis; yfinance é fallback, nunca o contrário.** Múltiplos do yfinance para B3 são frequentemente errados ou defasados — validado por checagem cruzada (ver `decisions.md`).
11. **Proveniência do dado (`fundamentalsSource`, `statementDate`) é sempre exposta**, nunca omitida silenciosamente ao adicionar fonte de dado nova.

## Sidecar Python

12. **O sidecar Python nunca contém regra de negócio.** Ele só coleta e formata dado bruto. Toda regra financeira (o que é dívida real para um banco, como interpretar um indicador, thresholds) vive no backend Java. Se uma decisão de "o que esse número significa" está sendo tomada em Python, ela está no lugar errado.

## Resiliência

13. **Funcionalidade opcional nunca impede uma análise.** Macro, notícias e indicadores técnicos podem falhar e a análise continua (`safeGet` com fallback). Só fundamentos é obrigatório. Nunca tornar um dado opcional bloqueante sem decisão explícita de que ele deixou de ser opcional.
14. **Falha de LLM primário sempre tenta fallback antes de desistir.** Gemini falha → tenta Groq → só aí erro pro usuário. Nunca remover o fallback sem substituir por outro.

## Tratamento setorial

15. **Para financeiras sem conta de "Empréstimos e Financiamentos" no balanço CVM, dívida/alavancagem não é penalizada em `Gestão de Risco`.** Depósito e captação não são dívida corporativa — tratar como se fossem distorce o setor inteiro.

## Segurança

16. **Segredo sensível (JWT secret, API key) nunca tem fallback fraco/hardcoded.** Ausência da env var deve falhar o boot, nunca cair silenciosamente para um valor previsível.

17. **`.env` nunca é escrito/sobrescrito por automação — só `.env.example`.** Regra do usuário, não do sistema, mas invariante da mesma forma: `.env` guarda segredo real do ambiente local, não é gitignored por acaso, e não tem histórico de recuperação. Qualquer chave nova precisa ser documentada em `.env.example` e pedida ao usuário, nunca escrita diretamente.

## Banco

18. **Mudança estrutural de schema (entidade nova, coluna, índice, constraint) sempre entra via migration Flyway nova (`db/migration/V{n}__*.sql`), nunca via `ddl-auto`.** Desde 2026-08-06 (ver `decisions.md`), `ddl-auto: validate` só confere — não altera o banco. Editar `V1__baseline_schema.sql` retroativamente ou usar `ddl-auto: update` de novo quebra a garantia de schema versionado que essa migração existiu pra dar.

---

## Como usar este arquivo

- Ao propor uma mudança, checar contra esta lista antes de checar contra `decisions.md` ou `financial-rules.md` — se violar um invariante, a resposta é "não", não "vamos discutir o trade-off".
- Ao adicionar um invariante novo: só entra aqui se quebrá-lo mudar a identidade do produto (compliance, integridade do score, reprodutibilidade) — não é lugar para preferência de estilo ou decisão reversível (isso é `decisions.md`).
- Este arquivo deve ser curto o suficiente para ser lido por inteiro antes de qualquer aprovação — se crescer demais a ponto de virar checklist genérico, algo foi classificado errado como invariante.
