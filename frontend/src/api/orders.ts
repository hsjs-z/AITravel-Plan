import http from './http'
import type { CreateOrderPayload, Order } from './types'

export function createOrder(payload: CreateOrderPayload): Promise<Order> {
  return http.post('/orders', payload)
}

export function listOrders(): Promise<Order[]> {
  return http.get('/orders')
}

export function getOrder(orderId: string): Promise<Order> {
  return http.get(`/orders/${orderId}`)
}

export function cancelOrder(orderId: string): Promise<Order> {
  return http.post(`/orders/${orderId}/cancel`)
}
