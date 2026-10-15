export type ResourceType = 'TRADABLE' | 'CONTENT'

export interface SpecView {
  specId: string
  specName: string
  specType: string | null
  occupancy: number | null
  price: number | null
  available: number | null
  bookable: boolean
}

export interface ResourceSummary {
  id: string
  type: ResourceType
  name: string
  destination: string
  status: string
  tradable: boolean
  orderEntryAllowed: boolean
  priceFrom: number | null
  priceTo: number | null
  availableTotal: number | null
  contentCategory: string | null
}

export interface ResourceDetail {
  id: string
  type: ResourceType
  name: string
  destination: string
  status: string
  tradable: boolean
  soldOut: boolean
  orderEntryAllowed: boolean
  date: string | null
  availabilityKnown: boolean
  priceFrom: number | null
  priceTo: number | null
  availableTotal: number | null
  specs: SpecView[]
  contentCategory: string | null
  description: string | null
}

export interface SearchResult {
  items: ResourceSummary[]
  total: number
  availabilityKnown: boolean
  message: string | null
}
