<template>
  <!-- 统一顶栏结构：顶栏置于广场容器之外，采用一致的 topbar-wrap 包裹以保证宽度与边距统一 -->
  <div class="topbar-wrap">
    <AppTopBar @search="onSearch" />
  </div>
  <div class="square-container">
    <!-- 回退：移除页面级顶栏吸顶与内容渐隐遮罩，恢复原始布局与滚动行为 -->
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
  /* 统一顶栏包裹：限定最大宽度为 1080px、居中显示，并保留左右 16px 安全边距
     说明：这使顶栏在所有页面显示为一致宽度，不受容器（如 .container/.square-container）影响 */
  .topbar-wrap { max-width: 1080px; margin: 0 auto; padding: 0 16px; }
  /* 页面容器样式
     说明：
     - 在容器上声明 CSS 变量以配置子组件（SquareBody）里的广场标题区高度；
     - 变量会向子树继承，`.square-header` 使用 `min-height: var(--square-header-height)` 读取该值；
     - 滚动定位逻辑会动态读取真实高度并叠加 36px 安全间距，调整后仍能准确避让。 */
  /* 回退：广场页容器恢复为居中定宽布局
     说明：
     - 恢复为最大宽度 960px，居中显示；
     - 采用通用内边距 16px，以保持内容安全边距；
     - 该回退仅影响广场页容器，不改动内部组件布局。 */
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

   /* 左侧侧边栏偏移配置
      需求：将导航栏“向下、向右”移动一些以避开顶部元素或贴边区域。
      用法：SideNav.vue 中读取以下变量以控制定位与间距：
      - --side-nav-offset-y → 作用于 position: sticky 的 top（向下移动）
      - --side-nav-offset-x → 作用于 margin-left（向右移动）
      默认值分别为 16px/0px；此处设置为 36px/12px 以获得更舒适的间距。 */
   --side-nav-offset-y: 36px;  /* 垂直向下偏移（粘性顶部距离） */
   --side-nav-offset-x: 12px;  /* 水平向右偏移（列内左右空隙） */
  }
  /* 回退说明：移除页面级 :deep(.topbar) 吸顶覆写与 .content-fade 渐隐遮罩，
     保持广场页原始样式与行为，仅保留标题高度与侧边栏偏移变量。 */
</style>