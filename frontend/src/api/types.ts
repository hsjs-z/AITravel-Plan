export type ResourceType = 'TRADABLE' | 'CONTENT' | 'ATTRACTION'

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
  province?: string | null
  city?: string | null
  level?: string | null
  honorTitle?: string | null
  summary?: string | null
  suggestedHours?: number | null
  ticketPrice?: number | null
  imageUrl?: string | null
  latitude?: number | null
  longitude?: number | null
  coordinateStatus?: string | null
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
export type Pace = 'RELAXED' | 'BALANCED' | 'VIBRANT'
export type TimeSlot = 'MORNING' | 'AFTERNOON' | 'EVENING'

export interface CreatePlanningTaskPayload {
  destination: string
  days: number
  partySize: number
  budget?: number
  pace?: Pace
  preferences?: string
}

export interface PlanningTask {
  id: string
  status: PlanningStatus
  itineraryId: string | null
  failureReason: string | null
  result: string | null
  pace?: string
}

export interface PlanningAttraction {
  name: string
  description: string | null
  suggestedHours: number | null
  ticketPrice: number | null
  resourceId: string | null
  specId: string | null
  matched: boolean
  bookable: boolean
  priceLabel: string | null
  latitude: number | null
  longitude: number | null
  coordinateStatus: string | null
  timeSlot: TimeSlot
  sortOrder: number
}

export interface PlanningResult {
  destination?: string
  totalDays?: number
  pace?: Pace
  days?: Array<{
    day: number
    theme: string
    slots: Record<TimeSlot, PlanningAttraction[]>
  }>
  warnings?: string[]
  tips?: string[]
}

export type ItineraryStatus = 'DRAFT' | 'CONFIRMED' | 'LOCKED' | 'COMPLETED'
export type ItineraryItemSource = 'AI' | 'MANUAL'

export interface ItineraryItem {
  id: string
  dayDate: string
  name: string | null
  description: string
  suggestedHours: number | null
  ticketPrice: number | null
  latitude: number | null
  longitude: number | null
  resourceType: string | null
  resourceId: string | null
  specId: string | null
  source: ItineraryItemSource
  timeSlot: TimeSlot | null
  sortOrder: number | null
}

export interface Itinerary {
  id: string
  userId: string
  destination: string
  startDate: string
  endDate: string
  partySize: number
  budget: number | null
  status: ItineraryStatus
  hasOrder: boolean
  activeOrderCount: number
  completedOrderCount: number
  items: ItineraryItem[]
  warnings: string[]
  tips: string[]
}

export interface ItinerarySummary {
  id: string
  destination: string
  startDate: string
  endDate: string
  partySize: number
  status: ItineraryStatus
  hasOrder: boolean
  activeOrderCount: number
  completedOrderCount: number
  createdAt: string
}

export interface ItineraryItemPayload {
  dayDate: string
  name?: string
  description: string
  suggestedHours?: number | null
  ticketPrice?: number | null
  latitude?: number | null
  longitude?: number | null
  resourceType?: string
  resourceId?: string
  specId?: string
  source: ItineraryItemSource
  timeSlot?: TimeSlot | null
  sortOrder?: number | null
}

export interface UpdateItineraryPayload {
  destination: string
  startDate: string
  endDate: string
  partySize: number
  budget?: number | null
  items: ItineraryItemPayload[]
  warnings?: string[]
}

export interface AddItineraryItemPayload {
  dayDate: string
  name?: string
  description?: string
  suggestedHours?: number | null
  timeSlot: TimeSlot
  resourceId: string
  specId?: string | null
}

export interface AttractionItem {
  resourceId: string
  name: string
  province: string | null
  city: string | null
  level: string | null
  honorTitle: string | null
  summary: string | null
  suggestedHours: number | null
  ticketPrice: number | null
  free: boolean
  latitude: number | null
  longitude: number | null
  coordinateStatus: string | null
  specId: string | null
  specName: string | null
  price: number | null
  available: number | null
}

export interface AttractionPage {
  total: number
  offset: number
  limit: number
  hasMore: boolean
  items: AttractionItem[]
}

export type OrderStatus = 'CREATED' | 'BOOKED' | 'PAID' | 'CONFIRMED' | 'CANCELLED' | 'FAILED'
export type OrderItemStatus = 'PENDING' | 'BOOKED' | 'FAILED' | 'RELEASED'

export interface OrderItem {
  id: string
  resourceId: string
  specId: string
  date: string
  quantity: number
  unitPrice: number
  status: OrderItemStatus
  lockId: string | null
}

export interface Order {
  id: string
  itineraryId: string
  status: OrderStatus
  totalAmount: number
  failureReason: string | null
  items: OrderItem[]
}

export interface CreateOrderItemPayload {
  resourceId: string
  specId: string
  date: string
  quantity: number
  unitPrice: number
}

export interface CreateOrderPayload {
  itineraryId: string
  idempotencyKey?: string
  items: CreateOrderItemPayload[]
}

export type NotificationReadStatus = 'UNREAD' | 'READ'

export interface NotificationItem {
  id: string
  type: string
  channel: string
  businessRef: string | null
  title: string
  body: string
  readStatus: NotificationReadStatus
  createdAt: string
  readAt: string | null
}

export interface UnreadCount {
  unread: number
}
