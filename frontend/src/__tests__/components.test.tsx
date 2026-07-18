import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Header } from '../components/Header';
import { RefreshControl } from '../components/RefreshControl';
import { QuoteTable } from '../components/QuoteTable';
import { OrderList } from '../components/OrderList';
import type { QuoteTableRow, Order, RefreshInterval } from '../types';

describe('Header', () => {
  it('should show connected status', () => {
    render(<Header connected={true} />);
    expect(screen.getByText('已连接')).toBeInTheDocument();
  });

  it('should show disconnected status', () => {
    render(<Header connected={false} />);
    expect(screen.getByText('未连接')).toBeInTheDocument();
  });

  it('should render title', () => {
    render(<Header connected={true} />);
    expect(screen.getByText('做市商报价监控面板')).toBeInTheDocument();
  });
});

describe('RefreshControl', () => {
  it('should render all interval options', () => {
    const props = {
      interval: 5000 as RefreshInterval,
      onIntervalChange: vi.fn(),
      isPaused: false,
      onPause: vi.fn(),
      onResume: vi.fn(),
      onRefresh: vi.fn(),
    };
    render(<RefreshControl {...props} />);
    expect(screen.getByText('1s')).toBeInTheDocument();
    expect(screen.getByText('5s')).toBeInTheDocument();
    expect(screen.getByText('10s')).toBeInTheDocument();
    expect(screen.getByText('30s')).toBeInTheDocument();
  });

  it('should call onIntervalChange when clicking interval button', () => {
    const onIntervalChange = vi.fn();
    const props = {
      interval: 5000 as RefreshInterval,
      onIntervalChange,
      isPaused: false,
      onPause: vi.fn(),
      onResume: vi.fn(),
      onRefresh: vi.fn(),
    };
    render(<RefreshControl {...props} />);
    fireEvent.click(screen.getByText('1s'));
    expect(onIntervalChange).toHaveBeenCalledWith(1000);
  });

  it('should show pause/resume button', () => {
    const onPause = vi.fn();
    const props = {
      interval: 5000 as RefreshInterval,
      onIntervalChange: vi.fn(),
      isPaused: false,
      onPause,
      onResume: vi.fn(),
      onRefresh: vi.fn(),
    };
    render(<RefreshControl {...props} />);
    const pauseBtn = screen.getByText('暂停');
    fireEvent.click(pauseBtn);
    expect(onPause).toHaveBeenCalled();
  });
});

describe('QuoteTable', () => {
  const mockRows: QuoteTableRow[] = [
    {
      symbol: 'AU2406',
      exchangeBid: 520.5,
      exchangeAsk: 520.7,
      lastPrice: 520.6,
      bankBid: 520.3,
      bankAsk: 520.9,
      midPrice: 520.6,
      spreadBps: 11.5,
      change: 0.1,
      changePercent: 0.02,
      timestamp: '2024-01-01T10:00:00Z',
    },
  ];

  it('should render quote data', () => {
    render(<QuoteTable rows={mockRows} loading={false} />);
    expect(screen.getByText('AU2406')).toBeInTheDocument();
    expect(screen.getByText('520.50')).toBeInTheDocument();
    expect(screen.getByText('520.70')).toBeInTheDocument();
  });

  it('should show loading state', () => {
    render(<QuoteTable rows={[]} loading={true} />);
    expect(screen.getByText('加载行情数据中...')).toBeInTheDocument();
  });

  it('should show empty state', () => {
    render(<QuoteTable rows={[]} loading={false} />);
    expect(screen.getByText('暂无行情数据')).toBeInTheDocument();
  });

  it('should display bank quote prices', () => {
    render(<QuoteTable rows={mockRows} loading={false} />);
    expect(screen.getByText('520.30')).toBeInTheDocument();
    expect(screen.getByText('520.90')).toBeInTheDocument();
  });
});

describe('OrderList', () => {
  const mockOrders: Order[] = [
    {
      orderId: 'ORD-001-abc',
      clientOrderId: 'C001-001',
      customerId: 'C001',
      symbol: 'AU2406',
      side: 'BUY',
      type: 'MARKET',
      qty: 10,
      filledQty: 10,
      price: null,
      avgPrice: 520.6,
      status: 'FILLED',
      rejectReason: null,
      createdAt: '2024-01-01T10:00:00Z',
      updatedAt: '2024-01-01T10:00:01Z',
    },
  ];

  it('should render order data', () => {
    render(<OrderList orders={mockOrders} loading={false} onOrderChanged={vi.fn()} />);
    expect(screen.getByText('AU2406')).toBeInTheDocument();
    expect(screen.getByText('买')).toBeInTheDocument();
    expect(screen.getByText('FILLED')).toBeInTheDocument();
  });

  it('should show empty state', () => {
    render(<OrderList orders={[]} loading={false} onOrderChanged={vi.fn()} />);
    expect(screen.getByText('暂无订单')).toBeInTheDocument();
  });

  it('should show cancel button for cancellable orders', () => {
    const cancellableOrders: Order[] = [
      { ...mockOrders[0], status: 'NEW', orderId: 'ORD-002-xyz' },
    ];
    render(<OrderList orders={cancellableOrders} loading={false} onOrderChanged={vi.fn()} />);
    expect(screen.getByText('撤单')).toBeInTheDocument();
  });

  it('should not show cancel button for filled orders', () => {
    render(<OrderList orders={mockOrders} loading={false} onOrderChanged={vi.fn()} />);
    expect(screen.queryByText('撤单')).not.toBeInTheDocument();
  });
});
