import { createRouter, createWebHashHistory } from 'vue-router';

export const routes = [
  { path: '/', component: () => import('../views/Square.vue') },
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/register', component: () => import('../views/Register.vue') },
  { path: '/notes', component: () => import('../views/Notes.vue') },
  { path: '/my-notes', component: () => import('../views/MyNotes.vue') },
  { path: '/messages', meta: { requiresAuth: true }, component: () => import('../views/Messages.vue') },
  { path: '/likes', meta: { requiresAuth: true }, component: () => import('../views/Likes.vue') },
  { path: '/favorites', meta: { requiresAuth: true }, component: () => import('../views/Favorites.vue') },
  { path: '/history', meta: { requiresAuth: true }, component: () => import('../views/History.vue') },
  { path: '/forgot', component: () => import('../views/Forgot.vue') },
  { path: '/oauth/callback', component: () => import('../views/OAuthCallback.vue') },
  { path: '/admin', meta: { requiresAuth: true, requiresAdmin: true }, component: () => import('../views/Admin.vue') },
  { path: '/search', component: () => import('../views/Search.vue') },
];

const router = createRouter({ history: createWebHashHistory(), routes });

// 管理员路由守卫：从后端确认角色，避免前端篡改
import { http } from '@/api/http';
router.beforeEach(async (to, from) => {
  // 普通登录守卫：要求登录的页面在无 token 时跳转到登录页
  if (to.meta && to.meta.requiresAuth) {
    const token = localStorage.getItem('token') || sessionStorage.getItem('token');
    if (!token) {
      const redirect = typeof to.fullPath === 'string' ? to.fullPath : to.path;
      return { path: '/login', query: { redirect } };
    }
  }
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