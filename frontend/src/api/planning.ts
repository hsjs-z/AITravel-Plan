import http from './http'
import type { CreatePlanningTaskPayload, PlanningTask } from './types'

export function createPlanningTask(payload: CreatePlanningTaskPayload): Promise<{ taskId: string }> {
  return http.post('/planning/tasks', payload)
}

export function getPlanningTask(taskId: string): Promise<PlanningTask> {
  return http.get(`/planning/tasks/${taskId}`)
}

export function retryPlanningTask(taskId: string): Promise<PlanningTask> {
  return http.post(`/planning/tasks/${taskId}/retry`)
}
