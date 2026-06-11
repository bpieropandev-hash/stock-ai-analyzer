import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Nav } from '../../shared/components/nav/nav';
import { StockService } from '../../core/services/stock.service';
import { StockAlert, StockQuote } from '../../core/models/models';

const SECTOR_COLORS = ['#00d4aa', '#818cf8', '#f59e0b', '#f472b6', '#34d399', '#60a5fa', '#fb923c', '#a78bfa'];

@Component({
  selector: 'app-dashboard',
  imports: [CommonModule, RouterLink, Nav],
  template: `
    <app-nav />

    <!-- ── Ticker tape ── -->
    <div class="ticker-bar">
      <div class="ticker-live">
        <span class="live-dot" [class.on]="wsConnected()"></span>
        <span class="live-text">{{ wsConnected() ? 'LIVE' : 'LOAD' }}</span>
      </div>
      <div class="ticker-viewport">
        <div class="ticker-track">
          @for (q of quotes(); track q.symbol) {
            <span class="ticker-item">
              <span class="ti-sym">{{ q.symbol.replace('.SA','') }}</span>
              <span class="ti-price">{{ q.price | number:'1.2-2' }}</span>
              <span class="ti-chg" [class.pos]="q.changePercent >= 0" [class.neg]="q.changePercent < 0">
                {{ q.changePercent >= 0 ? '▲' : '▼' }}&nbsp;{{ (q.changePercent < 0 ? -q.changePercent : q.changePercent) | number:'1.2-2' }}%
              </span>
            </span>
          }
          @for (q of quotes(); track 'b_' + q.symbol) {
            <span class="ticker-item" aria-hidden="true">
              <span class="ti-sym">{{ q.symbol.replace('.SA','') }}</span>
              <span class="ti-price">{{ q.price | number:'1.2-2' }}</span>
              <span class="ti-chg" [class.pos]="q.changePercent >= 0" [class.neg]="q.changePercent < 0">
                {{ q.changePercent >= 0 ? '▲' : '▼' }}&nbsp;{{ (q.changePercent < 0 ? -q.changePercent : q.changePercent) | number:'1.2-2' }}%
              </span>
            </span>
          }
        </div>
      </div>
    </div>

    <div class="dash-shell">

      <div class="page-header">
        <h1>Dashboard</h1>
        <p>Cotações B3 — atualização automática a cada 30 segundos</p>
      </div>

      <!-- ── Quote cards grid ── -->
      <div class="quotes-grid">
        @for (q of quotes(); track q.symbol) {
          <a class="qcard" [routerLink]="['/analysis']" [queryParams]="{ticker: q.symbol.replace('.SA','')}">
            <div class="qcard-top">
              <span class="qcard-dot" [style.background]="sectorColor(q.symbol)"></span>
              <span class="qcard-ticker">{{ q.symbol.replace('.SA','') }}</span>
              <span class="qcard-chg" [class.pos]="q.changePercent >= 0" [class.neg]="q.changePercent < 0">
                {{ q.changePercent >= 0 ? '+' : '' }}{{ q.changePercent | number:'1.2-2' }}%
              </span>
            </div>
            <div class="qcard-price">{{ q.price | number:'1.2-2' }}</div>
            <div class="qcard-meta">
              <span>Vol {{ formatVol(q.volume) }}</span>
              <span>{{ formatCap(q.marketCap) }}</span>
            </div>
            <div class="qcard-bar">
              <div class="qcard-bar-fill"
                [style.width.%]="changeWidth(q.changePercent)"
                [class.pos-fill]="q.changePercent >= 0"
                [class.neg-fill]="q.changePercent < 0">
              </div>
            </div>
          </a>
        }

        @if (loadingQuotes()) {
          @for (_ of [1,2,3,4,5]; track _) {
            <div class="qcard skeleton"></div>
          }
        }
      </div>

      <!-- ── Alerts ── -->
      @if (alerts().length > 0) {
        <section class="alerts-section fade-in">
          <div class="section-hd">
            <h2>Alertas de Score</h2>
            <span class="hd-tag">últimos 7 dias</span>
          </div>
          <div class="alerts-list">
            @for (a of alerts(); track a.createdAt) {
              <div class="alert-row" [class.alert-up]="a.direction === 'UP'" [class.alert-dn]="a.direction === 'DOWN'">
                <span class="alert-arrow" [class.pos]="a.direction === 'UP'" [class.neg]="a.direction === 'DOWN'">
                  {{ a.direction === 'UP' ? '↑' : '↓' }}
                </span>
                <span class="alert-ticker">{{ a.ticker.replace('.SA','') }}</span>
                <span class="alert-mag" [class.pos]="a.direction === 'UP'" [class.neg]="a.direction === 'DOWN'">
                  {{ a.direction === 'UP' ? '+' : '-' }}{{ a.magnitude | number:'1.1-1' }}pts
                </span>
                <span class="alert-flow">{{ a.scoreBefore | number:'1.1-1' }} → {{ a.scoreAfter | number:'1.1-1' }}</span>
                <span class="alert-date">{{ a.alertDate }}</span>
              </div>
            }
          </div>
        </section>
      }

    </div>
  `,
  styles: [`
    :host {
      --a:     #00d4aa;
      --a-dim: rgba(0, 212, 170, 0.1);
      --surf:  #0d1929;
    }

    /* ── Ticker bar ── */
    .ticker-bar {
      background: #060e1a;
      border-bottom: 1px solid rgba(255, 255, 255, 0.06);
      display: flex;
      align-items: center;
      height: 32px;
      overflow: hidden;
    }

    .ticker-live {
      display: flex;
      align-items: center;
      gap: 5px;
      padding: 0 14px;
      border-right: 1px solid rgba(255, 255, 255, 0.06);
      height: 100%;
      flex-shrink: 0;
      min-width: 64px;
    }

    .live-dot {
      width: 5px;
      height: 5px;
      border-radius: 50%;
      background: var(--text-muted);
      transition: background 0.4s;

      &.on {
        background: var(--a);
        box-shadow: 0 0 6px var(--a);
        animation: pulse-dot 2s ease-in-out infinite;
      }
    }

    @keyframes pulse-dot { 0%, 100% { opacity: 1; } 50% { opacity: 0.45; } }

    .live-text {
      font-family: var(--font-mono);
      font-size: 9px;
      font-weight: 700;
      letter-spacing: 0.12em;
      color: var(--text-muted);
    }

    .ticker-viewport {
      flex: 1;
      overflow: hidden;
      position: relative;

      &::before,
      &::after {
        content: '';
        position: absolute;
        top: 0; bottom: 0;
        width: 28px;
        z-index: 2;
        pointer-events: none;
      }
      &::before { left: 0; background: linear-gradient(to right, #060e1a, transparent); }
      &::after  { right: 0; background: linear-gradient(to left, #060e1a, transparent); }
    }

    .ticker-track {
      display: flex;
      align-items: center;
      width: max-content;
      animation: ticker-scroll 50s linear infinite;
      &:hover { animation-play-state: paused; }
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
      white-space: nowrap;
      border-right: 1px solid rgba(255, 255, 255, 0.05);
      height: 32px;
    }

    .ti-sym   { font-family: var(--font-mono); font-size: 11px; font-weight: 700; color: var(--text-primary); letter-spacing: 0.06em; }
    .ti-price { font-family: var(--font-mono); font-size: 11px; color: var(--text-secondary); }
    .ti-chg   { font-family: var(--font-mono); font-size: 10px; font-weight: 700; }

    /* ── Shell ── */
    .dash-shell { max-width: 1400px; margin: 0 auto; padding: 28px 40px; }

    .page-header {
      margin-bottom: 24px;
      h1 { font-family: var(--font-display); font-size: 24px; font-weight: 800; color: var(--text-primary); letter-spacing: -0.02em; }
      p  { color: var(--text-secondary); margin-top: 4px; font-size: 13px; }
    }

    /* ── 5-col grid ── */
    .quotes-grid {
      display: grid;
      grid-template-columns: repeat(5, 1fr);
      gap: 12px;
      margin-bottom: 36px;
    }

    @media (max-width: 1280px) { .quotes-grid { grid-template-columns: repeat(4, 1fr); } }
    @media (max-width: 960px)  { .quotes-grid { grid-template-columns: repeat(3, 1fr); } }
    @media (max-width: 640px)  { .quotes-grid { grid-template-columns: repeat(2, 1fr); } }
    @media (max-width: 400px)  { .quotes-grid { grid-template-columns: 1fr; } }

    /* ── Stock card 110px ── */
    .qcard {
      position: relative;
      background: var(--surf);
      border: 1px solid rgba(255, 255, 255, 0.07);
      border-radius: 8px;
      padding: 14px 14px 8px;
      height: 110px;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      text-decoration: none;
      overflow: hidden;
      transition: border-color 0.2s, box-shadow 0.2s, transform 0.18s;

      &:hover {
        border-color: var(--a);
        box-shadow: 0 0 20px rgba(0, 212, 170, 0.1);
        transform: translateY(-2px);
      }
    }

    .qcard-top {
      display: flex;
      align-items: center;
      gap: 7px;
    }

    .qcard-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      flex-shrink: 0;
    }

    .qcard-ticker {
      font-family: var(--font-display);
      font-size: 13px;
      font-weight: 700;
      color: var(--text-primary);
      letter-spacing: 0.02em;
      flex: 1;
    }

    .qcard-chg {
      font-family: var(--font-mono);
      font-size: 11px;
      font-weight: 700;
    }

    .qcard-price {
      font-family: var(--font-mono);
      font-size: 22px;
      font-weight: 700;
      color: var(--text-primary);
      line-height: 1;
      letter-spacing: -0.02em;
    }

    .qcard-meta {
      display: flex;
      justify-content: space-between;
      font-size: 10px;
      color: var(--text-muted);
      font-family: var(--font-mono);
    }

    .qcard-bar {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      height: 2px;
      background: rgba(255, 255, 255, 0.04);
    }

    .qcard-bar-fill { height: 100%; transition: width 0.6s ease; }
    .pos-fill { background: var(--a); }
    .neg-fill { background: #ef4444; }

    .skeleton {
      background: linear-gradient(90deg, var(--surf) 0%, rgba(255,255,255,0.03) 50%, var(--surf) 100%);
      background-size: 200% 100%;
      animation: shimmer 1.6s infinite;
    }

    @keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

    /* ── Alerts ── */
    .alerts-section {
      padding-top: 24px;
      border-top: 1px solid rgba(255, 255, 255, 0.06);
    }

    .section-hd {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 14px;
      h2 { font-family: var(--font-display); font-size: 15px; font-weight: 700; color: var(--text-primary); }
    }

    .hd-tag {
      font-size: 10px;
      color: var(--text-muted);
      background: rgba(255,255,255,0.04);
      border: 1px solid rgba(255,255,255,0.07);
      border-radius: 20px;
      padding: 2px 9px;
      text-transform: uppercase;
      letter-spacing: 0.06em;
    }

    /* Compact row list */
    .alerts-list {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .alert-row {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 9px 14px;
      background: var(--surf);
      border: 1px solid rgba(255, 255, 255, 0.06);
      border-left: 2px solid transparent;
      border-radius: 6px;
      font-size: 12px;

      &.alert-up { border-left-color: var(--a); }
      &.alert-dn { border-left-color: #ef4444; }
    }

    .alert-arrow { font-size: 14px; font-weight: 800; flex-shrink: 0; width: 16px; text-align: center; }
    .alert-ticker { font-family: var(--font-mono); font-size: 13px; font-weight: 700; color: var(--text-primary); letter-spacing: 0.04em; min-width: 52px; }
    .alert-mag { font-family: var(--font-mono); font-size: 12px; font-weight: 700; min-width: 64px; }
    .alert-flow { font-family: var(--font-mono); font-size: 11px; color: var(--text-secondary); flex: 1; }
    .alert-date { font-size: 10px; color: var(--text-muted); flex-shrink: 0; }

    /* ── Color helpers ── */
    .pos { color: var(--a); }
    .neg { color: #ef4444; }

    .fade-in { animation: fi 0.35s ease-out; }
    @keyframes fi { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
  `]
})
export class DashboardPage implements OnInit, OnDestroy {
  quotes        = signal<StockQuote[]>([]);
  alerts        = signal<StockAlert[]>([]);
  loadingQuotes = signal(true);
  wsConnected   = signal(false);

  constructor(private stockService: StockService) {}

  ngOnInit() {
    this.stockService.getQuotes().subscribe({
      next: qs => { this.quotes.set(qs); this.loadingQuotes.set(false); },
      error: ()  => this.loadingQuotes.set(false)
    });
    this.stockService.getAlerts().subscribe({ next: a => this.alerts.set(a), error: () => {} });
    this.stockService.connectWebSocket(q => {
      this.wsConnected.set(true);
      this.quotes.update(qs => {
        const idx = qs.findIndex(x => x.symbol === q.symbol);
        if (idx >= 0) { const next = [...qs]; next[idx] = q; return next; }
        return [...qs, q];
      });
    });
  }

  ngOnDestroy() { this.stockService.disconnectWebSocket(); }

  sectorColor(symbol: string): string {
    const s = symbol.replace('.SA', '');
    let h = 0;
    for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) & 0xffff;
    return SECTOR_COLORS[h % SECTOR_COLORS.length];
  }

  changeWidth(change: number): number { return Math.min(Math.abs(change) * 10, 100); }

  formatVol(v: number): string {
    if (!v) return '—';
    if (v >= 1e9) return `${(v / 1e9).toFixed(1)}B`;
    if (v >= 1e6) return `${(v / 1e6).toFixed(1)}M`;
    if (v >= 1e3) return `${(v / 1e3).toFixed(0)}K`;
    return v.toString();
  }

  formatCap(cap: number): string {
    if (!cap) return '—';
    if (cap >= 1e12) return `${(cap / 1e12).toFixed(1)}T`;
    if (cap >= 1e9)  return `${(cap / 1e9).toFixed(1)}B`;
    if (cap >= 1e6)  return `${(cap / 1e6).toFixed(1)}M`;
    return cap.toString();
  }
}
