import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Nav } from '../../shared/components/nav/nav';
import { RecommendationBadge } from '../../shared/components/recommendation-badge/recommendation-badge';
import { TickerSelect } from '../../shared/components/ticker-select/ticker-select';
import { StockService } from '../../core/services/stock.service';
import { ComparisonResult } from '../../core/models/models';

@Component({
  selector: 'app-compare',
  imports: [CommonModule, RouterLink, Nav, RecommendationBadge, TickerSelect],
  template: `
    <app-nav />

    <div class="cmp-shell">

      <div class="page-header">
        <h1>Comparar Ações</h1>
        <p>Análise lado a lado — máximo de 5 tickers</p>
      </div>

      <!-- ── Search ── -->
      <div class="search-card">
        <label class="field-label">
          Tickers para comparar
          @if (selectedTickers.length > 0) {
            <span class="ticker-count">{{ selectedTickers.length }}/5</span>
          }
        </label>
        <div class="search-row">
          <div class="select-wrap">
            <app-ticker-select
              [multi]="true"
              [max]="5"
              [(values)]="selectedTickers"
              placeholder="Adicionar ticker (máx. 5)..." />
          </div>
          <button class="btn-compare" (click)="compare()"
            [disabled]="loading() || selectedTickers.length < 2">
            @if (loading()) {
              <span class="spinner"></span>
              Comparando…
            } @else {
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
                <line x1="18" y1="20" x2="18" y2="10"/>
                <line x1="12" y1="20" x2="12" y2="4"/>
                <line x1="6" y1="20" x2="6" y2="14"/>
              </svg>
              Comparar
            }
          </button>
        </div>
        @if (selectedTickers.length < 2 && selectedTickers.length > 0) {
          <p class="hint-text">Adicione pelo menos mais um ticker</p>
        }
      </div>

      @if (error()) {
        <div class="error-box">{{ error() }}</div>
      }

      @if (result(); as r) {
        <div class="compare-results fade-in">

          <!-- ── Highlight cards ── -->
          <div class="highlights">
            <div class="hl-card hl-green">
              <div class="hl-icon">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                  <polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/>
                </svg>
              </div>
              <div class="hl-label">Melhor Dividendos</div>
              <div class="hl-ticker">{{ cleanTicker(r.bestForDividends) }}</div>
            </div>
            <div class="hl-card hl-amber">
              <div class="hl-icon">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                  <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
                </svg>
              </div>
              <div class="hl-label">Melhor Momentum</div>
              <div class="hl-ticker">{{ cleanTicker(r.bestMomentum) }}</div>
            </div>
            <div class="hl-card hl-blue">
              <div class="hl-icon">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                </svg>
              </div>
              <div class="hl-label">Menor Risco</div>
              <div class="hl-ticker">{{ cleanTicker(r.lowestRisk) }}</div>
            </div>
          </div>

          <!-- ── Comparison table ── -->
          <div class="table-card">
            <div class="table-scroll">
              <table class="cmp-table">
                <thead>
                  <tr>
                    <th class="dim-col">Dimensão</th>
                    @for (a of r.analyses; track $index) {
                      <th class="ticker-col">
                        <a class="th-ticker" [routerLink]="['/analysis']" [queryParams]="{ticker: cleanTicker(a.ticker)}">
                          {{ cleanTicker(a.ticker) }}
                        </a>
                        <div class="th-sector">{{ a.sector }}</div>
                      </th>
                    }
                  </tr>
                </thead>
                <tbody>

                  <!-- Score geral -->
                  <tr class="row-highlight">
                    <td class="dim-label">Score Geral</td>
                    @for (a of r.analyses; track $index) {
                      <td class="score-cell">
                        <span class="score-num" [style.color]="scoreColor(a.analysis.scoreGeral)">
                          {{ a.analysis.scoreGeral | number:'1.1-1' }}
                        </span>
                        <div class="inline-bar-track">
                          <div class="inline-bar"
                            [style.width.%]="a.analysis.scoreGeral * 10"
                            [style.background]="scoreColor(a.analysis.scoreGeral)">
                          </div>
                        </div>
                      </td>
                    }
                  </tr>

                  <!-- Recomendação -->
                  <tr>
                    <td class="dim-label">Recomendação</td>
                    @for (a of r.analyses; track $index) {
                      <td class="center-cell">
                        <app-recommendation-badge [recommendation]="a.recommendation" />
                      </td>
                    }
                  </tr>

                  <!-- Dimension rows -->
                  @for (dim of DIMS; track dim.key) {
                    <tr>
                      <td class="dim-label">{{ dim.label }}</td>
                      @for (a of r.analyses; track $index) {
                        <td class="score-cell">
                          <span class="score-num" [style.color]="scoreColor(getDimScore(a, dim.key))">
                            {{ getDimScore(a, dim.key) | number:'1.1-1' }}
                          </span>
                          <div class="inline-bar-track">
                            <div class="inline-bar"
                              [style.width.%]="getDimScore(a, dim.key) * 10"
                              [style.background]="scoreColor(getDimScore(a, dim.key))">
                            </div>
                          </div>
                        </td>
                      }
                    </tr>
                  }

                  <!-- Resumo -->
                  <tr>
                    <td class="dim-label">Resumo</td>
                    @for (a of r.analyses; track $index) {
                      <td class="summary-cell">{{ a.simpleSummary }}</td>
                    }
                  </tr>

                </tbody>
              </table>
            </div>
          </div>

          <!-- ── Ranking ── -->
          <div class="ranking-card">
            <div class="ranking-hd">
              <span class="ranking-dot"></span>
              RANKING GERAL
            </div>
            <div class="ranking-list">
              @for (rank of r.ranking; track rank.position) {
                <div class="rank-row" [class.rank-top]="rank.position <= 3">
                  <div class="rank-num" [class]="medalClass(rank.position)">
                    {{ rank.position }}
                  </div>
                  <a class="rank-ticker" [routerLink]="['/analysis']" [queryParams]="{ticker: cleanTicker(rank.ticker)}">
                    {{ cleanTicker(rank.ticker) }}
                  </a>
                  <span class="rank-sector">{{ rank.sector }}</span>
                  <div class="rank-score-wrap">
                    <span class="rank-score" [style.color]="scoreColor(rank.scoreGeral)">
                      {{ rank.scoreGeral | number:'1.1-1' }}
                    </span>
                    <div class="rank-bar-track">
                      <div class="rank-bar"
                        [style.width.%]="rank.scoreGeral * 10"
                        [style.background]="scoreColor(rank.scoreGeral)">
                      </div>
                    </div>
                  </div>
                  <app-recommendation-badge [recommendation]="rank.recommendation" />
                  <span class="rank-summary">{{ rank.simpleSummary }}</span>
                </div>
              }
            </div>
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

    .cmp-shell { max-width: 1400px; margin: 0 auto; padding: 28px 40px; }

    .page-header {
      margin-bottom: 24px;
      h1 { font-family: var(--font-display); font-size: 24px; font-weight: 800; color: var(--text-primary); letter-spacing: -0.02em; }
      p  { color: var(--text-secondary); margin-top: 4px; font-size: 13px; }
    }

    /* ── Search card ── */
    .search-card {
      background: var(--surf);
      border: 1px solid var(--brd);
      border-radius: 8px;
      padding: 20px;
      box-shadow: 0 2px 20px rgba(0,0,0,0.4);
      margin-bottom: 20px;
    }

    .field-label {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 10px;
      font-weight: 700;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.1em;
      margin-bottom: 10px;
    }

    .ticker-count {
      background: var(--a-dim);
      color: var(--a);
      border: 1px solid rgba(0,212,170,0.2);
      border-radius: 10px;
      padding: 1px 7px;
      font-size: 9px;
      font-weight: 700;
    }

    .search-row { display: flex; gap: 10px; align-items: flex-start; }
    .select-wrap { flex: 1; }

    .btn-compare {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      padding: 9px 20px;
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

      &:hover { background: #00f0c2; box-shadow: 0 0 20px rgba(0,212,170,0.25); }
      &:disabled { opacity: 0.4; cursor: not-allowed; box-shadow: none; }
    }

    .hint-text { font-size: 11px; color: var(--text-muted); margin-top: 8px; }

    .error-box {
      background: rgba(239,68,68,0.08);
      border: 1px solid rgba(239,68,68,0.22);
      border-left: 3px solid #ef4444;
      color: #ef4444;
      border-radius: 6px;
      padding: 11px 16px;
      margin-bottom: 20px;
      font-size: 13px;
    }

    /* ── Highlights ── */
    .highlights {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;
      margin-bottom: 16px;
    }

    .hl-card {
      background: var(--surf);
      border: 1px solid var(--brd);
      border-radius: 8px;
      padding: 20px;
      text-align: center;
      box-shadow: 0 2px 16px rgba(0,0,0,0.35);
    }

    .hl-icon { margin-bottom: 10px; display: flex; justify-content: center; opacity: 0.7; }
    .hl-label { font-size: 9px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.1em; color: var(--text-muted); margin-bottom: 8px; }
    .hl-ticker { font-family: var(--font-mono); font-size: 22px; font-weight: 700; letter-spacing: 0.06em; }

    .hl-green { border-color: rgba(0,212,170,0.2); .hl-icon, .hl-ticker { color: var(--a); } }
    .hl-amber { border-color: rgba(245,158,11,0.2); .hl-icon, .hl-ticker { color: #f59e0b; } }
    .hl-blue  { border-color: rgba(96,165,250,0.2); .hl-icon, .hl-ticker { color: #60a5fa; } }

    /* ── Comparison table ── */
    .table-card {
      background: var(--surf);
      border: 1px solid var(--brd);
      border-radius: 8px;
      overflow: hidden;
      box-shadow: 0 2px 20px rgba(0,0,0,0.4);
      margin-bottom: 16px;
    }

    .table-scroll { overflow-x: auto; }

    .cmp-table {
      width: 100%;
      border-collapse: collapse;
      min-width: 500px;

      th, td {
        padding: 10px 14px;
        border-bottom: 1px solid rgba(255,255,255,0.05);
        font-size: 12px;
        text-align: center;
      }

      th {
        font-size: 9px;
        text-transform: uppercase;
        letter-spacing: 0.08em;
        color: var(--text-muted);
        font-weight: 600;
        background: rgba(255,255,255,0.02);
      }

      th.dim-col, td.dim-label { text-align: left; }
      tr:last-child td { border-bottom: none; }
      tr:hover td { background: rgba(255,255,255,0.015); }
    }

    .row-highlight td { background: rgba(0,212,170,0.03); }

    .dim-col { min-width: 140px; }
    .ticker-col { min-width: 120px; }

    .th-ticker {
      display: block;
      font-family: var(--font-mono);
      font-size: 14px;
      font-weight: 700;
      color: var(--a);
      text-decoration: none;
      letter-spacing: 0.05em;
      margin-bottom: 2px;
      &:hover { text-decoration: underline; }
    }

    .th-sector { font-size: 9px; color: var(--text-muted); font-weight: 400; text-transform: none; letter-spacing: 0; }
    .dim-label { color: var(--text-secondary); font-size: 12px; font-weight: 500; }
    .center-cell { text-align: center; }
    .summary-cell { font-size: 11px; color: var(--text-muted); max-width: 160px; line-height: 1.45; text-align: left; }

    /* ── Inline bars in table ── */
    .score-cell { text-align: center; padding-bottom: 8px !important; }

    .score-num {
      display: block;
      font-family: var(--font-mono);
      font-size: 14px;
      font-weight: 700;
      line-height: 1;
      margin-bottom: 5px;
    }

    .inline-bar-track {
      height: 4px;
      background: rgba(255,255,255,0.06);
      border-radius: 2px;
      overflow: hidden;
      margin: 0 auto;
      max-width: 80px;
    }

    .inline-bar { height: 100%; border-radius: 2px; transition: width 0.8s ease; }

    /* ── Ranking ── */
    .ranking-card {
      background: var(--surf);
      border: 1px solid var(--brd);
      border-radius: 8px;
      padding: 20px;
      box-shadow: 0 2px 20px rgba(0,0,0,0.4);
    }

    .ranking-hd {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 9px;
      font-weight: 700;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.12em;
      margin-bottom: 14px;
    }

    .ranking-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--a); }

    .ranking-list { display: flex; flex-direction: column; gap: 6px; }

    .rank-row {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 14px;
      background: rgba(255,255,255,0.02);
      border: 1px solid rgba(255,255,255,0.05);
      border-radius: 6px;
      font-size: 12px;
      transition: background 0.15s;

      &:hover { background: rgba(255,255,255,0.04); }
      &.rank-top { border-color: rgba(255,255,255,0.08); }
    }

    .rank-num {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-family: var(--font-mono);
      font-size: 12px;
      font-weight: 700;
      flex-shrink: 0;
      background: rgba(255,255,255,0.06);
      color: var(--text-muted);

      &.gold   { background: rgba(251,191,36,0.15); color: #fbbf24; }
      &.silver { background: rgba(148,163,184,0.15); color: #94a3b8; }
      &.bronze { background: rgba(217,119,6,0.15);  color: #d97706; }
    }

    .rank-ticker {
      font-family: var(--font-mono);
      font-size: 14px;
      font-weight: 700;
      color: var(--a);
      text-decoration: none;
      min-width: 52px;
      letter-spacing: 0.05em;
      &:hover { text-decoration: underline; }
    }

    .rank-sector { color: var(--text-muted); font-size: 11px; min-width: 80px; flex-shrink: 0; }

    .rank-score-wrap { display: flex; flex-direction: column; gap: 4px; min-width: 80px; }
    .rank-score { font-family: var(--font-mono); font-size: 14px; font-weight: 700; }
    .rank-bar-track { height: 3px; background: rgba(255,255,255,0.06); border-radius: 2px; overflow: hidden; }
    .rank-bar { height: 100%; border-radius: 2px; transition: width 0.8s ease; }

    .rank-summary { flex: 1; font-size: 11px; color: var(--text-muted); line-height: 1.4; }

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
    @keyframes fi { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }

    @media (max-width: 768px) {
      .cmp-shell { padding: 20px; }
      .highlights { grid-template-columns: 1fr; }
      .search-row { flex-direction: column; }
      .btn-compare { width: 100%; justify-content: center; }
    }
  `]
})
export class ComparePage {
  selectedTickers: string[] = [];
  result  = signal<ComparisonResult | null>(null);
  loading = signal(false);
  error   = signal('');

  readonly DIMS = [
    { key: 'fundamentos',           label: 'Fundamentos' },
    { key: 'valuation',             label: 'Valuation' },
    { key: 'regimeMomentum',        label: 'Momentum' },
    { key: 'sentimentoInstitucional', label: 'Institucional' },
    { key: 'retornoAcionista',      label: 'Dividendos' },
    { key: 'gestaoRisco',           label: 'Gestão de Risco' },
  ];

  constructor(private stockService: StockService) {}

  cleanTicker(t?: string | null): string { return (t ?? '').replace('.SA', ''); }
  scoreColor(s?: number): string { const n = s ?? 0; return n >= 6.5 ? '#00d4aa' : n >= 4 ? '#f59e0b' : '#ef4444'; }

  getDimScore(analysis: any, key: string): number {
    return analysis?.analysis?.[key]?.score ?? 0;
  }

  medalClass(pos: number): string {
    if (pos === 1) return 'gold';
    if (pos === 2) return 'silver';
    if (pos === 3) return 'bronze';
    return '';
  }

  compare() {
    if (this.selectedTickers.length < 2) { this.error.set('Selecione pelo menos 2 tickers.'); return; }
    if (this.selectedTickers.length > 5) { this.error.set('Máximo de 5 tickers.'); return; }

    this.loading.set(true);
    this.error.set('');
    this.stockService.compare(this.selectedTickers).subscribe({
      next: r => { this.result.set(r); this.loading.set(false); },
      error: e => { this.error.set(e?.error?.message || 'Erro ao comparar.'); this.loading.set(false); }
    });
  }
}
