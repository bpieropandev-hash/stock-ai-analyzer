-- Role somente-leitura pro Postgres MCP (Claude Code) — NUNCA a role da aplicação (stockai_user).
-- Rodar manualmente contra o Postgres local (psql/podman exec) — Claude não executa isso sozinho,
-- a senha é escolhida por quem roda o script, nunca gerada/vista pelo assistente.
--
-- Uso:
--   psql -h localhost -U postgres -d stockai -f infra/postgres-mcp-readonly-role.sql
-- (ajustar usuário/host/db se seu setup local for diferente do docker-compose.yml)
--
-- Depois de rodar, trocar a senha abaixo por uma real antes do CREATE ROLE, ou definir
-- via \set na sessão psql. Nunca commitar a senha real em lugar nenhum.

CREATE ROLE stockai_mcp_readonly WITH LOGIN PASSWORD 'TROCAR_ANTES_DE_RODAR';

GRANT CONNECT ON DATABASE stockai TO stockai_mcp_readonly;
GRANT USAGE ON SCHEMA public TO stockai_mcp_readonly;

-- SELECT nas tabelas que já existem hoje
GRANT SELECT ON ALL TABLES IN SCHEMA public TO stockai_mcp_readonly;

-- Exclui users do SELECT geral: única tabela com PII (email, google_id, name, picture_url).
-- Risco não é "sistema multi-usuário real hoje" — é vazar PII pro transcript do assistente
-- por um canal que não existia antes. Se precisar depurar users pontualmente, GRANT SELECT
-- manual e revogar depois. (achado do security-reviewer, 2026-08-08)
REVOKE SELECT ON users FROM stockai_mcp_readonly;

-- ATENÇÃO: só funciona automaticamente enquanto CREATE TABLE futuro rodar sob stockai_user
-- (hoje é o caso — ver POSTGRES_USER em docker-compose.yml/application.yml). Se isso mudar,
-- o auto-grant para de funcionar SEM AVISO. Reexecutar este script (idempotente o suficiente)
-- se uma tabela nova não aparecer pro MCP. Tabela nova que guardar PII de usuário precisa do
-- mesmo REVOKE explícito acima, não é automático.
ALTER DEFAULT PRIVILEGES FOR ROLE stockai_user IN SCHEMA public
    GRANT SELECT ON TABLES TO stockai_mcp_readonly;

-- Sem GRANT de INSERT/UPDATE/DELETE/TRUNCATE/DDL — propositalmente. Modo --access-mode=restricted
-- do postgres-mcp já limita a transações read-only, isso aqui é a segunda trava, no próprio banco.

-- Reduzir raio de dano de qualquer query longa/travada iniciada via MCP
ALTER ROLE stockai_mcp_readonly SET statement_timeout = '30s';

-- Evita loop de agente descontrolado abrindo conexões demais contra o Postgres local
ALTER ROLE stockai_mcp_readonly CONNECTION LIMIT 5;
