import http from './http'
import type { ResourceDetail, SearchResult } from './types'

export interface ResourceQuery {
  type?: 'TRADABLE' | 'CONTENT'
  destination?: string
  keyword?: string
  category?: string
  sort?: string
}

export function searchResources(params: ResourceQuery): Promise<SearchResult> {
  return http.get('/resources/search', { params })
}

export function getResource(resourceId: string, date?: string): Promise<ResourceDetail> {
  return http.get(`/resources/${resourceId}`, { params: date ? { date } : undefined })
}
