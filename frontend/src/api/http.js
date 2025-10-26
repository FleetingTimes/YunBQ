import axios from 'axios';

export const API_BASE = 'http://localhost:8080/api';

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