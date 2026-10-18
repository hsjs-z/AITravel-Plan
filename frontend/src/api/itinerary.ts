import http from './http'
import type {
  AddItineraryItemPayload,
  Itinerary,
  ItinerarySummary,
  UpdateItineraryPayload,
} from './types'

export function listItineraries(): Promise<ItinerarySummary[]> {
  return http.get('/itineraries')
}

export function getItinerary(itineraryId: string): Promise<Itinerary> {
  return http.get(`/itineraries/${itineraryId}`)
}

export function updateItinerary(itineraryId: string, payload: UpdateItineraryPayload): Promise<Itinerary> {
  return http.put(`/itineraries/${itineraryId}`, payload)
}

export function confirmItinerary(itineraryId: string): Promise<Itinerary> {
  return http.post(`/itineraries/${itineraryId}/confirm`)
}

export function addItineraryItem(
  itineraryId: string,
  payload: AddItineraryItemPayload,
): Promise<Itinerary> {
  return http.post(`/itineraries/${itineraryId}/items`, payload)
}

export function deleteItineraryItem(itineraryId: string, itemId: string): Promise<Itinerary> {
  return http.delete(`/itineraries/${itineraryId}/items/${itemId}`)
}

export function deleteItinerary(itineraryId: string): Promise<void> {
  return http.delete(`/itineraries/${itineraryId}`)
}
