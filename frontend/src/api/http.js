import axios from 'axios';

/**
 * HTTP 客户端配置（Axios 实例）
 * 作用：
 * - 统一设置 `baseURL`，与后端 `application.yml` 中端口/前缀保持一致；
 * - 在请求阶段自动附加 `Authorization: Bearer <token>`；
 * - 在响应阶段统一处理 401（未登录/令牌失效）：可选择跳转登录或在调用处抑制重定向；
 * - 提供 `avatarFullUrl` 工具以将相对路径转换为完整静态资源 URL。
 *
 * 关键约定：
 * - `baseURL` 已包含 `/api` 前缀（例如 `http://localhost:8080/api`）；前端发起请求时不要重复添加 `/api`；
 * - 调用处若需要在 401 时保留当前页面并自行处理，可在请求配置中设置 `suppress401Redirect: true`；
 * - 使用 hash 路由，401 时携带重定向参数返回登录页：`#/login?redirect=<当前路径>`。
 */

// API 基址推断逻辑：优先环境变量，其次智能回退到“公网域名”
// 设计背景：
// - 在本机联调时，Vite 默认使用 `http://localhost:8080/api`；
// - 当通过 Cloudflare Tunnel 公网访问前端（例如手机访问 `https://app.shiyan.online`），
//   若未正确配置环境变量，前端将错误地请求 `http://localhost:8080/api`，导致“看得到页面但没有数据”。
// - 为提升鲁棒性，这里加入“公网域名自动回退”：当检测到当前页面是公网域名（HTTPS 且形如 *.shiyan.online），
//   在未显式设置 `VITE_API_BASE` 时，自动将 API 基址设为对应的 `api.<域名>/api`。
// 计算 API 基址：
// 1) 默认取环境变量 `VITE_API_BASE`，否则退回 `http://localhost:8080/api`（本地联调）
// 2) 当当前页面处于「https 的公网域名（*.shiyan.online）」时，强制覆盖为对应 `https://api.<域名>/api`
//    目的：避免线上构建时误注入了 http 或 localhost，导致移动端 WebView 在 https 下被“混合内容”拦截，出现“只有背景图、没有数据”的现象。
let resolvedBase = import.meta.env?.VITE_API_BASE || 'http://localhost:8080/api';
try {
  const loc = window?.location;
  const hostname = String(loc?.hostname || '');
  const protocol = String(loc?.protocol || 'http:');
  // 判定“公网域名”条件：HTTPS 且域名以 shiyan.online 结尾（可根据实际业务拓展）
  const isPublicDomain = protocol === 'https:' && /\.shiyan\.online$/i.test(hostname);
  if (isPublicDomain) {
    // 线上（https + 公网域名）场景：统一走 api 子域，避免混合内容与跨域异常
    const apiHost = hostname.replace(/^app\./i, 'api.');
    resolvedBase = `${protocol}//${apiHost}/api`;
  } else {
    // 非公网域：保留环境变量或本地默认；若存在 http 基址但页面为 https，则尝试自动升级协议以降低被拦截概率
    try {
      const u = new URL(resolvedBase);
      if (protocol === 'https:' && u.protocol === 'http:') {
        // 自动协议升级：仅修改为 https，主机与端口保持不变（如 http://example:8080 -> https://example:8080）
        u.protocol = 'https:';
        resolvedBase = u.toString();
      }
    } catch (_) {
      // 忽略解析异常，保留原值
    }
  }
} catch (_) {
  // 忽略运行期环境不支持 window 的情况（如 SSR），保留默认 localhost 基址
}
export const API_BASE = resolvedBase;

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
    // 诊断辅助：在移动端 WebView 场景下，常见问题为“混合内容被拦截/证书错误/网络不可达”；
    // 当没有后端日志时，上报基础错误信息便于定位。
    if (!err?.response) {
      try {
        console.error('[HTTP] 请求失败（无响应对象，可能为网络/证书/拦截）：', {
          baseURL: API_BASE,
          url: err?.config?.url,
          method: err?.config?.method,
          message: err?.message
        });
      } catch (_) { /* 控制台不可用时忽略 */ }
    }
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
  // 头像/图片完整地址拼接：
  // - 当为绝对 URL（以 http 开头）直接返回；
  // - 当为相对路径（如 '/uploads/avatar/xxx.png'），从 API_BASE 去除 '/api' 前缀，
  //   拼接得到静态资源完整地址（与后端静态资源映射一致）。
  if (!path) return '';
  if (path.startsWith('http')) return path;
  const base = API_BASE.replace(/\/api$/, '');
  return base + path;
}

// 头像缩略图完整地址拼接：
// - 保留原图路径规则，在文件名后追加 `_<size>` 再拼接后缀；
// - 绝对 URL（http 开头）直接返回；
// - 相对路径（如 `/uploads/avatars/xxx.png`）按约定生成 `/uploads/avatars/xxx_<size>.png`。
export function avatarThumbUrl(path, size = 64) {
  if (!path) return ''
  if (String(path).startsWith('http')) return path
  const s = Number.isFinite(size) ? Math.max(1, Math.floor(size)) : 64
  const m = String(path).match(/^(.*?)(\.[a-zA-Z0-9]+)$/)
  const withSize = m ? `${m[1]}_${s}${m[2]}` : `${path}_${s}`
  const base = API_BASE.replace(/\/api$/, '')
  return base + withSize
}