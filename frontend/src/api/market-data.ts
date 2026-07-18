import { apiGet, MARKET_DATA_BASE } from './client';
import type { MarketData } from '../types';

export function getAllMarketData(): Promise<MarketData[]> {
  return apiGet<MarketData[]>(MARKET_DATA_BASE, '/marketdata');
}

export function getMarketData(symbol: string): Promise<MarketData> {
  return apiGet<MarketData>(MARKET_DATA_BASE, `/marketdata/${symbol}`);
}
