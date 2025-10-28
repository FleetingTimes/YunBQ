import axios from 'axios';

// 优先使用环境变量，其次回退到默认 8080（与后端 application.yml 一致）
const base = import.meta.env?.VITE_API_BASE || 'http://localhost:8080/api';
export const API_BASE = base;

export const http = axios.create({ baseURL: API_BASE });

http.interceptors.request.use(cfg => {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token');
  if (token) cfg.headers['Authorization'] = 'Bearer ' + token;
  return cfg;
});

// 全局响应拦截：未登录（401）统一跳转到广场页
http.interceptors.response.use(
  resp => resp,
  err => {
    const status = err?.response?.status;
    if (status === 401) {
      try {
        localStorage.removeItem('token');
        sessionStorage.removeItem('token');
      } catch (_) {}
      // 使用 hash 路由，统一跳转首页（广场页）
      window.location.hash = '#/';
    }
    return Promise.reject(err);
  }
);

export function avatarFullUrl(path) {
  if (!path) return '';
  if (path.startsWith('http')) return path;
  const base = API_BASE.replace(/\/api$/, '');
  return base + path;
}