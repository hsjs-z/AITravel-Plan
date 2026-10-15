import { createRouter, createWebHistory } from 'vue-router'
import ContentView from '../views/content/ContentView.vue'
import LoginView from '../views/auth/LoginView.vue'
import RegisterView from '../views/auth/RegisterView.vue'
import PlanningView from '../views/planning/PlanningView.vue'
import ItineraryView from '../views/itinerary/ItineraryView.vue'
import { getToken } from '../api/session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/content' },
    { path: '/content', name: 'content', component: ContentView },
    { path: '/planning', name: 'planning', component: PlanningView },
    { path: '/itineraries/:itineraryId', name: 'itinerary', component: ItineraryView },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/register', name: 'register', component: RegisterView },
    { path: '/:pathMatch(.*)*', redirect: '/content' },
  ],
})

router.beforeEach((to) => {
  const token = getToken()
  const isAuthPage = to.name === 'login' || to.name === 'register'

  if (!token && !isAuthPage) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (token && isAuthPage) {
    return { name: 'content' }
  }
  return true
})

export default router
