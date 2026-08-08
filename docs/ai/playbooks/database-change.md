# Playbook: mudança estrutural de banco

Sequência de execução para qualquer alteração de entidade JPA, tabela ou índice. Desde 2026-08-06 o schema é versionado via Flyway (`db/migration/V{n}__*.sql`), `ddl-auto: validate` (ver `decisions.md`) — não existe mais aplicação silenciosa no boot, mas isso não dispensa pensar a mudança antes de escrever a migration.

## 1. Escrever a migration
- Toda mudança de entidade (coluna, tabela, índice, constraint) precisa de um `V{n+1}__descricao.sql` novo em `db/migration/` — **nunca** editar `V1__baseline_schema.sql` ou qualquer migration já aplicada retroativamente (invariante #18).
- A mudança é aditiva (coluna nova nullable, tabela nova) ou destrutiva (remover/renomear coluna, mudar tipo, apertar constraint em dado existente)? Destrutiva precisa de mais cuidado nos passos 4-6 abaixo — Flyway aplica o SQL exatamente como escrito, não avisa se ele perde dado.
- **Renomear coluna nunca é uma migration só** (`ALTER TABLE ... RENAME COLUMN` quebra o código antigo se o deploy não for atômico): 1) migration aditiva cria a coluna nova; 2) migration faz backfill (`UPDATE ... SET novo = antigo`); 3) código Java passa a escrever nos dois até o deploy estabilizar; 4) migration separada, só depois de confirmar que nada mais lê a coluna antiga, faz o `DROP COLUMN`. Mesmo raciocínio já usado na migration de `Stock` (ver `decisions.md`), só que ali foi cutover numa migration só porque era ambiente pessoal sem dependência externa lendo a coluna — não é o padrão default, foi exceção justificada.
- Testar a migration contra um banco real antes de considerar pronto: schema vazio (ambiente novo) **e** schema já na versão anterior (ambiente de dev existente) — os dois caminhos precisam funcionar.

## 2. Impacto no cache
- Alguma entidade afetada é serializada pro Redis em algum lugar? Mudar o shape da entidade pode quebrar deserialização de cache antigo ainda no Redis (TTLs vão de curto a 24h — cache velho pode sobreviver o deploy).

## 3. Índices
- A mudança introduz uma query nova por um campo sem índice? Volume atual é pequeno (uso pessoal/poucos usuários) — mas se o campo for usado em `WHERE`/`ORDER BY` de forma recorrente, decidir o índice agora é mais barato que descobrir depois.
- Índice composto existente (`score_history`: `(ticker, analysisDate)`) é o padrão de referência — seguir a mesma lógica pra casos análogos.
- **Índice em tabela já com dado (`score_history`, `stock_embeddings` quando crescerem)**: `CREATE INDEX CONCURRENTLY` evita lock de escrita na tabela, mas **não pode rodar dentro de uma transação** — e o Flyway envolve toda migration numa transação por padrão, então a migration falha se o `CONCURRENTLY` for só mais uma linha de SQL num `V{n}__*.sql` normal. Precisa de um arquivo de config irmão `V{n}__descricao.sql.conf` com `executeInTransaction=false`, e o `CREATE INDEX CONCURRENTLY` sozinho nesse arquivo (nada mais junto). Enquanto o volume for pequeno (caso atual), índice normal dentro da transação já basta — isso só importa quando a tabela crescer o bastante pra um `CREATE INDEX` comum travar escrita por tempo perceptível.

## 4. Retrocompatibilidade
- Dado antigo já persistido continua válido com a entidade nova? (ex.: coluna nova sem default quebra `insert` de código antigo se o deploy não for atômico.)
- Rótulo/enum mudando: existe dado antigo no banco ou no cache Redis (TTL até 24h) com o valor antigo? Precisa de mapeamento de compatibilidade (já existe precedente: migração de rótulos COMPRAR/VENDER → ATRATIVO/NEUTRO manteve mapeamento pro cache antigo).

## 5. Volume esperado
- Essa tabela cresce por usuário, por ticker, por análise (diária/por request)? `score_history` cresce por análise — sem uma dimensão de "arquivar/agregar" hoje. Se a mudança acelera esse crescimento, mencionar mesmo que não seja bloqueante no volume atual.

## 6. Rollback
- Flyway (edição livre, sem Teams/Enterprise) não tem `undo` automático — reverter o código Java não desfaz uma migration já aplicada. Se a mudança pode precisar ser desfeita, escrever a migration de forma que uma `V{n+2}__revert_x.sql` consiga desfazer sem perda de dado (ex.: coluna nova fica órfã em vez de ser dropada por engano), ou ter esse plano manual explícito antes de aplicar mudança destrutiva.

## Agentes a acionar
`backend-architect` sempre. `performance-reviewer` se envolver índice/volume. `security-reviewer` se o campo novo guardar dado sensível (PII, segredo).
