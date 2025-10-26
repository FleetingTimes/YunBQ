import { createRouter, createWebHashHistory } from 'vue-router';

export const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/register', component: () => import('../views/Register.vue') },
  { path: '/notes', component: () => import('../views/Notes.vue') },
  { path: '/forgot', component: () => import('../views/Forgot.vue') },
  { path: '/oauth/callback', component: () => import('../views/OAuthCallback.vue') },
];

const router = createRouter({ history: createWebHashHistory(), routes });

export default router;