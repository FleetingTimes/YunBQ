export function getToken(){
  return localStorage.getItem('token') || sessionStorage.getItem('token');
}
export function setToken(token, persist = true){
  const storage = persist ? localStorage : sessionStorage;
  storage.setItem('token', token);
}
export function clearToken(){
  localStorage.removeItem('token');
  sessionStorage.removeItem('token');
}