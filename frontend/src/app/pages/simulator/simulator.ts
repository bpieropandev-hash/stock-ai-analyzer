import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Nav } from '../../shared/components/nav/nav';
import { RecommendationBadge } from '../../shared/components/recommendation-badge/recommendation-badge';
import { TickerSelect } from '../../shared/components/ticker-select/ticker-select';
import { SimulationResult } from '../../core/models/models';

@Component({
  selector: 'app-simulator',
  imports: [CommonModule, FormsModule, Nav, RecommendationBadge, TickerSelect],
  template: `
    <app-nav />

    <div class="sim-shell">

      <div class="page-header">
        <h1>Simulador de Alocação</h1>
        <p>Distribua um valor entre ações com base no score de IA</p>
      </div>

      <!-- ── Form ── -->
      <div class="sim-form">
        <div class="form-top">
          <div class="amount-group">
            <label class="field-label">Valor a Investir</label>
            <div class="amount-wrap">
              <span class="amount-prefix">R$</span>
              <input class="field-input mono-input" type="number" [(ngModel)]="amount" placeholder="10000" />
            </div>
          </div>
          <div class="tickers-group">
            <label class="field-label">
              Ações
              @if (selectedTickers.length > 0) {
                <span class="ticker-count">{{ selectedTickers.length }} selecionada(s)</span>
              } @else {
                <span class="ticker-hint">vazio = padrões B3</span>
              }
            </label>
            <app-ticker-select
              [multi]="true"
              [max]="10"
              [(values)]="selectedTickers"
              placeholder="Adicionar ticker..." />
          </div>
          <button class="btn-sim" (click)="simulate()" [disabled]="loading() || !amount">
            @if (loading()) {
              <span class="spinner"></span>
              Simulando…
            } @else {
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
                <polygon points="5 3 19 12 5 21 5 3"/>
              </svg>
              Simular
            }
          </button>
        </div>
      </div>

      <!-- Loading notice -->
      @if (loading()) {
        <div class="loading-notice">
          <span class="spinner lg-spinner"></span>
          <div>
            <strong>Analisando ações em tempo real…</strong>
            <p>O simulador busca dados via yfinance. Pode levar 1–3 minutos dependendo do número de tickers.</p>
          </div>
        </div>
      }

      @if (error()) {
        <div class="error-box">{{ error() }}</div>
      }

      <!-- ── Results ── -->
      @if (result(); as r) {
        <div class="result-section fade-in">

          <!-- Summary bar -->
          <div class="result-summary">
            <div class="rs-main">
              <span class="rs-label">TOTAL ALOCADO</span>
              <span class="rs-amount">R$ {{ r.totalAmount | number:'1.2-2' }}</span>
            </div>
            <div class="rs-stats">
              <div class="rs-stat">
                <span class="rs-num">{{ r.allocations.length }}</span>
                <span class="rs-desc">ações</span>
              </div>
              @if (r.excludedTickers.length > 0) {
                <span class="rs-excl">{{ r.excludedTickers.length }} excluída(s)</span>
              }
            </div>
          </div>

          <!-- Allocation cards 4-col -->
          <div class="alloc-grid">
            @for (a of r.allocations; track a.ticker) {
              <div class="alloc-card">
                <div class="ac-head">
                  <div class="ac-left">
                    <div class="ac-ticker">{{ cleanTicker(a.ticker) }}</div>
                    <div class="ac-sector">{{ a.sector }}</div>
                  </div>
                  <div class="ac-pct" [style.color]="accentColor(a.percentage)">
                    {{ a.percentage | number:'1.1-1' }}%
                  </div>
                </div>

                <div class="ac-bar-track">
                  <div class="ac-bar"
                    [style.width.%]="a.percentage"
                    [style.background]="accentColor(a.percentage)">
                  </div>
                </div>

                <div class="ac-body">
                  <span class="ac-amount">R$ {{ a.amount | number:'1.2-2' }}</span>
                  <div class="ac-score" [style.color]="scoreColor(a.scoreGeral)">
                    {{ a.scoreGeral | number:'1.1-1' }}
                  </div>
                  <app-recommendation-badge [recommendation]="a.recommendation" />
                </div>

                @if (a.simpleSummary) {
                  <p class="ac-summary">{{ a.simpleSummary }}</p>
                }
              </div>
            }
          </div>

          <!-- Excluded -->
          @if (r.excludedTickers.length > 0) {
            <div class="excl-section">
              <span class="excl-label">EXCLUÍDAS — VENDER ou sem dados suficientes</span>
              <div class="excl-tags">
                @for (t of r.excludedTickers; track t) {
                  <span class="excl-tag">{{ cleanTicker(t) }}</span>
                }
              </div>
            </div>
          }

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

    .sim-shell { max-width: 1400px; margin: 0 auto; padding: 28px 40px; }

    .page-header {
      margin-bottom: 24px;
      h1 { font-family: var(--font-display); font-size: 24px; font-weight: 800; color: var(--text-primary); letter-spacing: -0.02em; }
      p  { color: var(--text-secondary); margin-top: 4px; font-size: 13px; }
    }

    /* ── Form ── */
    .sim-form {
      background: var(--surf);
      border: 1px solid var(--brd);
      border-radius: 8px;
      padding: 20px;
      box-shadow: 0 2px 20px rgba(0,0,0,0.4);
      margin-bottom: 20px;
    }

    .form-top {
      display: flex;
      gap: 12px;
      align-items: flex-end;
      flex-wrap: wrap;
    }

    .amount-group { flex: 0 0 160px; display: flex; flex-direction: column; }
    .tickers-group { flex: 1; min-width: 220px; display: flex; flex-direction: column; }

    .field-label {
      font-size: 10px;
      font-weight: 600;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.08em;
      margin-bottom: 5px;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .ticker-count {
      background: var(--a-dim);
      color: var(--a);
      border: 1px solid rgba(0,212,170,0.2);
      border-radius: 10px;
      padding: 1px 7px;
      font-size: 9px;
      font-weight: 700;
      letter-spacing: 0.04em;
    }

    .ticker-hint { color: var(--text-dim); font-weight: 400; text-transform: none; letter-spacing: 0; }

    .amount-wrap { position: relative; }
    .amount-prefix { position: absolute; left: 11px; top: 50%; transform: translateY(-50%); font-family: var(--font-mono); font-size: 12px; color: var(--text-muted); pointer-events: none; }

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
    }

    .mono-input { padding-left: 30px; font-family: var(--font-mono); }

    .btn-sim {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      padding: 9px 22px;
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

    /* ── Loading / Error ── */
    .loading-notice {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      padding: 16px 20px;
      background: var(--surf);
      border: 1px solid rgba(0,212,170,0.2);
      border-radius: 8px;
      margin-bottom: 20px;

      strong { display: block; color: var(--a); font-size: 13px; margin-bottom: 4px; }
      p { font-size: 12px; color: var(--text-secondary); line-height: 1.55; margin: 0; }
    }

    .lg-spinner { width: 22px !important; height: 22px !important; border-width: 2px !important; margin-top: 2px; flex-shrink: 0; }

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

    /* ── Results ── */
    .result-summary {
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: var(--surf);
      border: 1px solid var(--brd);
      border-radius: 8px;
      padding: 18px 22px;
      margin-bottom: 16px;
      box-shadow: 0 2px 20px rgba(0,0,0,0.35);
    }

    .rs-main { display: flex; flex-direction: column; gap: 3px; }
    .rs-label { font-size: 9px; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.1em; }
    .rs-amount { font-family: var(--font-mono); font-size: 28px; font-weight: 700; color: var(--a); line-height: 1; }

    .rs-stats { display: flex; align-items: center; gap: 16px; }
    .rs-stat { text-align: right; }
    .rs-num { display: block; font-family: var(--font-mono); font-size: 22px; font-weight: 700; color: var(--text-primary); line-height: 1; }
    .rs-desc { font-size: 10px; color: var(--text-muted); }
    .rs-excl { background: rgba(239,68,68,0.08); border: 1px solid rgba(239,68,68,0.2); color: #ef4444; border-radius: 4px; padding: 4px 10px; font-size: 11px; font-weight: 600; }

    /* ── Allocation cards 4-col ── */
    .alloc-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 12px;
      margin-bottom: 16px;
    }

    @media (max-width: 1200px) { .alloc-grid { grid-template-columns: repeat(2, 1fr); } }
    @media (max-width: 600px)  { .alloc-grid { grid-template-columns: 1fr; } }

    .alloc-card {
      background: var(--surf);
      border: 1px solid var(--brd);
      border-radius: 8px;
      padding: 16px;
      box-shadow: 0 2px 16px rgba(0,0,0,0.35);
      transition: border-color 0.18s, transform 0.18s;

      &:hover { border-color: var(--a); transform: translateY(-2px); }
    }

    .ac-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
    .ac-left {}
    .ac-ticker { font-family: var(--font-mono); font-size: 20px; font-weight: 700; color: var(--text-primary); letter-spacing: 0.06em; line-height: 1; }
    .ac-sector { font-size: 10px; color: var(--text-muted); margin-top: 3px; }
    .ac-pct { font-family: var(--font-mono); font-size: 24px; font-weight: 700; }

    .ac-bar-track {
      height: 4px;
      background: rgba(255,255,255,0.05);
      border-radius: 2px;
      overflow: hidden;
      margin-bottom: 12px;
    }

    .ac-bar { height: 100%; border-radius: 2px; transition: width 0.9s ease; box-shadow: 2px 0 10px currentColor; }

    .ac-body { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
    .ac-amount { font-family: var(--font-mono); font-size: 13px; font-weight: 600; color: var(--text-primary); flex: 1; }
    .ac-score  { font-family: var(--font-mono); font-size: 14px; font-weight: 700; }
    .ac-summary { font-size: 11px; color: var(--text-muted); line-height: 1.55; }

    /* ── Excluded ── */
    .excl-section {
      background: var(--surf);
      border: 1px solid var(--brd);
      border-radius: 8px;
      padding: 16px 18px;
      box-shadow: 0 2px 16px rgba(0,0,0,0.35);
    }

    .excl-label {
      display: block;
      font-size: 9px;
      font-weight: 700;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.1em;
      margin-bottom: 10px;
    }

    .excl-tags { display: flex; flex-wrap: wrap; gap: 6px; }

    .excl-tag {
      font-family: var(--font-mono);
      font-size: 11px;
      font-weight: 700;
      background: rgba(239,68,68,0.08);
      color: #ef4444;
      border: 1px solid rgba(239,68,68,0.22);
      border-radius: 4px;
      padding: 3px 9px;
      letter-spacing: 0.05em;
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
    @keyframes fi { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }

    @media (max-width: 768px) { .sim-shell { padding: 20px; } .form-top { flex-direction: column; } .amount-group, .tickers-group { flex: 1; width: 100%; } }
  `]
})
export class SimulatorPage {
  amount           = 10000;
  selectedTickers: string[] = [];
  result           = signal<SimulationResult | null>(null);
  loading          = signal(false);
  error            = signal('');

  constructor(private http: HttpClient) {}

  cleanTicker(t?: string | null): string { return (t ?? '').replace('.SA', ''); }

  scoreColor(s: number): string { return s >= 6.5 ? '#00d4aa' : s >= 4 ? '#f59e0b' : '#ef4444'; }

  accentColor(pct: number): string {
    if (pct >= 25) return '#00d4aa';
    if (pct >= 15) return '#f59e0b';
    return '#60a5fa';
  }

  simulate() {
    this.loading.set(true);
    this.error.set('');
    this.result.set(null);
    this.http.post<SimulationResult>('http://localhost:8080/api/simulate', {
      amount: this.amount,
      tickers: this.selectedTickers
    }).subscribe({
      next: r => { this.result.set(r); this.loading.set(false); },
      error: e => {
        this.error.set(e?.error?.message || 'Erro ao simular. O backend pode estar buscando dados — tente novamente em instantes.');
        this.loading.set(false);
      }
    });
  }
}
