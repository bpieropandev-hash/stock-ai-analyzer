import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Nav } from '../../shared/components/nav/nav';
import { RecommendationBadge } from '../../shared/components/recommendation-badge/recommendation-badge';
import { TickerSelect } from '../../shared/components/ticker-select/ticker-select';
import { PortfolioService } from '../../core/services/portfolio.service';
import { EvaluationItem, PortfolioItemResponse } from '../../core/models/models';

const MINI_R = 14;
const MINI_CIRC = 2 * Math.PI * MINI_R;

@Component({
  selector: 'app-portfolio',
  imports: [CommonModule, FormsModule, RouterLink, Nav, RecommendationBadge, TickerSelect],
  template: `
    <app-nav />

    <div class="port-shell">

      <div class="page-header">
        <div>
          <h1>Minha Carteira</h1>
          <p>Posições e avaliação inteligente das suas ações</p>
        </div>
        <button class="btn-evaluate" (click)="evaluate()" [disabled]="evaluating() || portfolio().length === 0">
          @if (evaluating()) {
            <span class="spinner"></span>
            Avaliando…
          } @else {
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
              <polyline points="22 4 12 14.01 9 11.01"/>
            </svg>
            Avaliar Carteira
          }
        </button>
      </div>

      <!-- ── Add form ── -->
      <div class="add-card">
        <div class="add-hd">
          <span class="add-dot"></span>
          <span>ADICIONAR / ATUALIZAR POSIÇÃO</span>
        </div>
        <div class="form-row">
          <div class="form-group ticker-group">
            <label class="field-label">Ticker</label>
            <app-ticker-select [(value)]="form.ticker" placeholder="PETR4, VALE3..." />
          </div>
          <div class="form-group">
            <label class="field-label">Quantidade</label>
            <input class="field-input mono-input" type="number" [(ngModel)]="form.quantity" placeholder="100" />
          </div>
          <div class="form-group">
            <label class="field-label">Preço Médio (R$)</label>
            <input class="field-input mono-input" type="number" [(ngModel)]="form.averagePrice" placeholder="28.50" />
          </div>
          <div class="form-group">
            <label class="field-label">Data de Compra</label>
            <input class="field-input date-input" type="date" [(ngModel)]="form.purchaseDate" />
          </div>
          <button class="btn-save" (click)="addOrUpdate()" [disabled]="saving()">
            @if (saving()) { <span class="spinner"></span> }
            @else {
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
                <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/>
              </svg>
              Salvar
            }
          </button>
        </div>
      </div>

      <!-- ── Controls row ── -->
      <div class="controls-row">
        <h2 class="section-title">Posições</h2>
        <button class="btn-reload" (click)="loadPortfolio()" [disabled]="loadingPortfolio()">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
            <polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 .49-3.51"/>
          </svg>
          Recarregar
        </button>
      </div>

      <!-- ── Portfolio table ── -->
      @if (loadingPortfolio()) {
        <div class="empty-state">
          <span class="spinner" style="width:28px;height:28px;border-width:3px"></span>
        </div>
      } @else if (portfolio().length === 0) {
        <div class="empty-state">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" opacity="0.25">
            <rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 21V9"/>
          </svg>
          <p>Nenhuma posição cadastrada. Adicione sua primeira ação acima.</p>
        </div>
      } @else {
        <div class="table-wrap">
          <table class="port-table">
            <thead>
              <tr>
                <th>Ticker</th>
                <th>Setor</th>
                <th class="num">Qtd</th>
                <th class="num">Preço Médio</th>
                <th class="num">Score</th>
                <th>Recomendação</th>
                <th>Resumo</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (item of portfolio(); track item.ticker) {
                <tr>
                  <td>
                    <a class="ticker-link" [routerLink]="['/analysis']" [queryParams]="{ticker: item.ticker.replace('.SA','')}">
                      {{ item.ticker.replace('.SA','') }}
                    </a>
                  </td>
                  <td class="sector-cell">{{ item.sector }}</td>
                  <td class="num mono-cell">{{ item.quantity | number }}</td>
                  <td class="num mono-cell">R$&nbsp;{{ item.averagePrice | number:'1.2-2' }}</td>
                  <td class="num">
                    <div class="mini-gauge">
                      <svg viewBox="0 0 40 40" width="36" height="36">
                        <circle cx="20" cy="20" r="14" fill="none" stroke="rgba(255,255,255,0.06)" stroke-width="4"/>
                        <circle cx="20" cy="20" r="14" fill="none"
                          [attr.stroke]="scoreColor(getScore(item))"
                          stroke-width="4"
                          stroke-linecap="round"
                          [attr.stroke-dasharray]="MINI_CIRC"
                          [attr.stroke-dashoffset]="miniOffset(getScore(item))"
                          transform="rotate(-90, 20, 20)"/>
                      </svg>
                      <span class="mini-val" [style.color]="scoreColor(getScore(item))">
                        {{ getScore(item) | number:'1.1-1' }}
                      </span>
                    </div>
                  </td>
                  <td><app-recommendation-badge [recommendation]="item.recommendation" /></td>
                  <td class="summary-cell">{{ item.simpleSummary }}</td>
                  <td>
                    <button class="btn-remove" (click)="remove(item.ticker)" title="Remover posição">
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
                        <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                      </svg>
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      <!-- ── Evaluation results ── -->
      @if (evaluation().length > 0) {
        <div class="eval-section fade-in">
          <div class="eval-hd">
            <span class="eval-dot"></span>
            <h2 class="section-title">Avaliação da Carteira</h2>
          </div>
          <div class="eval-grid">
            @for (e of evaluation(); track e.ticker) {
              <div class="eval-card" [class]="evalClass(e.action)">
                <div class="eval-action">{{ formatAction(e.action) }}</div>
                <div class="eval-ticker">{{ e.ticker.replace('.SA','') }}</div>
                <div class="eval-score-row">
                  <div class="mini-gauge" style="margin: 0">
                    <svg viewBox="0 0 40 40" width="32" height="32">
                      <circle cx="20" cy="20" r="14" fill="none" stroke="rgba(255,255,255,0.06)" stroke-width="4"/>
                      <circle cx="20" cy="20" r="14" fill="none"
                        [attr.stroke]="scoreColor(e.scoreGeral)"
                        stroke-width="4"
                        stroke-linecap="round"
                        [attr.stroke-dasharray]="MINI_CIRC"
                        [attr.stroke-dashoffset]="miniOffset(e.scoreGeral)"
                        transform="rotate(-90, 20, 20)"/>
                    </svg>
                    <span class="mini-val" [style.color]="scoreColor(e.scoreGeral)">{{ e.scoreGeral | number:'1.1-1' }}</span>
                  </div>
                </div>
                <p class="eval-summary">{{ e.simpleSummary }}</p>
              </div>
            }
          </div>
        </div>
      }

    </div>
  `,
  styles: [`
    :host {
      --a:     #00d4aa;
      --a-dim: rgba(0, 212, 170, 0.1);
      --surf:  #0d1929;
      --brd:   rgba(255, 255, 255, 0.07);
    }

    /* ── Shell ── */
    .port-shell { max-width: 1400px; margin: 0 auto; padding: 28px 40px; }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 24px;

      h1 { font-family: var(--font-display); font-size: 24px; font-weight: 800; color: var(--text-primary); letter-spacing: -0.02em; }
      p  { color: var(--text-secondary); margin-top: 4px; font-size: 13px; }
    }

    .btn-evaluate {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      padding: 10px 20px;
      background: var(--a);
      color: #04100d;
      border: none;
      border-radius: 6px;
      font-size: 13px;
      font-weight: 700;
      cursor: pointer;
      transition: all 0.18s;
      flex-shrink: 0;

      &:hover { background: #00f0c2; box-shadow: 0 0 20px rgba(0,212,170,0.25); }
      &:disabled { opacity: 0.4; cursor: not-allowed; box-shadow: none; }
    }

    /* ── Add card ── */
    .add-card {
      background: var(--surf);
      border: 1px solid var(--brd);
      border-radius: 8px;
      padding: 20px;
      box-shadow: 0 2px 20px rgba(0,0,0,0.4);
      margin-bottom: 24px;
    }

    .add-hd {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 9px;
      font-weight: 700;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.12em;
      margin-bottom: 16px;
    }

    .add-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--a); flex-shrink: 0; }

    .form-row {
      display: flex;
      gap: 10px;
      align-items: flex-end;
      flex-wrap: wrap;
    }

    .form-group { display: flex; flex-direction: column; flex: 1; min-width: 110px; }
    .ticker-group { flex: 0 0 220px; }

    .field-label {
      font-size: 10px;
      font-weight: 600;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.08em;
      margin-bottom: 5px;
    }

    .field-input {
      width: 100%;
      background: rgba(255,255,255,0.04);
      border: 1px solid rgba(255,255,255,0.1);
      border-radius: 6px;
      color: var(--text-primary);
      padding: 9px 12px;
      font-family: var(--font-body);
      font-size: 13px;
      outline: none;
      transition: border-color 0.18s;

      &:focus { border-color: var(--a); }
      &::placeholder { color: var(--text-muted); }
    }

    .mono-input { font-family: var(--font-mono); }
    .date-input { color-scheme: dark; }

    .btn-save {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 9px 18px;
      background: var(--a);
      color: #04100d;
      border: none;
      border-radius: 6px;
      font-size: 13px;
      font-weight: 700;
      cursor: pointer;
      transition: all 0.18s;
      height: 38px;
      flex-shrink: 0;

      &:hover { background: #00f0c2; }
      &:disabled { opacity: 0.4; cursor: not-allowed; }
    }

    /* ── Controls ── */
    .controls-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 14px;
    }

    .section-title {
      font-family: var(--font-display);
      font-size: 15px;
      font-weight: 700;
      color: var(--text-primary);
    }

    .btn-reload {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 7px 14px;
      background: transparent;
      color: var(--text-secondary);
      border: 1px solid rgba(255,255,255,0.1);
      border-radius: 6px;
      font-size: 12px;
      cursor: pointer;
      transition: all 0.18s;

      &:hover { border-color: var(--a); color: var(--a); }
      &:disabled { opacity: 0.4; cursor: not-allowed; }
    }

    /* ── Table ── */
    .table-wrap {
      background: var(--surf);
      border: 1px solid var(--brd);
      border-radius: 8px;
      overflow-x: auto;
      box-shadow: 0 2px 20px rgba(0,0,0,0.4);
      margin-bottom: 24px;
    }

    .port-table {
      width: 100%;
      border-collapse: collapse;
      min-width: 700px;

      th, td {
        padding: 10px 14px;
        text-align: left;
        border-bottom: 1px solid rgba(255,255,255,0.05);
        font-size: 12px;
      }

      th {
        font-size: 9px;
        text-transform: uppercase;
        letter-spacing: 0.08em;
        color: var(--text-muted);
        font-weight: 600;
        background: rgba(255,255,255,0.02);
        white-space: nowrap;
      }

      tr:last-child td { border-bottom: none; }
      tbody tr:hover td { background: rgba(255,255,255,0.02); }
    }

    .num { text-align: right !important; }
    .mono-cell { font-family: var(--font-mono); color: var(--text-secondary); }
    .sector-cell { color: var(--text-muted); font-size: 11px; }
    .summary-cell { max-width: 180px; font-size: 11px; color: var(--text-muted); line-height: 1.45; }

    .ticker-link {
      font-family: var(--font-mono);
      font-size: 13px;
      font-weight: 700;
      color: var(--a);
      text-decoration: none;
      letter-spacing: 0.05em;
      &:hover { text-decoration: underline; }
    }

    /* ── Mini gauge ── */
    .mini-gauge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      position: relative;
    }

    .mini-val {
      font-family: var(--font-mono);
      font-size: 12px;
      font-weight: 700;
    }

    .btn-remove {
      background: none;
      border: none;
      color: var(--text-muted);
      cursor: pointer;
      padding: 5px;
      border-radius: 4px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.15s;
      &:hover { color: #ef4444; background: rgba(239,68,68,0.1); }
    }

    /* ── Evaluation ── */
    .eval-section { margin-top: 28px; }

    .eval-hd {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 14px;
    }

    .eval-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--a); flex-shrink: 0; }

    .eval-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
      gap: 10px;
    }

    .eval-card {
      background: var(--surf);
      border: 1px solid var(--brd);
      border-radius: 8px;
      padding: 16px;
      box-shadow: 0 2px 16px rgba(0,0,0,0.35);

      &.buy-more { border-color: rgba(0,212,170,0.25); background: rgba(0,212,170,0.04); }
      &.hold     { border-color: rgba(59,130,246,0.25); background: rgba(59,130,246,0.04); }
      &.sell     { border-color: rgba(239,68,68,0.25);  background: rgba(239,68,68,0.04); }
    }

    .eval-action {
      font-size: 9px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.1em;
      color: var(--text-muted);
      margin-bottom: 8px;

      .buy-more & { color: var(--a); }
      .hold &     { color: #3b82f6; }
      .sell &     { color: #ef4444; }
    }

    .eval-ticker {
      font-family: var(--font-mono);
      font-size: 18px;
      font-weight: 700;
      color: var(--text-primary);
      margin-bottom: 10px;
      letter-spacing: 0.05em;
    }

    .eval-score-row { margin-bottom: 10px; }
    .eval-summary { font-size: 11px; color: var(--text-muted); line-height: 1.5; }

    /* ── Empty ── */
    .empty-state {
      text-align: center;
      padding: 60px 24px;
      color: var(--text-secondary);
      p { font-size: 13px; margin-top: 12px; color: var(--text-muted); }
    }

    /* ── Spinner ── */
    .spinner {
      display: inline-block;
      width: 14px;
      height: 14px;
      border: 2px solid rgba(255,255,255,0.12);
      border-top-color: currentColor;
      border-radius: 50%;
      animation: spin 0.65s linear infinite;
      flex-shrink: 0;
    }

    @keyframes spin { to { transform: rotate(360deg); } }

    .fade-in { animation: fi 0.35s ease-out; }
    @keyframes fi { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }

    @media (max-width: 768px) { .port-shell { padding: 20px; } .form-row { flex-direction: column; } .ticker-group { flex: 1; } }
  `]
})
export class PortfolioPage implements OnInit {
  readonly MINI_CIRC = MINI_CIRC;

  portfolio = signal<PortfolioItemResponse[]>([]);
  evaluation = signal<EvaluationItem[]>([]);
  loadingPortfolio = signal(false);
  saving = signal(false);
  evaluating = signal(false);

  form = { ticker: '', quantity: 0, averagePrice: 0, purchaseDate: '' };

  constructor(private portfolioService: PortfolioService) {}

  ngOnInit() { this.loadPortfolio(); }

  loadPortfolio() {
    this.loadingPortfolio.set(true);
    this.portfolioService.getPortfolio().subscribe({
      next: p => { this.portfolio.set(p); this.loadingPortfolio.set(false); },
      error: () => this.loadingPortfolio.set(false)
    });
  }

  addOrUpdate() {
    if (!this.form.ticker) return;
    this.saving.set(true);
    this.portfolioService.addOrUpdate({
      ticker: this.form.ticker.toUpperCase(),
      quantity: this.form.quantity,
      averagePrice: this.form.averagePrice,
      purchaseDate: this.form.purchaseDate || null
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.form = { ticker: '', quantity: 0, averagePrice: 0, purchaseDate: '' };
        this.loadPortfolio();
      },
      error: () => this.saving.set(false)
    });
  }

  remove(ticker: string) {
    this.portfolioService.remove(ticker).subscribe({ next: () => this.loadPortfolio() });
  }

  evaluate() {
    this.evaluating.set(true);
    this.portfolioService.evaluate().subscribe({
      next: e => { this.evaluation.set(e); this.evaluating.set(false); },
      error: () => this.evaluating.set(false)
    });
  }

  miniOffset(score: number): number { return MINI_CIRC - (score / 10) * MINI_CIRC; }
  scoreColor(s: number): string { return s >= 6.5 ? '#00d4aa' : s >= 4 ? '#f59e0b' : '#ef4444'; }

  evalClass(action: string): string {
    const map: Record<string, string> = { 'COMPRAR_MAIS': 'buy-more', 'MANTER': 'hold', 'VENDER': 'sell' };
    return map[action] ?? '';
  }

  formatAction(action: string): string {
    const map: Record<string, string> = { 'COMPRAR_MAIS': '▲ Comprar Mais', 'MANTER': '= Manter', 'VENDER': '▼ Vender' };
    return map[action] ?? action;
  }
  getScore(item: any): number {
    return item.scoreGeral ?? item.currentScore ?? 0;
  }
}
