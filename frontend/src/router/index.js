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
      // 直接调用后端管理员健康检查，确保与后端权限判定一致
      await http.get('/admin/health');
      return true;
    } catch (e) {
      // 未登录(401)或无权限(403)都返回广场页
      return { path: '/' };
    }
  }
  return true;
});

export default router;