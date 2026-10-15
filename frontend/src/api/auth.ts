import http from './http'
import type { LoginResult, UserProfile } from './types'

export interface RegisterPayload {
  email: string
  password: string
  phone?: string
}

export function register(payload: RegisterPayload): Promise<LoginResult> {
  return http.post('/auth/register', payload)
}

export function login(email: string, password: string): Promise<LoginResult> {
  return http.post('/auth/login', { email, password })
}

export function me(): Promise<UserProfile> {
  return http.get('/users/me')
}
