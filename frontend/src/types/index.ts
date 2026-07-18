export interface MarketData {
  symbol: string;
  bidPrice: number;
  askPrice: number;
  lastPrice: number;
  timestamp: string;
}

export interface CustomerQuote {
  symbol: string;
  customerBidPrice: number;
  customerAskPrice: number;
  midPrice: number;
  spreadBps: number;
  customerLevel: string;
  timestamp: string;
}

export interface Order {
  orderId: string;
  clientOrderId: string;
  customerId: string;
  symbol: string;
  side: 'BUY' | 'SELL';
  type: 'MARKET' | 'LIMIT';
  qty: number;
  filledQty: number;
  price: number | null;
  avgPrice: number | null;
  status: 'NEW' | 'PENDING_RISK' | 'ACCEPTED' | 'FILLED' | 'REJECTED' | 'CANCELLED';
  rejectReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateOrderRequest {
  customerId: string;
  symbol: string;
  side: 'BUY' | 'SELL';
  type: 'MARKET' | 'LIMIT';
  qty: number;
  price?: number;
  clientOrderId?: string;
}

export interface RfqRequest {
  symbol: string;
  customerId: string;
  side: 'BUY' | 'SELL';
  qty: number;
}

export type RefreshInterval = 1000 | 5000 | 10000 | 30000;

export interface QuoteTableRow {
  symbol: string;
  exchangeBid: number;
  exchangeAsk: number;
  lastPrice: number;
  bankBid: number | null;
  bankAsk: number | null;
  midPrice: number | null;
  spreadBps: number | null;
  change: number;
  changePercent: number;
  timestamp: string;
}
