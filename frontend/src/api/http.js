import axios from 'axios';

// 优先使用环境变量，其次回退到默认 8081
const base = import.meta.env?.VITE_API_BASE || 'http://localhost:8081/api';
export const API_BASE = base;

export const http = axios.create({ baseURL: API_BASE });

http.interceptors.request.use(cfg => {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token');
  if (token) cfg.headers['Authorization'] = 'Bearer ' + token;
  return cfg;
});

export function avatarFullUrl(path) {
  if (!path) return '';
  if (path.startsWith('http')) return path;
  const base = API_BASE.replace(/\/api$/, '');
  return base + path;
}