import http from './http'
import type { AttractionPage, ResourceDetail, SearchResult } from './types'

export interface ResourceQuery {
  type?: 'TRADABLE' | 'CONTENT' | 'ATTRACTION'
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

export interface AttractionQuery {
  destination: string
  date?: string
  limit?: number
  offset?: number
}

export function searchAttractions(params: AttractionQuery): Promise<AttractionPage> {
  return http.get('/inventory/attractions', { params })
}
