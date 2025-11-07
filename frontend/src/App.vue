<!--
  应用根组件（App.vue）
  说明：
  - 仅渲染 `<router-view />` 以承载各路由页面；
  - 底部统一透明页脚（TransparentFooter）在所有页面保持一致；
  - 全局样式入口可在此组件 `<style>` 中扩展。
-->
<template>
  <!--
    根组件集成全屏过渡动画（SplashScreen）：
    需求：首次打开时显示 3 秒过渡动画，随后展示应用内容；
    方案：在本页以覆盖层实现（非独立路由页），避免额外跳转与状态丢失；
    触发：仅在当前标签页首次进入时显示（使用 sessionStorage 标记），路由切换不再重复显示。
  -->
  <!-- 撤销过渡动画：直接渲染 SplashScreen（保留全屏遮盖与配置功能） -->
  <SplashScreen
    v-if="showSplash"
    v-bind="splash.appearance"
    :closable="splash.behavior.closeOnClick"
    @close="showSplash = false"
  />

  <!-- 应用内容区域（路由视图 + 全局底栏） -->
  <router-view />
  <TransparentFooter />
</template>

<script setup>
import TransparentFooter from '@/components/TransparentFooter.vue'
import { ref, onMounted, watch } from 'vue'
import SplashScreen from '@/components/SplashScreen.vue'
import splash, { shouldShowSplash, computeDurationMs } from '@/config/splash'
import { useRoute } from 'vue-router'

/**
 * 显示控制：showSplash
 * - 首次进入当前标签页时显示 3 秒；之后置位 sessionStorage 标记避免重复显示；
 * - 期间禁用页面滚动（overflow:hidden），提升沉浸效果与避免滚动穿透。
 */
// 配置对象（含外观与行为）
// - splash.appearance：外观 props（传递给组件）
// - splash.behavior：行为控制（锁定滚动/点击关闭）
// - splash.oncePerSession + sessionKey：标签页内仅显示一次
// - splash.routes：路由显示规则（include/exclude）
const showSplash = ref(false)
const route = useRoute()

function setBodyScrollDisabled(disabled){
  try {
    document.body.style.overflow = disabled ? 'hidden' : ''
  } catch { /* 忽略异常，避免影响首屏渲染 */ }
}

onMounted(() => {
  try {
    // 路由规则判定：若当前路由不匹配显示规则则不显示
    const allowedByRoute = shouldShowSplash(route, splash)
    if (!allowedByRoute) return

    // 会话显示判定：oncePerSession 控制同标签页是否仅显示一次
    const key = splash.sessionKey || '__splash_shown__'
    const shown = splash.oncePerSession ? sessionStorage.getItem(key) : null
    const shouldShow = !shown || !splash.oncePerSession
    if (!shouldShow) return

    // 显示 + 定时关闭（时长可网络敏感延长）
    showSplash.value = true
    const duration = computeDurationMs(splash)
    setTimeout(() => {
      showSplash.value = false
      try { if (splash.oncePerSession) sessionStorage.setItem(key, '1') } catch {}
    }, duration)
  } catch { /* 忽略异常，继续正常渲染 */ }
})

// 动态禁用/恢复滚动（跟随 showSplash，受行为配置控制）
watch(showSplash, (val) => {
  const needLock = splash.behavior?.lockScroll
  setBodyScrollDisabled(!!needLock && val)
})
</script>

<style>
/* 全局路由视图样式可在此扩展 */
</style>
