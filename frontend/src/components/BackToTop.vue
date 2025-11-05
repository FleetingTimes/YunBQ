<template>
  <!--
    回到顶部按钮（玻璃拟态风格）：
    - 默认固定在视窗右下角；若提供 target（滚动容器），则根据该容器的滚动位置显示/隐藏按钮；
    - 点击按钮后将容器滚动到顶部，使用平滑滚动；
    - 提供自定义位置与阈值的属性，满足不同页面布局需求。
  -->
  <transition name="fade">
    <button
      v-if="visible"
      class="back-to-top"
      :style="{ bottom: `${bottom}px`, right: `${right}px` }"
      @click="scrollToTop"
      aria-label="回到顶部"
    >
      <!-- 使用内联 SVG，避免外部资源加载不稳定 -->
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M12 4l-7 7h4v7h6v-7h4l-7-7z" fill="currentColor"/>
      </svg>
    </button>
  </transition>
</template>

<script setup>
// 组件设计说明：
// - target：滚动目标。支持 HTMLElement、选择器字符串或 Vue ref（含 value）。
// - threshold：显示按钮所需的滚动高度；当滚动超过该值时按钮显示，反之隐藏。
// - bottom/right：按钮在视窗中的位置（像素）。
// - 平滑滚动：优先使用 scrollTo({behavior:'smooth'})，在不支持时回退为逐步滚动。

import { ref, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps({
  target: { type: [Object, String], default: null },
  threshold: { type: Number, default: 200 },
  bottom: { type: Number, default: 24 },
  right: { type: Number, default: 24 },
})

const visible = ref(false)
let targetEl = null
let scrollEl = null
let onScroll = null

function isRefLike(v){ return v && typeof v === 'object' && 'value' in v }
function resolveTarget(){
  try{
    const t = props.target
    if (!t){ return null }
    if (typeof t === 'string') return document.querySelector(t)
    if (isRefLike(t)) return t.value || null
    // HTMLElement：nodeType === 1
    if (t && t.nodeType === 1) return t
  }catch{}
  return null
}
function getScrollElement(){
  // 若提供了滚动容器，则使用该容器；否则回退到 window 的滚动（documentElement）
  const el = resolveTarget()
  if (el) return el
  // 回退：窗口滚动元素（兼容不同浏览器）
  return document.scrollingElement || document.documentElement
}
function currentScrollTop(){
  try{
    if (!scrollEl) return 0
    return scrollEl.scrollTop || 0
  }catch{ return 0 }
}
function updateVisible(){
  try{ visible.value = currentScrollTop() > props.threshold }
  catch{ visible.value = false }
}
function scrollToTop(){
  try{
    if (!scrollEl) return
    // 优先使用平滑滚动 API
    if (typeof scrollEl.scrollTo === 'function'){
      scrollEl.scrollTo({ top: 0, behavior: 'smooth' })
      return
    }
    // 回退：逐步设置 scrollTop
    const timer = setInterval(() => {
      try{
        if (!scrollEl) { clearInterval(timer); return }
        const cur = scrollEl.scrollTop
        if (cur <= 0) { clearInterval(timer); return }
        scrollEl.scrollTop = Math.max(0, cur - 60)
      }catch{ clearInterval(timer) }
    }, 16)
  }catch{}
}

function bind(){
  try{
    scrollEl = getScrollElement()
    targetEl = resolveTarget()
    onScroll = () => updateVisible()
    // 使用捕获阶段避免某些组件内部阻止冒泡导致未触发
    scrollEl.addEventListener('scroll', onScroll, { passive: true })
    // 初始化显示状态
    updateVisible()
  }catch{}
}
function unbind(){
  try{ if (scrollEl && onScroll) scrollEl.removeEventListener('scroll', onScroll) }catch{}
  scrollEl = null; onScroll = null; targetEl = null
}

onMounted(() => { bind() })
onUnmounted(() => { unbind() })
watch(() => props.target, () => { unbind(); bind() })
</script>

<style scoped>
/* 按钮：玻璃拟态 + 圆形悬浮 + 轻微阴影 */
.back-to-top {
  position: fixed;
  z-index: 1000;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #303133;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: saturate(180%) blur(12px);
  -webkit-backdrop-filter: saturate(180%) blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: 0 12px 28px rgba(0,0,0,0.12);
  cursor: pointer;
  transition: transform .2s ease, box-shadow .2s ease;
}
.back-to-top:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 32px rgba(0,0,0,0.16);
}

/* 渐隐动效 */
.fade-enter-active, .fade-leave-active { transition: opacity .2s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>