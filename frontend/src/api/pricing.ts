import { apiGet, apiPost, PRICING_BASE } from './client';
import type { CustomerQuote, RfqRequest } from '../types';

export function getQuote(symbol: string): Promise<CustomerQuote> {
  return apiGet<CustomerQuote>(PRICING_BASE, `/quotes/${symbol}`);
}

export function requestRfq(req: RfqRequest): Promise<CustomerQuote> {
  return apiPost<CustomerQuote>(PRICING_BASE, '/quotes/rfq', req);
}
