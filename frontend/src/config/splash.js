/**
 * 全局首屏过渡动画（Splash）配置
 *
 * 说明：
 * - 本配置文件用于集中管理过渡层的外观与行为；
 * - App.vue 会读取这些配置决定是否显示，以及显示多久；
 * - SplashScreen.vue 作为纯展示组件，外观相关通过 props 接收。
 */

// 默认配置对象（可按需调整）。
const splashConfig = {
  /** 是否启用首屏过渡动画
   * 变更：根据移动端优化需求，取消首次打开时的过渡动画。
   * 原因：用户在手机浏览器访问时希望立即进入内容，避免额外等待与遮盖层。
   * 做法：将 enabled 置为 false，App.vue 中的 shouldShowSplash() 将返回 false，从而不渲染 SplashScreen。*/
  enabled: false,

  /**
   * 显示时长（毫秒）。注意：
   * - App.vue 会根据网络情况做可选延长；
   * - 如果设置太短，动画可能还未完成就关闭；
   */
  durationMs: 4000,

  /** 是否在同一标签页内仅显示一次（使用 sessionStorage 标记） */
  oncePerSession: true,
  /** sessionStorage 的 key，可自定义避免冲突 */
  sessionKey: 'splash_shown_session',

  /**
   * 路由显示规则：
   * - includeNames/includePaths 命中其一即显示；
   * - excludeNames/excludePaths 命中则不显示；
   * - 如均为空，则对所有路由生效（受 enabled 控制）。
   */
  routes: {
    includeNames: [], // 例如 ['Square']，匹配 route.name
    includePaths: [], // 例如 ['/']，匹配 route.path 前缀
    excludeNames: [],
    excludePaths: [],
  },

  /**
   * 行为控制：
   * - lockScroll: 显示期间禁用页面滚动；
   * - closeOnClick: 点击过渡层是否立刻关闭；
   */
  behavior: {
    lockScroll: true,
    closeOnClick: false,
  },

  /**
   * 网络敏感参数：
   * - 根据网络情况（如 2G/slow-2g）延长显示时间，改善体验一致性；
   */
  networkSensitive: {
    extendDurationOnSlowNetwork: true,
    extendMs: 1000, // 在慢网环境下额外延长的时长
    // 通过 navigator.connection.effectiveType 判定慢网；
    slowTypes: ['slow-2g', '2g'],
  },

  /**
   * 外观配置：传递给 SplashScreen 组件的 props
   * - 可根据品牌需求调整颜色、文案、Logo、动画风格等。
   */
  appearance: {
    title: 'ShiYan',
    subtitle: '希望你 好好吃饭 好好睡觉 天天开心的 ',
    logoSrc: '/src/assets/cl2.png', // 例如 '/logo.svg'，留空则不显示 Logo
    theme: 'dark', // 'dark' | 'light'
    backgroundColor: '#0f172a',
    textColor: '#e2e8f0',
    accentColor: '#38bdf8',
    animation: 'dots', // 'dots' | 'spinner' | 'none'
    zIndex: 9999,
  },
};

/**
 * 判定当前路由是否应该显示 Splash。
 * - route: 来自 useRoute() 的当前路由对象；
 * - config: 配置对象；
 */
export function shouldShowSplash(route, config = splashConfig) {
  if (!config.enabled) return false;

  const { includeNames, includePaths, excludeNames, excludePaths } = config.routes || {};
  const name = route?.name || null;
  const path = route?.path || '';

  // 命中排除规则则不显示
  if (excludeNames?.length && name && excludeNames.includes(String(name))) return false;
  if (excludePaths?.length && excludePaths.some((p) => path.startsWith(p))) return false;

  // 包含规则为空则默认显示（受 enabled 控制）
  const noIncludeRules = (!includeNames || includeNames.length === 0) && (!includePaths || includePaths.length === 0);
  if (noIncludeRules) return true;

  // 命中包含规则则显示
  if (includeNames?.length && name && includeNames.includes(String(name))) return true;
  if (includePaths?.length && includePaths.some((p) => path.startsWith(p))) return true;

  return false;
}

/**
 * 计算最终显示时长：
 * - 基于基础时长（durationMs）；
 * - 若启用慢网延长且检测到慢网，则附加 extendMs。
 */
export function computeDurationMs(config = splashConfig) {
  let base = Number(config.durationMs) || 3000;
  const ns = config.networkSensitive || {};
  try {
    const conn = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
    const type = conn?.effectiveType || '';
    if (ns.extendDurationOnSlowNetwork && ns.slowTypes?.includes(type)) {
      base += Number(ns.extendMs) || 0;
    }
  } catch (e) {
    // 在不支持 Network Information API 的环境下忽略
  }
  return base;
}

/** 导出配置对象（App.vue 使用） */
export default splashConfig;