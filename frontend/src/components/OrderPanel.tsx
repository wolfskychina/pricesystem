import { useState } from 'react';
import type { CreateOrderRequest } from '../types';
import * as ordersApi from '../api/orders';

interface OrderPanelProps {
  symbols: string[];
  onOrderCreated: () => void;
}

export function OrderPanel({ symbols, onOrderCreated }: OrderPanelProps) {
  const [symbol, setSymbol] = useState(symbols[0] || '');
  const [side, setSide] = useState<'BUY' | 'SELL'>('BUY');
  const [orderType, setOrderType] = useState<'MARKET' | 'LIMIT'>('MARKET');
  const [qty, setQty] = useState('1');
  const [price, setPrice] = useState('');
  const [customerId, setCustomerId] = useState('C001');
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setMessage(null);

    const req: CreateOrderRequest = {
      customerId,
      symbol,
      side,
      type: orderType,
      qty: parseFloat(qty),
      ...(orderType === 'LIMIT' && price ? { price: parseFloat(price) } : {}),
    };

    try {
      await ordersApi.createOrder(req);
      setMessage({ type: 'success', text: `下单成功: ${side} ${qty} ${symbol}` });
      onOrderCreated();
    } catch (err) {
      setMessage({
        type: 'error',
        text: err instanceof Error ? err.message : '下单失败',
      });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-xs text-slate-500 mb-1">客户ID</label>
          <input
            type="text"
            value={customerId}
            onChange={(e) => setCustomerId(e.target.value)}
            className="w-full px-2.5 py-1.5 border border-slate-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
        <div>
          <label className="block text-xs text-slate-500 mb-1">合约</label>
          <select
            value={symbol}
            onChange={(e) => setSymbol(e.target.value)}
            className="w-full px-2.5 py-1.5 border border-slate-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
          >
            {symbols.map((s) => (
              <option key={s} value={s}>{s}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs text-slate-500 mb-1">方向</label>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => setSide('BUY')}
              className={`flex-1 px-3 py-1.5 rounded text-sm font-medium transition-colors ${
                side === 'BUY'
                  ? 'bg-red-500 text-white'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              买入
            </button>
            <button
              type="button"
              onClick={() => setSide('SELL')}
              className={`flex-1 px-3 py-1.5 rounded text-sm font-medium transition-colors ${
                side === 'SELL'
                  ? 'bg-green-500 text-white'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              卖出
            </button>
          </div>
        </div>
        <div>
          <label className="block text-xs text-slate-500 mb-1">类型</label>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => setOrderType('MARKET')}
              className={`flex-1 px-3 py-1.5 rounded text-sm font-medium transition-colors ${
                orderType === 'MARKET'
                  ? 'bg-blue-500 text-white'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              市价
            </button>
            <button
              type="button"
              onClick={() => setOrderType('LIMIT')}
              className={`flex-1 px-3 py-1.5 rounded text-sm font-medium transition-colors ${
                orderType === 'LIMIT'
                  ? 'bg-blue-500 text-white'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              限价
            </button>
          </div>
        </div>
        <div>
          <label className="block text-xs text-slate-500 mb-1">数量</label>
          <input
            type="number"
            step="any"
            min="0"
            value={qty}
            onChange={(e) => setQty(e.target.value)}
            className="w-full px-2.5 py-1.5 border border-slate-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
        {orderType === 'LIMIT' && (
          <div>
            <label className="block text-xs text-slate-500 mb-1">价格</label>
            <input
              type="number"
              step="any"
              min="0"
              value={price}
              onChange={(e) => setPrice(e.target.value)}
              className="w-full px-2.5 py-1.5 border border-slate-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
              required
            />
          </div>
        )}
      </div>
      <button
        type="submit"
        disabled={submitting}
        className="w-full py-2 bg-blue-600 text-white rounded font-medium hover:bg-blue-700 disabled:bg-slate-400 transition-colors"
      >
        {submitting ? '提交中...' : '提交下单'}
      </button>
      {message && (
        <div
          className={`text-sm px-3 py-1.5 rounded ${
            message.type === 'success'
              ? 'bg-green-50 text-green-700'
              : 'bg-red-50 text-red-700'
          }`}
        >
          {message.text}
        </div>
      )}
    </form>
  );
}
