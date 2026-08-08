# Playbook: nova API/endpoint

Sequência de execução para todo endpoint REST novo no backend. Seguir nesta ordem — pular etapa é o motivo mais comum de endpoint público sem querer (ver `anti-patterns.md`, `.anyRequest().permitAll()` fail-open).

## 1. Definir contrato
- Path e método seguem o padrão REST já existente (`/api/{recurso}` ou `/api/{recurso}/{id}/{ação}` — ver endpoints atuais em `stock`, `analysis`, `portfolio`).
- Checar se um DTO/record já existente cobre a necessidade antes de criar um novo (`core/models/models.ts` no frontend espelha os records do backend — checar os dois lados).
- Definir explicitamente o que o endpoint retorna em erro (hoje não há `@ControllerAdvice` — ver `anti-patterns.md` P2-11; não assumir que exceção vira resposta estruturada, ela vira 500 sem corpo).

## 2. Validar DTOs
- Request/response como `record` Java, imutável (ver `coding-standards.md`).
- Validar entrada na borda do controller, não confiar em dado do frontend.

## 3. Documentar erros
- Que exceções esse endpoint pode lançar? O que o frontend recebe hoje se lançar (sem `@ControllerAdvice`, provavelmente 500 vazio)? Se isso for inaceitável pro caso de uso, sinalizar — não implementar tratamento ad-hoc só nesse endpoint enquanto o resto do sistema não tem.

## 4. Verificar cache
- Esse dado muda com que frequência? Precisa de cache Redis? Em qual granularidade (por ticker? por usuário?)? TTL compatível com o resto do sistema (cotação = curto, análise = 30min, benchmark = 24h — ver `backend.md`)?
- Acionar `performance-reviewer` se o endpoint dispara chamada cara (LLM, dado externo) sem cache.

## 5. Avaliar autenticação
- **Obrigatório, nunca pular**: declarar explicitamente em `SecurityConfig` se o endpoint é público ou autenticado. O catch-all `.anyRequest().permitAll()` faz endpoint sem regra virar público por padrão — isso é responsabilidade de quem cria o endpoint, não do sistema.
- Se autenticado, endpoint deve escopar dado por `auth.getName()` (usuário do JWT) — nunca confiar em ID vindo do corpo da requisição.
- Endpoint público que dispara custo real (LLM/dado externo) — sinalizar ausência de rate limiting (`anti-patterns.md`), mesmo que não seja resolvido nesta tarefa.
- Acionar `security-reviewer` sempre.

## 6. Criar testes
- Cobertura hoje é baixa (`backend.md`) — não seguir o padrão existente de "sem teste de controller" como justificativa pra não testar o endpoint novo.
- No mínimo: caso feliz, caso de autorização negada (se autenticado), caso de dado inválido.

## 7. Atualizar frontend
- Adicionar o método correspondente no `*.service.ts` certo (`stock.service.ts`/`portfolio.service.ts`) — **não** injetar `HttpClient` direto na página (ver `anti-patterns.md`, `SimulatorPage` é o exemplo do que não fazer).
- Adicionar/atualizar interface em `core/models/models.ts` espelhando o DTO backend.
- URL base vem de `environment.apiUrl`/`environment.authUrl` (desde 2026-08-07, ver `decisions.md`) — nunca hardcodar `http://localhost:8080` de novo num service novo.

## Agentes a acionar
`security-reviewer` sempre. `performance-reviewer` se o endpoint tocar cache/custo de LLM. `backend-architect` se introduzir entidade/repositório novo.
