import { createRouter, createWebHashHistory } from 'vue-router';

export const routes = [
  { path: '/', component: () => import('../views/Square.vue') },
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/register', component: () => import('../views/Register.vue') },
  { path: '/notes', component: () => import('../views/Notes.vue') },
  { path: '/my-notes', component: () => import('../views/MyNotes.vue') },
  { path: '/forgot', component: () => import('../views/Forgot.vue') },
  { path: '/oauth/callback', component: () => import('../views/OAuthCallback.vue') },
  { path: '/admin', meta: { requiresAuth: true, requiresAdmin: true }, component: () => import('../views/Admin.vue') },
];

const router = createRouter({ history: createWebHashHistory(), routes });

// 管理员路由守卫：从后端确认角色，避免前端篡改
import { http } from '@/api/http';
router.beforeEach(async (to, from) => {
  if (to.meta && to.meta.requiresAdmin) {
    try {
      const { data } = await http.get('/api/account/me');
      if (data && data.role === 'ADMIN') {
        return true;
      }
      // 非管理员直接返回广场页
      return { path: '/' };
    } catch (e) {
      // 未登录或接口错误，返回广场页（全局 401 拦截器会清理并重定向）
      return { path: '/' };
    }
  }
  return true;
});

export default router;