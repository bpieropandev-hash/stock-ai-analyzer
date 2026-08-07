import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AnalysisResponse, BacktestResult, ComparisonResult, StockAlert, StockQuote } from '../models/models';
import { environment } from '../../../environments/environment';

const API = `${environment.apiUrl}`;
const POLL_INTERVAL_MS = 30_000;

@Injectable({ providedIn: 'root' })
export class StockService implements OnDestroy {
  private pollInterval: ReturnType<typeof setInterval> | null = null;

  constructor(private http: HttpClient) {}

  private normalizeTicker(ticker: string): string {
    return ticker.replace(/\.SA$/i, '').trim().toUpperCase();
  }

  getQuotes(): Observable<StockQuote[]> {
    return this.http.get<StockQuote[]>(`${API}/stocks`);
  }

  analyze(ticker: string): Observable<AnalysisResponse> {
    return this.http.get<AnalysisResponse>(`${API}/stocks/${this.normalizeTicker(ticker)}/analysis`);
  }

  refreshAnalysis(ticker: string): Observable<AnalysisResponse> {
    return this.http.post<AnalysisResponse>(`${API}/stocks/${this.normalizeTicker(ticker)}/analysis/refresh`, {});
  }

  compare(tickers: string[]): Observable<ComparisonResult> {
    let params = new HttpParams();
    tickers.forEach(t => { params = params.append('tickers', this.normalizeTicker(t)); });
    return this.http.get<ComparisonResult>(`${API}/compare`, { params });
  }

  getAlerts(days = 7): Observable<StockAlert[]> {
    return this.http.get<StockAlert[]>(`${API}/alerts?days=${days}`);
  }

  getBacktest(ticker: string): Observable<BacktestResult> {
    return this.http.get<BacktestResult>(`${API}/stocks/${this.normalizeTicker(ticker)}/backtest`);
  }

  /* WebSocket foi substituído por polling HTTP a cada 30s devido ao endpoint
     /ws retornar 404 (backend STOMP não acessível via SockJS nesta config). */
  connectWebSocket(onQuote: (quote: StockQuote) => void): void {
    this.pollInterval = setInterval(() => {
      this.http.get<StockQuote[]>(`${API}/stocks`).subscribe({
        next: quotes => quotes.forEach(q => onQuote(q)),
        error: () => {}
      });
    }, POLL_INTERVAL_MS);
  }

  disconnectWebSocket(): void {
    if (this.pollInterval !== null) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }
  }

  ngOnDestroy(): void {
    this.disconnectWebSocket();
  }
}
