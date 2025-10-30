<template>
  <div class="square-container">
    <AppTopBar @search="onSearch" />
    <SquareBody :query="query" />
  </div>
  
</template>

<script setup>
import { ref, defineAsyncComponent } from 'vue'

const AppTopBar = defineAsyncComponent(() => import('@/components/AppTopBar.vue'))
const SquareBody = defineAsyncComponent(() => import('./square/SquareBody.vue'))

const query = ref('')
function onSearch(q){ query.value = q || '' }
</script>

<style scoped>
/* 页面容器样式
   说明：
   - 在容器上声明 CSS 变量以配置子组件（SquareBody）里的广场标题区高度；
   - 变量会向子树继承，`.square-header` 使用 `min-height: var(--square-header-height)` 读取该值；
   - 滚动定位逻辑会动态读取真实高度并叠加 36px 安全间距，调整后仍能准确避让。 */
  .square-container {
   max-width: 960px; margin: 0 auto; padding: 16px;
   /* 设置广场标题高度为 12px：最小化标题占用空间
      说明：`.square-header` 使用 min-height，因此总高度≈min-height+padding；
      我们将上下内边距设为 0，以使总高度尽量接近 12px。
      警告：标题文字与图标有自身字体大小与行高（当前 h1 为 20px），
      在 12px 的容器下可能出现拥挤或裁切。若出现不理想效果，可按需：
      - 轻微调大此值（如 14px/16px），或
      - 在 SquareBody.vue 中将 `.brand h1 { font-size: 20px; }` 调小。
      滚动定位会动态读取实际高度并叠加 36px 安全间距，仍能准确避让。 */
   --square-header-height: 12px;
   /* 将上下内边距设为 0，确保总高度尽量接近 20px（可按需改回 4px/6px/8px） */
   --square-header-padding-block: 0px;
  }
</style>