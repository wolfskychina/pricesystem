import { useState, useCallback, useMemo } from 'react';
import type { MarketData, CustomerQuote, Order, RefreshInterval, QuoteTableRow } from '../types';
import { usePolling } from '../hooks/usePolling';
import * as marketDataApi from '../api/market-data';
import * as pricingApi from '../api/pricing';
import * as ordersApi from '../api/orders';
import { Header } from '../components/Header';
import { RefreshControl } from '../components/RefreshControl';
import { QuoteTable } from '../components/QuoteTable';
import { OrderPanel } from '../components/OrderPanel';
import { OrderList } from '../components/OrderList';

export function Dashboard() {
  const [refreshInterval, setRefreshInterval] = useState<RefreshInterval>(5000);
  const [activeTab, setActiveTab] = useState<'order' | 'orders'>('order');
  const [, setOrderRefreshKey] = useState(0);

  // Poll market data
  const fetchMarketData = useCallback(() => marketDataApi.getAllMarketData(), []);
  const {
    data: marketDataList,
    loading: mdLoading,
    error: mdError,
    isPaused: mdPaused,
    pause: mdPause,
    resume: mdResume,
    refresh: mdRefresh,
  } = usePolling<MarketData[]>(fetchMarketData, refreshInterval);

  // Poll orders
  const fetchOrders = useCallback(() => ordersApi.getOrders(), []);
  const {
    data: ordersList,
    loading: ordersLoading,
    refresh: ordersRefresh,
  } = usePolling<Order[]>(fetchOrders, refreshInterval);

  // Fetch quotes for each symbol
  const symbols = useMemo(
    () => (marketDataList || []).map((md) => md.symbol),
    [marketDataList]
  );

  const fetchAllQuotes = useCallback(async (): Promise<CustomerQuote[]> => {
    if (!marketDataList || marketDataList.length === 0) return [];
    const results = await Promise.allSettled(
      marketDataList.map((md) => pricingApi.getQuote(md.symbol))
    );
    return results
      .filter((r): r is PromiseFulfilledResult<CustomerQuote> => r.status === 'fulfilled')
      .map((r) => r.value);
  }, [marketDataList]);

  const { data: quotesList } = usePolling<CustomerQuote[]>(fetchAllQuotes, refreshInterval, symbols.length > 0);

  // Merge market data and quotes into table rows
  const tableRows: QuoteTableRow[] = useMemo(() => {
    if (!marketDataList) return [];
    const quoteMap = new Map<string, CustomerQuote>();
    if (quotesList) {
      for (const q of quotesList) {
        quoteMap.set(q.symbol, q);
      }
    }

    return marketDataList.map((md) => {
      const quote = quoteMap.get(md.symbol);
      const midPrice = (md.bidPrice + md.askPrice) / 2;
      const change = md.lastPrice - midPrice;
      const changePercent = midPrice !== 0 ? (change / midPrice) * 100 : 0;

      return {
        symbol: md.symbol,
        exchangeBid: md.bidPrice,
        exchangeAsk: md.askPrice,
        lastPrice: md.lastPrice,
        bankBid: quote?.customerBidPrice ?? null,
        bankAsk: quote?.customerAskPrice ?? null,
        midPrice: quote?.midPrice ?? null,
        spreadBps: quote?.spreadBps ?? null,
        change,
        changePercent,
        timestamp: md.timestamp,
      };
    });
  }, [marketDataList, quotesList]);

  const connected = !mdError && (marketDataList !== null);

  const handleOrderCreated = useCallback(() => {
    ordersRefresh();
    setOrderRefreshKey((k) => k + 1);
  }, [ordersRefresh]);

  return (
    <div className="min-h-screen flex flex-col">
      <Header connected={connected} />

      <main className="flex-1 flex flex-col p-4 gap-4">
        {/* Toolbar */}
        <div className="flex items-center justify-between">
          <RefreshControl
            interval={refreshInterval}
            onIntervalChange={setRefreshInterval}
            isPaused={mdPaused}
            onPause={mdPause}
            onResume={mdResume}
            onRefresh={mdRefresh}
          />
          {mdError && (
            <span className="text-xs text-red-500 bg-red-50 px-2 py-1 rounded">
              行情连接错误: {mdError}
            </span>
          )}
        </div>

        {/* Quote Table - main area */}
        <div className="flex-1 bg-white rounded-lg shadow-sm border border-slate-200 overflow-hidden">
          <div className="px-4 py-2.5 border-b border-slate-200 bg-slate-50">
            <h2 className="text-sm font-semibold text-slate-700">行情报价</h2>
          </div>
          <QuoteTable rows={tableRows} loading={mdLoading} />
        </div>

        {/* Bottom panel: Order / Orders */}
        <div className="bg-white rounded-lg shadow-sm border border-slate-200">
          <div className="flex border-b border-slate-200">
            <button
              onClick={() => setActiveTab('order')}
              className={`px-4 py-2 text-sm font-medium transition-colors ${
                activeTab === 'order'
                  ? 'text-blue-600 border-b-2 border-blue-600 bg-blue-50/50'
                  : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              下单
            </button>
            <button
              onClick={() => setActiveTab('orders')}
              className={`px-4 py-2 text-sm font-medium transition-colors ${
                activeTab === 'orders'
                  ? 'text-blue-600 border-b-2 border-blue-600 bg-blue-50/50'
                  : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              订单列表
            </button>
          </div>
          <div className="p-4">
            {activeTab === 'order' ? (
              <OrderPanel symbols={symbols} onOrderCreated={handleOrderCreated} />
            ) : (
              <OrderList
                orders={ordersList || []}
                loading={ordersLoading}
                onOrderChanged={ordersRefresh}
              />
            )}
          </div>
        </div>
      </main>
    </div>
  );
}
