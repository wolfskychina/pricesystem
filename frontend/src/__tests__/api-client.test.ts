import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiGet, apiPost, apiDelete } from '../api/client';

describe('API client', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  describe('apiGet', () => {
    it('should fetch and parse JSON response', async () => {
      const mockData = [{ symbol: 'AU2406', bidPrice: 520.5 }];
      vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockData),
      } as Response);

      const result = await apiGet('/api/market-data', '/marketdata');
      expect(result).toEqual(mockData);
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/market-data/marketdata');
    });

    it('should throw on non-ok response', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      } as Response);

      await expect(apiGet('/api', '/test')).rejects.toThrow('API error: 500');
    });
  });

  describe('apiPost', () => {
    it('should POST with JSON body', async () => {
      const body = { symbol: 'AU2406', qty: 10 };
      const mockResponse = { orderId: '123' };
      vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response);

      const result = await apiPost('/api/oms', '/orders', body);
      expect(result).toEqual(mockResponse);
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/oms/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
    });

    it('should throw on error with response text', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: false,
        status: 400,
        statusText: 'Bad Request',
        text: () => Promise.resolve('Invalid order'),
      } as Response);

      await expect(apiPost('/api', '/test', {})).rejects.toThrow('API error: 400');
    });
  });

  describe('apiDelete', () => {
    it('should send DELETE request', async () => {
      const mockResponse = { status: 'CANCELLED' };
      vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response);

      const result = await apiDelete('/api/oms', '/orders/123');
      expect(result).toEqual(mockResponse);
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/oms/orders/123', {
        method: 'DELETE',
      });
    });
  });
});
