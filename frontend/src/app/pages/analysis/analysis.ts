import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { Nav } from '../../shared/components/nav/nav';
import { RecommendationBadge } from '../../shared/components/recommendation-badge/recommendation-badge';
import { TickerSelect } from '../../shared/components/ticker-select/ticker-select';
import { StockService } from '../../core/services/stock.service';
import { AnalysisResponse, StockQuote } from '../../core/models/models';

const R    = 90;
const CIRC = 2 * Math.PI * R;

@Component({
  selector: 'app-analysis',
  imports: [CommonModule, Nav, RecommendationBadge, TickerSelect],
  template: `
    <!-- ── Ticker bar 32px ── -->
    @if (quotes().length) {
      <div class="ticker-bar">
        <div class="ticker-track">
          @for (q of quotes(); track q.symbol + 'a') {
            <span class="ticker-item">
              <span class="t-sym">{{ q.symbol.replace('.SA','') }}</span>
              <span class="t-price">R$&nbsp;{{ q.price | number:'1.2-2' }}</span>
              <span class="t-chg" [class.chg-pos]="q.changePercent >= 0" [class.chg-neg]="q.changePercent < 0">
                {{ q.changePercent >= 0 ? '+' : '' }}{{ q.changePercent | number:'1.2-2' }}%
              </span>
            </span>
          }
          @for (q of quotes(); track q.symbol + 'b') {
            <span class="ticker-item">
              <span class="t-sym">{{ q.symbol.replace('.SA','') }}</span>
              <span class="t-price">R$&nbsp;{{ q.price | number:'1.2-2' }}</span>
              <span class="t-chg" [class.chg-pos]="q.changePercent >= 0" [class.chg-neg]="q.changePercent < 0">
                {{ q.changePercent >= 0 ? '+' : '' }}{{ q.changePercent | number:'1.2-2' }}%
              </span>
            </span>
          }
        </div>
      </div>
    }

    <!-- ── Navbar ── -->
    <app-nav />

    <!-- ── Page container ── -->
    <div class="page-ct">

      <!-- Search row -->
      <div class="search-row">
        <div class="ticker-select-wrap">
          <app-ticker-select
            [(value)]="tickerInput"
            placeholder="PETR4, VALE3, ITUB4..."
            (enter)="analyze()" />
        </div>

        <button class="btn-analyze" (click)="analyze()" [disabled]="loading()">
          @if (loading()) {
            <span class="spinner"></span>
          } @else {
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
              <circle cx="11" cy="11" r="8"/>
              <line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
            Analisar
          }
        </button>

        @if (result()) {
          <button class="btn-refresh" (click)="refresh()" [disabled]="loading()">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
              <polyline points="1 4 1 10 7 10"/>
              <path d="M3.51 15a9 9 0 1 0 .49-3.51"/>
            </svg>
            Atualizar
          </button>
        }

        @if (result()?.sector) {
          <span class="sector-pill">{{ result()!.sector }}</span>
        }
      </div>

      <!-- Error -->
      @if (error()) {
        <div class="error-box">{{ error() }}</div>
      }

      <!-- Loading -->
      @if (loading()) {
        <div class="loading-card">
          <span class="spinner" style="width:28px;height:28px;border-width:3px;flex-shrink:0"></span>
          <div>
            <div class="loading-title">Analisando <strong>{{ tickerInput.toUpperCase() }}</strong></div>
            <div class="loading-sub">Buscando dados e gerando score com IA… pode levar até 30s</div>
          </div>
        </div>
      }

      <!-- Result -->
      @if (!loading() && result(); as r) {
        @if (r.analysis) {
          <div class="main-grid fade-in">

            <!-- ── Left 320px ── -->
            <div class="col-left">

              <div class="gauge-card">
                <div class="card-meta">
                  <div>
                    <div class="ticker-label">{{ (r.ticker || '').replace('.SA','') }}</div>
                    @if (r.modelUsed) {
                      <div class="model-tag">{{ r.modelUsed }}</div>
                    }
                  </div>
                </div>

                <div class="gauge-wrap">
                  <svg viewBox="0 0 200 200" class="gauge-svg">
                    <defs>
                      <filter id="g-glow" x="-50%" y="-50%" width="200%" height="200%">
                        <feGaussianBlur stdDeviation="4" result="b"/>
                        <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
                      </filter>
                    </defs>
                    @for (tick of ticks; track tick) {
                      <line class="g-tick"
                        x1="100" y1="12" x2="100" y2="24"
                        [attr.transform]="'rotate(' + (tick * 36) + ' 100 100)'"/>
                    }
                    <circle cx="100" cy="100" r="90" class="g-bg"/>
                    <circle cx="100" cy="100" r="90" class="g-arc"
                      filter="url(#g-glow)"
                      [style.stroke]="ringColor(r.analysis.scoreGeral)"
                      [style.stroke-dasharray]="CIRC"
                      [style.stroke-dashoffset]="dashOffset(ringScore())"/>
                  </svg>
                  <div class="gauge-overlay">
                    <span class="g-num" [style.color]="ringColor(r.analysis.scoreGeral)">
                      {{ r.analysis.scoreGeral | number:'1.1-1' }}
                    </span>
                    <span class="g-denom">/10</span>
                    <span class="g-tag">SCORE GERAL</span>
                  </div>
                </div>

                <div class="badge-row">
                  <app-recommendation-badge [recommendation]="r.recommendation" [large]="true"/>
                </div>
              </div>

              <div class="summary-card">
                <div class="summary-hd">
                  <span class="summary-dot"></span>
                  <span class="summary-label">Resumo para investidor</span>
                </div>
                <p class="summary-body">{{ r.simpleSummary }}</p>
              </div>

            </div>

            <!-- ── Right: 6 dimensions ── -->
            <div class="col-right">
              <div class="dims-card">
                <div class="dims-hd">
                  <span class="dims-title">DIMENSÕES DO SCORE</span>
                  <span class="dims-scale">0 ──── 5 ──── 10</span>
                </div>

                @for (dim of buildDims(r); track dim.label) {
                  <div class="dim-row">
                    <div class="dim-header">
                      <span class="dim-label">{{ dim.label }}</span>
                      <div class="dim-track">
                        <div class="dim-fill"
                          [style.width.%]="dim.score * 10"
                          [style.background]="barColor(dim.score)"
                          [style.box-shadow]="'0 0 8px ' + barGlow(dim.score)">
                        </div>
                      </div>
                      <span class="dim-score" [style.color]="barColor(dim.score)">
                        {{ dim.score | number:'1.1-1' }}
                      </span>
                    </div>
                    @if (dim.expl) {
                      <p class="dim-expl">{{ dim.expl }}</p>
                    }
                  </div>
                }

              </div>
            </div>

          </div>

          @if (r.disclaimer) {
            <div class="disclaimer">
              <div class="disc-divider"></div>
              <p class="disc-text">{{ r.disclaimer }}</p>
            </div>
          }
        }
      }

      <!-- Empty state -->
      @if (!loading() && !result() && !error()) {
        <div class="empty-state">
          <svg width="52" height="52" viewBox="0 0 20 20" fill="none">
            <polyline points="1,15 6,8 10,12 19,2"
              stroke="currentColor" stroke-width="1.3"
              stroke-linecap="round" stroke-linejoin="round" opacity="0.2"/>
          </svg>
          <p>Digite um ticker da B3 para começar a análise</p>
        </div>
      }

    </div>
  `,
  styles: [`
    :host {
      --a:      #00d4aa;
      --a-dim:  rgba(0, 212, 170, 0.1);
      --a-glow: rgba(0, 212, 170, 0.25);
      --surf:   #0d1929;
      --brd:    rgba(255, 255, 255, 0.07);
    }

    /* ── Ticker bar ── */
    .ticker-bar {
      height: 32px;
      background: #060e1a;
      border-bottom: 1px solid rgba(255, 255, 255, 0.05);
      overflow: hidden;
      display: flex;
      align-items: center;
    }

    .ticker-track {
      display: flex;
      align-items: center;
      white-space: nowrap;
      animation: ticker-scroll 40s linear infinite;
    }

    @keyframes ticker-scroll {
      0%   { transform: translateX(0); }
      100% { transform: translateX(-50%); }
    }

    .ticker-item {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      padding: 0 20px;
      border-right: 1px solid rgba(255, 255, 255, 0.05);
      font-size: 11px;
    }

    .t-sym  { font-family: var(--font-mono); font-weight: 700; color: var(--text-primary); letter-spacing: 0.07em; }
    .t-price { color: var(--text-secondary); }
    .t-chg  { font-family: var(--font-mono); font-weight: 600; }
    .chg-pos { color: var(--a); }
    .chg-neg { color: #ef4444; }

    /* ── Page ── */
    .page-ct { max-width: 1400px; margin: 0 auto; padding: 32px 40px; }

    /* ── Search row ── */
    .search-row {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 28px;
      flex-wrap: wrap;
    }

    .ticker-select-wrap { width: 280px; flex-shrink: 0; }

    .btn-analyze {
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
      flex-shrink: 0;

      &:hover { background: #00f0c2; box-shadow: 0 0 20px var(--a-glow); }
      &:disabled { opacity: 0.45; cursor: not-allowed; box-shadow: none; }
    }

    .btn-refresh {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      padding: 9px 16px;
      background: transparent;
      color: var(--text-secondary);
      border: 1px solid rgba(255,255,255,0.12);
      border-radius: 6px;
      font-size: 13px;
      cursor: pointer;
      transition: all 0.18s;
      flex-shrink: 0;

      &:hover { border-color: var(--a); color: var(--a); }
      &:disabled { opacity: 0.4; cursor: not-allowed; }
    }

    .sector-pill {
      display: inline-flex;
      align-items: center;
      padding: 4px 14px;
      background: var(--a-dim);
      border: 1px solid rgba(0, 212, 170, 0.2);
      border-radius: 20px;
      font-size: 11px;
      font-weight: 600;
      color: var(--a);
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }

    /* ── Error / Loading ── */
    .error-box {
      background: rgba(239, 68, 68, 0.08);
      border: 1px solid rgba(239, 68, 68, 0.22);
      border-left: 3px solid #ef4444;
      color: #ef4444;
      border-radius: 6px;
      padding: 11px 16px;
      margin-bottom: 20px;
      font-size: 13px;
    }

    .loading-card {
      display: flex;
      align-items: center;
      gap: 20px;
      background: var(--surf);
      border: 1px solid var(--brd);
      border-radius: 8px;
      padding: 28px 24px;
      box-shadow: 0 2px 20px rgba(0, 0, 0, 0.5);
      margin-bottom: 20px;
    }

    .loading-title { font-size: 14px; color: var(--text-primary); margin-bottom: 4px; strong { color: var(--a); font-family: var(--font-mono); } }
    .loading-sub { font-size: 12px; color: var(--text-muted); }

    .spinner {
      display: inline-block;
      width: 16px;
      height: 16px;
      border: 2px solid rgba(255,255,255,0.12);
      border-top-color: var(--a);
      border-radius: 50%;
      animation: spin 0.65s linear infinite;
      flex-shrink: 0;
    }

    @keyframes spin { to { transform: rotate(360deg); } }

    /* ── Main grid ── */
    .main-grid { display: grid; grid-template-columns: 320px 1fr; gap: 20px; align-items: start; }
    .col-left { display: flex; flex-direction: column; gap: 16px; width: 320px; flex-shrink: 0; }
    .col-right { min-width: 0; }

    /* ── Gauge card ── */
    .gauge-card {
      background: var(--surf);
      border: 1px solid var(--brd);
      border-radius: 8px;
      padding: 20px;
      box-shadow: 0 2px 24px rgba(0,0,0,0.5);
      text-align: center;
    }

    .card-meta { display: flex; justify-content: flex-start; align-items: flex-start; text-align: left; margin-bottom: 8px; }
    .ticker-label { font-family: var(--font-mono); font-size: 26px; font-weight: 700; color: var(--text-primary); letter-spacing: 0.08em; line-height: 1; }
    .model-tag { font-family: var(--font-mono); font-size: 9px; color: var(--text-muted); background: rgba(255,255,255,0.04); border: 1px solid var(--brd); border-radius: 4px; padding: 2px 7px; display: inline-block; margin-top: 6px; }

    .gauge-wrap { position: relative; width: 200px; height: 200px; margin: 8px auto 14px; }
    .gauge-svg { width: 200px; height: 200px; transform: rotate(-90deg); }
    .g-tick { stroke: rgba(255,255,255,0.08); stroke-width: 1.5; stroke-linecap: round; }
    .g-bg { fill: none; stroke: rgba(255,255,255,0.05); stroke-width: 14; }
    .g-arc { fill: none; stroke-width: 14; stroke-linecap: round; transition: stroke-dashoffset 1.4s cubic-bezier(0.4,0,0.2,1); }

    .gauge-overlay { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; pointer-events: none; }
    .g-num { font-family: var(--font-display); font-size: 52px; font-weight: 800; line-height: 1; }
    .g-denom { font-family: var(--font-mono); font-size: 15px; color: var(--text-muted); margin-top: 2px; }
    .g-tag { font-size: 8px; letter-spacing: 0.14em; color: var(--text-muted); margin-top: 8px; text-transform: uppercase; }
    .badge-row { margin-top: 4px; }

    /* ── Summary card ── */
    .summary-card {
      background: var(--surf);
      border: 1px solid var(--brd);
      border-left: 2px solid var(--a);
      border-radius: 8px;
      padding: 18px 20px;
      box-shadow: 0 2px 24px rgba(0,0,0,0.5);
    }

    .summary-hd { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
    .summary-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--a); flex-shrink: 0; }
    .summary-label { font-size: 9px; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.1em; }
    .summary-body { font-size: 13px; color: var(--text-secondary); line-height: 1.75; }

    /* ── Dimensions card ── */
    .dims-card {
      background: var(--surf);
      border: 1px solid var(--brd);
      border-radius: 8px;
      padding: 24px;
      box-shadow: 0 2px 24px rgba(0,0,0,0.5);
      box-sizing: border-box;
    }

    .dims-hd { display: flex; justify-content: space-between; align-items: center; padding-bottom: 16px; margin-bottom: 4px; border-bottom: 1px solid rgba(255,255,255,0.06); }
    .dims-title { font-size: 9px; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.12em; }
    .dims-scale { font-family: var(--font-mono); font-size: 9px; color: var(--text-dim); letter-spacing: 0.04em; }

    .dim-row { padding: 14px 0; border-bottom: 1px solid rgba(255,255,255,0.04); &:last-child { border-bottom: none; } }
    .dim-header { display: flex; align-items: center; gap: 12px; }
    .dim-label { width: 160px; flex-shrink: 0; font-size: 11px; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.05em; white-space: nowrap; }
    .dim-track { flex: 1; height: 8px; background: rgba(255,255,255,0.05); border-radius: 4px; overflow: hidden; min-width: 0; }
    .dim-fill { height: 100%; border-radius: 4px; transition: width 1s cubic-bezier(0.4,0,0.2,1); }
    .dim-score { width: 36px; flex-shrink: 0; text-align: right; font-family: var(--font-mono); font-size: 15px; font-weight: 700; }
    .dim-expl { font-size: 12px; color: var(--text-muted); line-height: 1.65; margin-top: 6px; padding-left: calc(160px + 12px); }

    /* ── Disclaimer / Empty ── */
    .disclaimer { margin-top: 32px; }
    .disc-divider { height: 1px; background: rgba(255,255,255,0.06); margin-bottom: 14px; }
    .disc-text { font-size: 11px; color: var(--text-muted); line-height: 1.65; }

    .empty-state { text-align: center; padding: 80px 24px; color: var(--text-secondary); p { font-size: 14px; margin-top: 14px; } }

    .fade-in { animation: fi 0.35s ease-out; }
    @keyframes fi { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }

    @media (max-width: 960px) {
      .page-ct { padding: 24px; }
      .main-grid { grid-template-columns: 1fr; }
      .col-left { width: 100%; }
    }
    @media (max-width: 600px) {
      .page-ct { padding: 16px; }
      .dim-label { width: 110px; font-size: 10px; }
      .dim-expl { padding-left: calc(110px + 12px); }
    }
  `]
})
export class AnalysisPage implements OnInit {
  readonly CIRC = CIRC;

  tickerInput = '';
  result      = signal<AnalysisResponse | null>(null);
  loading     = signal(false);
  error       = signal('');
  ringScore   = signal(0);
  quotes      = signal<StockQuote[]>([]);
  ticks       = Array.from({ length: 10 }, (_, i) => i);

  constructor(private stockService: StockService, private route: ActivatedRoute) {}

  ngOnInit() {
    const t = this.route.snapshot.queryParamMap.get('ticker');
    if (t) { this.tickerInput = t; this.analyze(); }
    this.stockService.getQuotes().subscribe({ next: q => this.quotes.set(q), error: () => {} });
  }

  analyze() {
    const t = this.tickerInput.trim().toUpperCase();
    if (!t) return;
    this.loading.set(true);
    this.error.set('');
    this.result.set(null);
    this.ringScore.set(0);
    this.stockService.analyze(t).subscribe({
      next: r => {
        this.result.set(r);
        this.loading.set(false);
        setTimeout(() => this.ringScore.set(r.analysis?.scoreGeral ?? 0), 120);
      },
      error: e => {
        this.error.set(e?.error?.message || 'Erro ao analisar. Verifique o ticker e tente novamente.');
        this.loading.set(false);
      }
    });
  }

  refresh() {
    const t = this.tickerInput.trim().toUpperCase();
    if (!t) return;
    this.loading.set(true);
    this.result.set(null);
    this.ringScore.set(0);
    this.stockService.refreshAnalysis(t).subscribe({
      next: r => {
        this.result.set(r);
        this.loading.set(false);
        setTimeout(() => this.ringScore.set(r.analysis?.scoreGeral ?? 0), 120);
      },
      error: () => this.analyze()
    });
  }

  dashOffset(score: number): number { return CIRC - (score / 10) * CIRC; }
  barColor(s: number) { return s >= 6.5 ? '#00d4aa' : s >= 4 ? '#f59e0b' : '#ef4444'; }
  barGlow(s: number)  { return s >= 6.5 ? 'rgba(0,212,170,0.4)' : s >= 4 ? 'rgba(245,158,11,0.4)' : 'rgba(239,68,68,0.4)'; }
  ringColor(s: number){ return s >= 6.5 ? '#00d4aa' : s >= 4 ? '#f59e0b' : '#ef4444'; }

  buildDims(r: AnalysisResponse) {
    const a = r.analysis;
    return [
      { label: 'Fundamentos',          score: a.fundamentos.score,             expl: a.fundamentos.explicacao },
      { label: 'Valuation',            score: a.valuation.score,               expl: a.valuation.explicacao },
      { label: 'Regime / Momentum',    score: a.regimeMomentum.score,          expl: a.regimeMomentum.explicacao },
      { label: 'Sent. Institucional',  score: a.sentimentoInstitucional.score, expl: a.sentimentoInstitucional.explicacao },
      { label: 'Retorno ao Acionista', score: a.retornoAcionista.score,        expl: a.retornoAcionista.explicacao },
      { label: 'Gestão de Risco',      score: a.gestaoRisco.score,             expl: a.gestaoRisco.explicacao },
    ];
  }
}
