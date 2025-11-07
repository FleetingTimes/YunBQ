<template>
  <!--
    全屏过渡动画组件（首屏展示，纯展示职责）
    - 外观通过 props 传入（标题/副标题/颜色/动画/Logo 等）；
    - 显示/隐藏由父组件控制；
    - 点击关闭通过 closable 与 close 事件结合控制。
  -->
  <div
    class="splash"
    role="dialog"
    aria-label="应用加载中"
    aria-live="polite"
    :style="rootStyle"
    :class="themeClass"
    @click="handleClick"
  >
    <div class="logo-wrap">
      <!-- 优先使用传入的品牌图标；未提供时使用占位几何标识 -->
      <img v-if="logoSrc" :src="logoSrc" alt="logo" width="64" height="64" />
      <div v-else class="logo-mark" aria-hidden="true"></div>
    </div>
    <h1 class="title">{{ title }}</h1>
    <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>

    <!-- 加载指示器：支持 dots / spinner / none -->
    <div class="anim">
      <div v-if="animation === 'dots'" class="dots">
        <span></span><span></span><span></span>
      </div>
      <div v-else-if="animation === 'spinner'" class="spinner"></div>
      <!-- animation === 'none' 不显示动画 -->
    </div>
  </div>
</template>

<script setup>
/**
 * SplashScreen（纯展示组件）
 * - 外观 props：logo/title/subtitle/theme/颜色/动画/zIndex；
 * - 行为 props：closable（允许点击关闭时发出 close 事件）；
 * - 父组件控制显示与隐藏，不在此做计时逻辑。
 */
import { computed } from 'vue'

const props = defineProps({
  title: { type: String, default: 'YunBQ' },
  subtitle: { type: String, default: '正在准备内容，请稍候...' },
  logoSrc: { type: String, default: '' },
  theme: { type: String, default: 'light' }, // 'dark' | 'light'
  backgroundColor: { type: String, default: 'linear-gradient(180deg, #f7fbff 0%, #ffffff 100%)' },
  textColor: { type: String, default: '#111827' },
  accentColor: { type: String, default: '#67a6ff' },
  animation: { type: String, default: 'dots' }, // 'dots' | 'spinner' | 'none'
  zIndex: { type: Number, default: 9999 },
  closable: { type: Boolean, default: false },
})

const emit = defineEmits(['close'])

// 根样式：注入层级/背景/文本色与强调色变量
const rootStyle = computed(() => ({
  zIndex: props.zIndex,
  background: props.backgroundColor,
  color: props.textColor,
  '--accent-color': props.accentColor,
}))

// 主题类名钩子（用于未来扩展不同主题）
const themeClass = computed(() => (props.theme === 'dark' ? 'theme-dark' : 'theme-light'))

function handleClick() {
  if (!props.closable) return
  emit('close')
}
</script>

<style scoped>
/* 全屏遮罩：固定定位覆盖整个视窗，层级/背景/文本色由内联样式控制 */
.splash {
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100vh;
  pointer-events: auto; /* 阻止用户与底层内容交互，确保“全屏遮盖” */
  touch-action: none;   /* 移动端禁用触摸滚动与手势，避免穿透 */
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  will-change: opacity, transform; /* 提示浏览器优化过渡动画 */
}

/* 品牌图标容器：轻微放大缩小的呼吸动效 */
.logo-wrap {
  width: 92px;
  height: 92px;
  display: grid;
  place-items: center;
  background: #ffffff;
  border: 1px solid #e6effa;
  border-radius: 20px;
  box-shadow: 0 10px 30px -12px rgba(64, 158, 255, 0.35);
  animation: breathe 2.4s ease-in-out infinite;
}

/* 占位几何标识：使用强调色变量驱动视觉 */
.logo-mark {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--accent-color), #a0cfff);
  box-shadow: 0 10px 30px -12px rgba(64, 158, 255, 0.35);
}

.title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  background-image: linear-gradient(90deg, #409eff 0%, var(--accent-color) 60%, #a0cfff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.subtitle { font-size: 13px; margin: 0; opacity: 0.85; }

/* 加载动画容器 */
.anim { min-height: 16px; }

/* 三点加载动画（强调色变量） */
.dots { display: flex; gap: 8px; margin-top: 6px; }
.dots span {
  width: 8px; height: 8px; border-radius: 50%;
  background: var(--accent-color);
  animation: wave 1.2s infinite ease-in-out;
}
.dots span:nth-child(2) { animation-delay: 0.15s; }
.dots span:nth-child(3) { animation-delay: 0.3s; }

/* 旋转加载动画（spinner） */
.spinner {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid rgba(0,0,0,0.15);
  border-top-color: var(--accent-color);
  animation: spin 0.9s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

@keyframes breathe {
  0% { transform: scale(1); }
  50% { transform: scale(1.06); }
  100% { transform: scale(1); }
}

@keyframes wave {
  0%, 60%, 100% { transform: translateY(0); opacity: 1; }
  30% { transform: translateY(-6px); opacity: 0.85; }
}

/* 主题钩子：当前为占位，可在此定义 dark/light 差异样式 */
.theme-dark {}
.theme-light {}
</style>