import http from './http'
import type { Itinerary, UpdateItineraryPayload } from './types'

export function getItinerary(itineraryId: string): Promise<Itinerary> {
  return http.get(`/itineraries/${itineraryId}`)
}

export function updateItinerary(itineraryId: string, payload: UpdateItineraryPayload): Promise<Itinerary> {
  return http.put(`/itineraries/${itineraryId}`, payload)
}

export function confirmItinerary(itineraryId: string): Promise<Itinerary> {
  return http.post(`/itineraries/${itineraryId}/confirm`)
}

export function deleteItinerary(itineraryId: string): Promise<void> {
  return http.delete(`/itineraries/${itineraryId}`)
}
