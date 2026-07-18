import type { QuoteTableRow } from '../types';

interface QuoteTableProps {
  rows: QuoteTableRow[];
  loading: boolean;
}

function formatPrice(value: number | null, decimals: number = 2): string {
  if (value === null || value === undefined) return '-';
  return value.toFixed(decimals);
}

function ChangeCell({ value, percent }: { value: number; percent: number }) {
  if (value === 0) return <span className="text-slate-400">0.00</span>;
  const isPositive = value > 0;
  const color = isPositive ? 'text-green-600' : 'text-red-600';
  const sign = isPositive ? '+' : '';
  return (
    <span className={color}>
      {sign}{value.toFixed(2)} ({sign}{percent.toFixed(2)}%)
    </span>
  );
}

export function QuoteTable({ rows, loading }: QuoteTableProps) {
  if (loading && rows.length === 0) {
    return (
      <div className="text-center py-12 text-slate-400">加载行情数据中...</div>
    );
  }

  if (rows.length === 0) {
    return (
      <div className="text-center py-12 text-slate-400">暂无行情数据</div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="bg-slate-100 text-slate-600">
            <th className="px-3 py-2.5 text-left font-semibold">合约代码</th>
            <th className="px-3 py-2.5 text-right font-semibold">交易所买价</th>
            <th className="px-3 py-2.5 text-right font-semibold">交易所卖价</th>
            <th className="px-3 py-2.5 text-right font-semibold">最新价</th>
            <th className="px-3 py-2.5 text-right font-semibold font-bold text-blue-700">银行买价</th>
            <th className="px-3 py-2.5 text-right font-semibold font-bold text-blue-700">银行卖价</th>
            <th className="px-3 py-2.5 text-right font-semibold">中间价</th>
            <th className="px-3 py-2.5 text-right font-semibold">价差(bps)</th>
            <th className="px-3 py-2.5 text-right font-semibold">涨跌</th>
            <th className="px-3 py-2.5 text-right font-semibold">更新时间</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={row.symbol}
              className="border-b border-slate-100 hover:bg-blue-50 transition-colors"
            >
              <td className="px-3 py-2 font-medium text-slate-800">{row.symbol}</td>
              <td className="px-3 py-2 text-right tabular-nums text-red-600">{formatPrice(row.exchangeBid)}</td>
              <td className="px-3 py-2 text-right tabular-nums text-green-600">{formatPrice(row.exchangeAsk)}</td>
              <td className="px-3 py-2 text-right tabular-nums font-medium">{formatPrice(row.lastPrice)}</td>
              <td className="px-3 py-2 text-right tabular-nums font-bold text-blue-700 bg-blue-50/50">
                {formatPrice(row.bankBid)}
              </td>
              <td className="px-3 py-2 text-right tabular-nums font-bold text-blue-700 bg-blue-50/50">
                {formatPrice(row.bankAsk)}
              </td>
              <td className="px-3 py-2 text-right tabular-nums">{formatPrice(row.midPrice)}</td>
              <td className="px-3 py-2 text-right tabular-nums text-slate-500">
                {row.spreadBps !== null ? row.spreadBps.toFixed(1) : '-'}
              </td>
              <td className="px-3 py-2 text-right tabular-nums">
                <ChangeCell value={row.change} percent={row.changePercent} />
              </td>
              <td className="px-3 py-2 text-right text-slate-400 text-xs">
                {row.timestamp ? new Date(row.timestamp).toLocaleTimeString('zh-CN') : '-'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
