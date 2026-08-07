# Frontend

Angular 21, standalone components (sem NgModules), TypeScript 5.9, RxJS 7.8. Testes: Vitest + jsdom (`ng test`).

## Comandos

```bash
npm install        # instalar dependências
npm start          # inicia em dev (ng serve)
npm test           # testes unitários (ng test)
npm run build      # build de produção
```

## Rotas (`app.routes.ts`)

Todas via `loadComponent` (lazy). Guard: `authGuard` (checa só presença de token em `localStorage`, não expiração).

| Path | Guardado |
|---|---|
| `login`, `auth/callback` | Não |
| `dashboard`, `analysis`, `portfolio`, `simulator`, `compare` | Sim |
| `''` e `**` | redirect para `dashboard` |

## Estrutura

- `core/guards/auth.guard.ts`, `core/interceptors/auth.interceptor.ts`, `core/models/models.ts` (interfaces espelhando DTOs backend), `core/services/{auth,stock,portfolio}.service.ts`.
- `pages/{dashboard,analysis,compare,portfolio,simulator,login,auth-callback}`.
- `shared/components/{nav,recommendation-badge,score-bar,ticker-select}`.

## HTTP services

Desde 2026-08-07, `src/environments/environment.ts` (dev, absoluto `http://localhost:8080`) e `environment.prod.ts` (prod, relativo `/api` — assume backend na mesma origem via reverse proxy) trocados via `fileReplacements` no `angular.json` (`ng build --configuration production`). Nenhum service ou componente deve voltar a hardcodar `http://localhost:8080` — sempre importar `environment` de `../../../environments/environment` (ajustar profundidade conforme a pasta). Ver `decisions.md`.

- `stock.service.ts`: `getQuotes`, `analyze(ticker)`, `refreshAnalysis(ticker)`, `compare(tickers)`, `getAlerts(days)`. `connectWebSocket`/`disconnectWebSocket` na verdade fazem polling HTTP a cada 30s (`setInterval`) — não é WebSocket real.
- `portfolio.service.ts`: `getPortfolio`, `addOrUpdate`, `remove(ticker)`, `evaluate`, `suggestAllocation(amount)`.
- `auth.service.ts`: `loginWithGoogle()` usa `environment.authUrl` (`''` em prod, relativo).
- `SimulatorPage` injeta `HttpClient` direto e chama `${environment.apiUrl}/simulate` — **não usa `PortfolioService`**, endpoint diferente de `suggestAllocation` (`/api/portfolio/suggest-allocation`). Inconsistência conhecida, não corrigir sem entender se são fluxos propositalmente distintos.

## Design system — OBRIGATÓRIO em qualquer mudança de CSS

Framework: SCSS puro com variáveis CSS. **Sem Tailwind, sem Bootstrap.**

```scss
--color-bg: #0a0f1e
--color-surface: #0d1929
--color-surface-2: #111827
--color-accent: #00d4aa
--color-accent-2: #f59e0b
--color-danger: #ef4444
--color-text: #e2e8f0
--color-text-muted: #94a3b8
--color-border: rgba(255,255,255,0.08)
```

- Tipografia: Syne (títulos/números), Inter (corpo) — Google Fonts.
- Border-radius: 8px cards, 6px inputs, 20px badges.
- Sombra padrão: `0 4px 24px rgba(0,0,0,0.4)`.

### Proibições absolutas
- Gradientes roxos/azuis genéricos.
- `border-radius` > 12px em cards.
- `font-family` genérica (Arial, Roboto, system-ui).
- Cores hardcoded — sempre variável CSS.
- Layout sem `max-width` definido.
- Componente sem estado de loading.

### Padrões obrigatórios
- Cards: `background var(--color-surface)`, `border 1px solid var(--color-border)`.
- Títulos de página: Syne, `2rem`, `700`.
- Barras de score: `height 8px`, `border-radius 4px`, animação CSS 0→valor.
- Badges de recomendação: `padding 6px 16px`, `12px`, `600`, uppercase.
- Max-width do conteúdo: `1280px`, `margin 0 auto`, `padding 0 24px`.
- Gap entre cards: `16px`. Spacing vertical entre seções: `32px`.

### Componentes específicos
- Score gauge: SVG circle com `stroke-dasharray` animado, número Syne bold centralizado.
- Score bar: cor por valor (vermelho `<4`, amarelo `4–6.5`, verde `>6.5`).
- Stock card dashboard: `height 120px` — ticker + preço + variação + setor.
- Recommendation badge: linguagem descritiva (Res. CVM 20/2021 — nunca COMPRAR/VENDER); cores fixas `ATRATIVO=#00d4aa`, `NEUTRO=#3b82f6`, `CAUTELA=#f59e0b`, `DESFAVORÁVEL=#ef4444`.

### Processo obrigatório para mudança visual
1. Ler este arquivo antes de qualquer CSS.
2. Verificar se a variável CSS já existe antes de criar nova.
3. `ng build` após cada mudança.
4. Reportar o que mudou e por quê.

## Débitos conhecidos (não corrigir sem avisar)

- `@stomp/stompjs`/`sockjs-client` no `package.json`, sem uso real (WebSocket abandonado, `/ws` dava 404).
- `ScoreBar` parece componente órfão — páginas reimplementam a barra inline.
- Único teste existente é o spec default do Angular CLI (`app.spec.ts`) — zero cobertura real de página/serviço/guard.
