/**
 * 认证凭证存取工具
 * 说明：
 * - 统一读取/写入/清理前端本地存储中的 JWT token；
 * - 支持持久化（localStorage）与会话（sessionStorage）两种策略；
 * - 与 http.js 的拦截器配合使用，在请求阶段自动附加 Authorization 头。
 */
export function getToken(){
  // 优先从持久化存储读取；若不存在则回退到会话存储
  return localStorage.getItem('token') || sessionStorage.getItem('token');
}
export function setToken(token, persist = true){
  // persist=true → 使用 localStorage（跨会话保留）；false → 使用 sessionStorage（随会话失效）
  const storage = persist ? localStorage : sessionStorage;
  storage.setItem('token', token);
}
export function clearToken(){
  // 清理两类存储，确保退出后拦截器不再附加旧 Authorization
  localStorage.removeItem('token');
  sessionStorage.removeItem('token');
}