import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  standalone: true,
  selector: 'app-nav',
  imports: [RouterLink, RouterLinkActive],
  template: `
    <nav class="nav">
      <div class="nav-inner">
        <a class="nav-logo" routerLink="/dashboard">
          <svg width="18" height="18" viewBox="0 0 20 20" fill="none">
            <polyline points="1,15 6,8 10,12 19,2"
              stroke="currentColor" stroke-width="2.4"
              stroke-linecap="round" stroke-linejoin="round"/>
            <line x1="1" y1="19" x2="19" y2="19"
              stroke="currentColor" stroke-width="1.2"
              stroke-linecap="round" opacity="0.3"/>
          </svg>
          <span class="logo-text">STOCK<em>AI</em></span>
        </a>

        <div class="nav-links">
          <a routerLink="/dashboard"  routerLinkActive="active">Dashboard</a>
          <a routerLink="/analysis"   routerLinkActive="active">Análise</a>
          <a routerLink="/portfolio"  routerLinkActive="active">Carteira</a>
          <a routerLink="/simulator"  routerLinkActive="active">Simulador</a>
          <a routerLink="/compare"    routerLinkActive="active">Comparar</a>
        </div>

        <button class="btn-logout" (click)="logout()">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
            <polyline points="16 17 21 12 16 7"/>
            <line x1="21" y1="12" x2="9" y2="12"/>
          </svg>
          Sair
        </button>
      </div>
    </nav>
  `,
  styles: [`
    .nav {
      position: sticky;
      top: 0;
      z-index: 100;
      background: rgba(4, 12, 20, 0.95);
      backdrop-filter: blur(12px);
      -webkit-backdrop-filter: blur(12px);
      border-bottom: 1px solid rgba(255, 255, 255, 0.065);
    }

    .nav-inner {
      max-width: 1360px;
      margin: 0 auto;
      padding: 0 24px;
      height: 52px;
      display: flex;
      align-items: center;
      gap: 28px;
    }

    .nav-logo {
      display: flex;
      align-items: center;
      gap: 8px;
      text-decoration: none;
      color: var(--accent);
      flex-shrink: 0;
    }

    .logo-text {
      font-family: var(--font-mono);
      font-size: 13px;
      font-weight: 700;
      letter-spacing: 0.12em;
      color: var(--text-primary);
      em {
        color: var(--accent);
        font-style: normal;
      }
    }

    .nav-links {
      display: flex;
      flex: 1;
      gap: 2px;

      a {
        position: relative;
        padding: 6px 13px;
        color: var(--text-muted);
        font-size: 12px;
        font-weight: 500;
        letter-spacing: 0.04em;
        text-decoration: none;
        text-transform: uppercase;
        transition: color 0.15s;
        border-radius: var(--radius-xs);
        white-space: nowrap;

        &:hover {
          color: var(--text-secondary);
          background: rgba(255, 255, 255, 0.035);
        }

        &.active {
          color: var(--accent);
          background: var(--accent-dim);
        }

        &.active::before {
          content: '';
          position: absolute;
          bottom: -1px;
          left: 13px;
          right: 13px;
          height: 1px;
          background: var(--accent);
          border-radius: 1px;
          box-shadow: 0 0 8px var(--accent);
        }
      }
    }

    .btn-logout {
      display: flex;
      align-items: center;
      gap: 6px;
      background: transparent;
      border: 1px solid var(--border);
      color: var(--text-muted);
      padding: 5px 13px;
      border-radius: var(--radius-xs);
      font-size: 11px;
      font-weight: 500;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      cursor: pointer;
      transition: all 0.15s;
      flex-shrink: 0;
      white-space: nowrap;

      &:hover {
        border-color: var(--danger);
        color: var(--danger);
        background: var(--danger-dim);
      }
    }
  `]
})
export class Nav {
  constructor(private auth: AuthService) {}
  logout() { this.auth.logout(); }
}
