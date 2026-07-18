import type { Order } from '../types';
import * as ordersApi from '../api/orders';

interface OrderListProps {
  orders: Order[];
  loading: boolean;
  onOrderChanged: () => void;
}

const STATUS_STYLES: Record<string, string> = {
  NEW: 'bg-blue-100 text-blue-700',
  PENDING_RISK: 'bg-amber-100 text-amber-700',
  ACCEPTED: 'bg-blue-100 text-blue-700',
  FILLED: 'bg-green-100 text-green-700',
  REJECTED: 'bg-red-100 text-red-700',
  CANCELLED: 'bg-slate-100 text-slate-500',
};

const CANCELLABLE_STATUSES = new Set(['NEW', 'PENDING_RISK', 'ACCEPTED']);

function formatTime(dateStr: string): string {
  try {
    return new Date(dateStr).toLocaleString('zh-CN', {
      hour12: false,
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch {
    return dateStr;
  }
}

export function OrderList({ orders, loading, onOrderChanged }: OrderListProps) {
  const handleCancel = async (orderId: string) => {
    try {
      await ordersApi.cancelOrder(orderId);
      onOrderChanged();
    } catch {
      // error handled silently for now
    }
  };

  if (loading && orders.length === 0) {
    return <div className="text-center py-8 text-slate-400">加载订单中...</div>;
  }

  if (orders.length === 0) {
    return <div className="text-center py-8 text-slate-400">暂无订单</div>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="bg-slate-100 text-slate-600">
            <th className="px-3 py-2 text-left font-semibold">订单ID</th>
            <th className="px-3 py-2 text-left font-semibold">合约</th>
            <th className="px-3 py-2 text-center font-semibold">方向</th>
            <th className="px-3 py-2 text-center font-semibold">类型</th>
            <th className="px-3 py-2 text-right font-semibold">数量</th>
            <th className="px-3 py-2 text-right font-semibold">成交</th>
            <th className="px-3 py-2 text-right font-semibold">价格</th>
            <th className="px-3 py-2 text-center font-semibold">状态</th>
            <th className="px-3 py-2 text-right font-semibold">时间</th>
            <th className="px-3 py-2 text-center font-semibold">操作</th>
          </tr>
        </thead>
        <tbody>
          {orders.map((order) => (
            <tr key={order.orderId} className="border-b border-slate-100 hover:bg-slate-50">
              <td className="px-3 py-2 font-mono text-xs">{order.orderId.slice(0, 12)}</td>
              <td className="px-3 py-2">{order.symbol}</td>
              <td className="px-3 py-2 text-center">
                <span className={order.side === 'BUY' ? 'text-red-600 font-medium' : 'text-green-600 font-medium'}>
                  {order.side === 'BUY' ? '买' : '卖'}
                </span>
              </td>
              <td className="px-3 py-2 text-center text-slate-500">{order.type}</td>
              <td className="px-3 py-2 text-right tabular-nums">{order.qty}</td>
              <td className="px-3 py-2 text-right tabular-nums">{order.filledQty}</td>
              <td className="px-3 py-2 text-right tabular-nums">
                {order.avgPrice ? order.avgPrice.toFixed(2) : order.price ? order.price.toFixed(2) : '-'}
              </td>
              <td className="px-3 py-2 text-center">
                <span className={`inline-block px-2 py-0.5 rounded text-xs font-medium ${STATUS_STYLES[order.status] || 'bg-slate-100 text-slate-500'}`}>
                  {order.status}
                </span>
              </td>
              <td className="px-3 py-2 text-right text-xs text-slate-400">
                {formatTime(order.createdAt)}
              </td>
              <td className="px-3 py-2 text-center">
                {CANCELLABLE_STATUSES.has(order.status) && (
                  <button
                    onClick={() => handleCancel(order.orderId)}
                    className="px-2 py-0.5 text-xs rounded bg-red-50 text-red-600 hover:bg-red-100 transition-colors"
                  >
                    撤单
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
