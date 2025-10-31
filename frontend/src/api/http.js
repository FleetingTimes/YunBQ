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
      // 说明：在 401（未登录或 token 失效）时统一重定向到登录页。
      // 注意：这里不再清除本地 token，避免因短暂后端校验失败或网络抖动导致“误退出”。
      //       页面重定向到登录后，用户重新登录会覆盖旧 token；若 token 真过期，后续请求仍会 401 并继续重定向。
      const suppress = err?.config?.suppress401Redirect;
      if (!suppress) {
        // 使用 hash 路由，统一跳转“登录页”，并携带当前路径作为 redirect。
        // 这样在受保护页面（如 /likes）接口返回 401 时，不会跳到首页，
        // 而是引导用户先登录，登录成功后再回到原页面，体验更合理。
        try {
          // 取当前 hash（形如 '#/likes?x=1'），去掉前导 '#'
          const hash = window.location.hash || '#/';
          const current = hash.startsWith('#') ? hash.slice(1) : hash;
          const redirect = encodeURIComponent(current || '/');
          window.location.hash = `#/login?redirect=${redirect}`;
        } catch (_) {
          // 兜底：若上面逻辑异常，至少跳到登录页
          window.location.hash = '#/login';
        }
      }
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