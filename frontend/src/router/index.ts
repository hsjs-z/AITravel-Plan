import { createRouter, createWebHistory } from 'vue-router'
import ContentView from '../views/content/ContentView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/content' },
    { path: '/content', name: 'content', component: ContentView },
    { path: '/:pathMatch(.*)*', redirect: '/content' },
  ],
})

export default router
