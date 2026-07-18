import { apiGet, apiPost, apiDelete, OMS_BASE } from './client';
import type { Order, CreateOrderRequest } from '../types';

export function createOrder(req: CreateOrderRequest): Promise<Order> {
  return apiPost<Order>(OMS_BASE, '/orders', req);
}

export function getOrders(): Promise<Order[]> {
  return apiGet<Order[]>(OMS_BASE, '/orders');
}

export function getOrder(orderId: string): Promise<Order> {
  return apiGet<Order>(OMS_BASE, `/orders/${orderId}`);
}

export function cancelOrder(orderId: string): Promise<Order> {
  return apiDelete<Order>(OMS_BASE, `/orders/${orderId}`);
}
