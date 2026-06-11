import { Component } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  template: `
    <div class="login-bg">
      <div class="grid-overlay"></div>

      <div class="login-shell">
        <!-- Left: branding panel -->
        <div class="brand-panel">
          <div class="brand-logo">
            <svg width="24" height="24" viewBox="0 0 20 20" fill="none">
              <polyline points="1,15 6,8 10,12 19,2"
                stroke="currentColor" stroke-width="2.4"
                stroke-linecap="round" stroke-linejoin="round"/>
              <line x1="1" y1="19" x2="19" y2="19"
                stroke="currentColor" stroke-width="1.2"
                stroke-linecap="round" opacity="0.35"/>
            </svg>
            <span class="brand-name">STOCK<em>AI</em></span>
          </div>

          <h1 class="brand-headline">Análise de ações<br>com inteligência<br><span class="hl">artificial</span></h1>

          <div class="brand-stats">
            <div class="stat">
              <span class="stat-val">6</span>
              <span class="stat-label">Dimensões de análise</span>
            </div>
            <div class="stat">
              <span class="stat-val">10</span>
              <span class="stat-label">Pontuação máxima</span>
            </div>
            <div class="stat">
              <span class="stat-val">B3</span>
              <span class="stat-label">Bolsa brasileira</span>
            </div>
          </div>

          <div class="brand-features">
            <div class="feat">
              <span class="feat-icon"></span>
              <span>Score multicritério em tempo real</span>
            </div>
            <div class="feat">
              <span class="feat-icon"></span>
              <span>Resumos em linguagem simples</span>
            </div>
            <div class="feat">
              <span class="feat-icon"></span>
              <span>Simulador e comparador de ações</span>
            </div>
            <div class="feat">
              <span class="feat-icon"></span>
              <span>Gestão de carteira com avaliação IA</span>
            </div>
          </div>
        </div>

        <!-- Right: login card -->
        <div class="login-card">
          <div class="login-card-inner">
            <div class="card-tag">ACESSO SEGURO</div>
            <h2 class="card-title">Entre com sua<br>conta Google</h2>
            <p class="card-sub">
              Seus dados ficam protegidos.<br>
              Nenhuma senha é armazenada.
            </p>

            <button class="btn-google" (click)="login()">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
                <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
                <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
              </svg>
              Entrar com Google
            </button>

            <div class="card-divider"></div>

            <div class="card-footer">
              <span class="secure-badge">
                <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                </svg>
                Conexão criptografada
              </span>
              <span class="secure-badge">
                <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                </svg>
                JWT seguro
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .login-bg {
      min-height: 100vh;
      background: var(--bg-base);
      display: flex;
      align-items: stretch;
      position: relative;
      overflow: hidden;
    }

    .grid-overlay {
      position: fixed;
      inset: 0;
      background-image:
        linear-gradient(rgba(0, 229, 195, 0.025) 1px, transparent 1px),
        linear-gradient(90deg, rgba(0, 229, 195, 0.025) 1px, transparent 1px);
      background-size: 48px 48px;
      pointer-events: none;
      z-index: 0;
    }

    .login-shell {
      display: flex;
      width: 100%;
      max-width: 1200px;
      margin: 0 auto;
      padding: 40px 24px;
      gap: 80px;
      align-items: center;
      position: relative;
      z-index: 1;
    }

    /* ── Left brand panel ── */
    .brand-panel {
      flex: 1;
      max-width: 520px;
      animation: fadeIn 0.5s ease-out;
    }

    .brand-logo {
      display: flex;
      align-items: center;
      gap: 10px;
      color: var(--accent);
      margin-bottom: 48px;
    }

    .brand-name {
      font-family: var(--font-mono);
      font-size: 14px;
      font-weight: 700;
      letter-spacing: 0.14em;
      color: var(--text-primary);
      em { color: var(--accent); font-style: normal; }
    }

    .brand-headline {
      font-family: var(--font-display);
      font-size: clamp(36px, 5vw, 52px);
      font-weight: 800;
      color: var(--text-primary);
      line-height: 1.08;
      letter-spacing: -0.03em;
      margin-bottom: 40px;
    }

    .hl {
      color: var(--accent);
      position: relative;
      &::after {
        content: '';
        position: absolute;
        left: 0;
        bottom: 4px;
        width: 100%;
        height: 2px;
        background: var(--accent);
        opacity: 0.35;
      }
    }

    .brand-stats {
      display: flex;
      gap: 32px;
      margin-bottom: 40px;
      padding-top: 24px;
      border-top: 1px solid var(--border);
    }

    .stat {
      display: flex;
      flex-direction: column;
      gap: 3px;
    }

    .stat-val {
      font-family: var(--font-mono);
      font-size: 26px;
      font-weight: 700;
      color: var(--accent);
      line-height: 1;
    }

    .stat-label {
      font-size: 11px;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.06em;
    }

    .brand-features {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .feat {
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 13px;
      color: var(--text-secondary);
    }

    .feat-icon {
      display: inline-block;
      width: 18px;
      height: 18px;
      border-radius: 50%;
      background: var(--accent-dim);
      border: 1px solid rgba(0, 229, 195, 0.3);
      flex-shrink: 0;
      position: relative;

      &::after {
        content: '';
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -55%) rotate(45deg);
        width: 4px;
        height: 7px;
        border-right: 1.5px solid var(--accent);
        border-bottom: 1.5px solid var(--accent);
      }
    }

    /* ── Right login card ── */
    .login-card {
      flex-shrink: 0;
      width: 360px;
      animation: slideIn 0.5s ease-out 0.1s both;
    }

    .login-card-inner {
      background: var(--bg-surface);
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-xl);
      padding: 36px 32px;
      box-shadow: 0 24px 80px rgba(0, 0, 0, 0.6),
                  0 0 0 1px rgba(255, 255, 255, 0.04) inset;
    }

    .card-tag {
      display: inline-block;
      font-family: var(--font-mono);
      font-size: 9px;
      font-weight: 700;
      letter-spacing: 0.14em;
      color: var(--accent);
      background: var(--accent-dim);
      border: 1px solid rgba(0, 229, 195, 0.2);
      border-radius: var(--radius-xs);
      padding: 3px 8px;
      margin-bottom: 20px;
    }

    .card-title {
      font-family: var(--font-display);
      font-size: 24px;
      font-weight: 800;
      color: var(--text-primary);
      line-height: 1.2;
      margin-bottom: 12px;
      letter-spacing: -0.02em;
    }

    .card-sub {
      font-size: 13px;
      color: var(--text-secondary);
      line-height: 1.65;
      margin-bottom: 28px;
    }

    .btn-google {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 12px;
      width: 100%;
      padding: 13px 20px;
      background: #fff;
      color: #1a1a1a;
      border-radius: var(--radius-sm);
      font-family: var(--font-body);
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
      border: none;
      transition: all 0.2s;

      &:hover {
        background: #f4f4f4;
        box-shadow: 0 4px 24px rgba(0, 0, 0, 0.4);
        transform: translateY(-1px);
      }

      &:active {
        transform: translateY(0);
      }
    }

    .card-divider {
      height: 1px;
      background: var(--border);
      margin: 24px 0;
    }

    .card-footer {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
    }

    .secure-badge {
      display: flex;
      align-items: center;
      gap: 5px;
      font-size: 10px;
      color: var(--text-muted);
      background: var(--bg-elevated);
      border: 1px solid var(--border);
      border-radius: var(--radius-xs);
      padding: 4px 8px;
    }

    /* ── Animations ── */
    @keyframes fadeIn {
      from { opacity: 0; transform: translateX(-16px); }
      to   { opacity: 1; transform: translateX(0); }
    }

    @keyframes slideIn {
      from { opacity: 0; transform: translateY(16px); }
      to   { opacity: 1; transform: translateY(0); }
    }

    /* ── Responsive ── */
    @media (max-width: 880px) {
      .login-shell {
        flex-direction: column;
        align-items: center;
        gap: 40px;
        padding: 32px 20px;
      }
      .brand-panel { max-width: 100%; text-align: center; }
      .brand-stats { justify-content: center; }
      .brand-features { align-items: center; }
      .login-card { width: 100%; max-width: 400px; }
    }
  `]
})
export class LoginPage {
  constructor(private auth: AuthService) {}
  login() { this.auth.loginWithGoogle(); }
}
