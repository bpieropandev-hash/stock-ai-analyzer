import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AddPortfolioItemRequest, EvaluationItem,
  PortfolioItemResponse, PortfolioItemSaved, SimulationResult
} from '../models/models';

const API = 'http://localhost:8080/api/portfolio';

@Injectable({ providedIn: 'root' })
export class PortfolioService {
  constructor(private http: HttpClient) {}

  getPortfolio(): Observable<PortfolioItemResponse[]> {
    return this.http.get<PortfolioItemResponse[]>(API);
  }

  addOrUpdate(req: AddPortfolioItemRequest): Observable<PortfolioItemSaved> {
    return this.http.post<PortfolioItemSaved>(API, req);
  }

  remove(ticker: string): Observable<void> {
    return this.http.delete<void>(`${API}/${ticker}`);
  }

  evaluate(): Observable<EvaluationItem[]> {
    return this.http.get<EvaluationItem[]>(`${API}/evaluation`);
  }

  suggestAllocation(amount: number): Observable<SimulationResult> {
    return this.http.post<SimulationResult>(`${API}/suggest-allocation`, { amount });
  }
}
