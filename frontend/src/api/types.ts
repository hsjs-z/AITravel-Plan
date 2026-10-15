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

export interface LoginResult {
  token: string
  userId: string
}

export interface UserProfile {
  id: string
  email: string
  phone: string | null
}

export type PlanningStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface CreatePlanningTaskPayload {
  destination: string
  days: number
  partySize: number
  budget: number
  preferences?: string
}

export interface PlanningTask {
  id: string
  status: PlanningStatus
  itineraryId: string | null
  failureReason: string | null
  result: string | null
}

export interface PlanningDay {
  day: number
  arrangements: string[]
}

export interface PlanningNamedItem {
  name: string
  dayIndex?: number
}

export interface PlanningResult {
  destination?: string
  totalDays?: number
  partySize?: number
  estimatedBudget?: number
  days?: PlanningDay[]
  hotels?: PlanningNamedItem[]
  attractions?: PlanningNamedItem[]
  foods?: PlanningNamedItem[]
}

export type ItineraryStatus = 'DRAFT' | 'CONFIRMED' | 'LOCKED' | 'COMPLETED'
export type ItineraryItemSource = 'AI' | 'MANUAL'

export interface ItineraryItem {
  id: string
  dayDate: string
  description: string
  resourceType: string | null
  resourceId: string | null
  specId: string | null
  source: ItineraryItemSource
}

export interface Itinerary {
  id: string
  userId: string
  destination: string
  startDate: string
  endDate: string
  partySize: number
  budget: number
  status: ItineraryStatus
  hasOrder: boolean
  items: ItineraryItem[]
}

export interface ItineraryItemPayload {
  dayDate: string
  description: string
  resourceType?: string
  resourceId?: string
  specId?: string
  source: ItineraryItemSource
}

export interface UpdateItineraryPayload {
  destination: string
  startDate: string
  endDate: string
  partySize: number
  budget: number
  items: ItineraryItemPayload[]
}
