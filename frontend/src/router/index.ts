import { createRouter, createWebHistory } from 'vue-router'
import ContentView from '../views/content/ContentView.vue'
import LoginView from '../views/auth/LoginView.vue'
import RegisterView from '../views/auth/RegisterView.vue'
import PlanningView from '../views/planning/PlanningView.vue'
import PlanningResultView from '../views/planning/PlanningResultView.vue'
import ItineraryView from '../views/itinerary/ItineraryView.vue'
import OrderListView from '../views/order/OrderListView.vue'
import OrderCreateView from '../views/order/OrderCreateView.vue'
import OrderDetailView from '../views/order/OrderDetailView.vue'
import PaymentView from '../views/payment/PaymentView.vue'
import NotificationView from '../views/notification/NotificationView.vue'
import { getToken } from '../api/session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/content' },
    { path: '/content', name: 'content', component: ContentView },
    { path: '/planning', name: 'planning', component: PlanningView },
    {
      path: '/planning/tasks/:taskId/result',
      name: 'planning-result',
      component: PlanningResultView,
    },
    { path: '/itineraries/:itineraryId', name: 'itinerary', component: ItineraryView },
    { path: '/orders', name: 'orders', component: OrderListView },
    { path: '/orders/new', name: 'order-create', component: OrderCreateView },
    { path: '/orders/:orderId', name: 'order-detail', component: OrderDetailView },
    { path: '/orders/:orderId/pay', name: 'order-pay', component: PaymentView },
    { path: '/notifications', name: 'notifications', component: NotificationView },
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
