import { createRouter, createWebHashHistory } from 'vue-router';

export const routes = [
  { path: '/', component: () => import('../views/Square.vue') },
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/register', component: () => import('../views/Register.vue') },
  // 路由重命名：将视图路径统一为 /shiyan，并保留 /notes 作为别名以兼容旧链接/书签
  // 说明：alias 不会改变路由的实际地址生成，仅作为额外可匹配路径，内部渲染同一组件。
  { path: '/shiyan', alias: '/notes', component: () => import('../views/Notes.vue') },
  // 我的拾言页：需要登录。原因：页面会请求 /api/shiyan?mineOnly=true 与 /api/account/me，
  // 未登录时后端返回 401，前端全局拦截器会跳转首页导致请求被中止、浏览器提示报错。
  // 加上 requiresAuth 后，通过路由守卫在进入页面前统一跳转登录页，避免页面内出现“报错/请求中止”。
  // 路由重命名：新增 /my-shiyan 作为“我的拾言”主路径，并保留 /my-notes 作为别名兼容旧链接
  { path: '/my-shiyan', alias: '/my-notes', meta: { requiresAuth: true }, component: () => import('../views/MyNotes.vue') },
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