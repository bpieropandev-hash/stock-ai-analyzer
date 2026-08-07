export interface StockQuote {
  symbol: string;
  price: number;
  changePercent: number;
  volume: number;
  marketCap: number;
}

export interface ScoreDimension {
  score: number;
  explicacao: string;
}

export interface ScoreDetails {
  scoreGeral: number;
  resumo?: string;
  fundamentos: ScoreDimension;
  valuation: ScoreDimension;
  regimeMomentum: ScoreDimension;
  sentimentoInstitucional: ScoreDimension;
  retornoAcionista: ScoreDimension;
  gestaoRisco: ScoreDimension;
}

export interface ScoreConfidence {
  overall: number;
  fundamentalsQuality: number;
  sentimentQuality: number;
  technicalDataAvailable: boolean;
  sectorBenchmarkDynamic: boolean;
}

export interface AnalysisResponse {
  ticker: string;
  analysis: ScoreDetails;
  recommendation: string;
  simpleSummary: string;
  sector: string;
  modelUsed?: string;
  promptVersion?: string;
  confidence?: ScoreConfidence;
  disclaimer?: string;
}

export interface BacktestEntry {
  analysisDate: string;
  scoreGeral: number;
  modelUsed: string;
  promptVersion: string;
  priceAtAnalysis: number | null;
  return30dPct: number | null;
  return90dPct: number | null;
}

export interface BacktestResult {
  ticker: string;
  totalAnalyses: number;
  analysesWithForwardData: number;
  correlationScore30d: number | null;
  correlationScore90d: number | null;
  entries: BacktestEntry[];
}

export interface PortfolioItemResponse {
  ticker: string;
  quantity: number;
  averagePrice: number;
  purchaseDate: string;
  scoreGeral: number;
  recommendation: string;
  simpleSummary: string;
  sector: string;
}

export interface PortfolioItemSaved {
  id: string;
  ticker: string;
  quantity: number;
  averagePrice: number;
  purchaseDate: string;
}

export interface AddPortfolioItemRequest {
  ticker: string;
  quantity: number;
  averagePrice: number;
  purchaseDate: string | null;
}

export interface EvaluationItem {
  ticker: string;
  quantity: number;
  averagePrice: number;
  scoreGeral: number;
  action: string;
  simpleSummary: string;
}

export interface Allocation {
  ticker: string;
  sector: string;
  amount: number;
  percentage: number;
  scoreGeral: number;
  recommendation: string;
  simpleSummary: string;
}

export interface SimulationResult {
  totalAmount: number;
  allocations: Allocation[];
  excludedTickers: string[];
  generatedAt: string;
}

export interface SimulateRequest {
  amount: number;
  tickers: string[];
}

export interface RankedStock {
  position: number;
  ticker: string;
  scoreGeral: number;
  recommendation: string;
  simpleSummary: string;
  sector: string;
}

export interface ComparisonResult {
  tickers: string[];
  analyses: AnalysisResponse[];
  ranking: RankedStock[];
  bestForDividends: string;
  bestMomentum: string;
  lowestRisk: string;
  generatedAt: string;
}

export interface StockAlert {
  ticker: string;
  alertDate: string;
  scoreBefore: number;
  scoreAfter: number;
  direction: string;
  magnitude: number;
  createdAt: string;
}
