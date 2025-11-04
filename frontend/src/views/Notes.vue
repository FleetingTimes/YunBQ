<template>
  <!-- 两栏布局：左侧 SideNav，右侧上方顶栏、右侧下方正文（NotesBody）
       说明：统一全站页面结构，不影响 NotesBody 已实现功能；侧栏仅提供结构与视觉一致性。 -->
  <TwoPaneLayout>
    <!-- 左侧：通用侧栏（仅展示，不做快捷填充或滚动）
    <template #left>
      <SideNav :sections="sectionsNotes" v-model:activeId="activeId" :alignCenter="true" />
    </template>
     -->
    <!-- 全宽顶栏：跨越左右两列并吸顶，顶栏内容全屏铺满 -->
    <template #topFull>
      <!-- 固定透明顶栏：transparent=true 禁止滚动时毛玻璃切换，保持沉浸式背景 -->
      <AppTopBar fluid :transparent="true" @search="onSearch" />
    </template>
    <!-- 右下：正文（添加便签） -->
    <template #rightMain>
      <div class="notes-container">
        <NotesBody :query="query" />
      </div>
    </template>
  </TwoPaneLayout>
  
</template>

<script setup>
// 添加便签页脚本：接入通用侧边栏以保持左侧导航布局（与广场页一致）
// 说明：
// - sectionsNotes 为本页的侧边栏配置，包含“便签分类”父项与若干子项（网站/影视/音乐/工具/AI）；
// - activeId 用于高亮当前被点击的侧边栏项；
// - 不进行“快捷填充标签”等行为，点击仅改变高亮或折叠状态。 
import { ref, defineAsyncComponent } from 'vue';
const TwoPaneLayout = defineAsyncComponent(() => import('@/components/TwoPaneLayout.vue'))
const AppTopBar = defineAsyncComponent(() => import('@/components/AppTopBar.vue'));
const NotesBody = defineAsyncComponent(() => import('./notes/NotesBody.vue'));
const SideNav = defineAsyncComponent(() => import('@/components/SideNav.vue'));
// 导入与广场页一致的公共导航配置，保持内容与顺序完全相同
import { sideNavSections } from '@/config/navSections.js';

const query = ref('');
function onSearch(q){ query.value = q || ''; }

// 侧边栏配置：仅包含与“添加便签”相关的快捷分类
const activeId = ref('');
// 复用广场页导航配置：添加便签页仅用于显示与折叠，不进行滚动或标签填充
const sectionsNotes = sideNavSections;

// 不进行快捷填充：移除 quickTags 与选择事件处理逻辑
</script>

<style scoped>
/* 顶栏宽度限制已移除：使用 TwoPaneLayout 的全宽插槽，AppTopBar 自行吸顶与视觉控制 */
/* 正文容器：保持原有宽度与内边距，避免影响编辑体验 */
.notes-container { max-width: 1080px; margin: 0 auto; padding: 0 16px; }

/* 回退说明：移除了页面级 :deep(.topbar) 吸顶覆写与内容区的渐隐遮罩，
   保持添加便签页原始样式与滚动展示，不影响顶栏组件与其它页面。 */

/* 响应式由 TwoPaneLayout 统一处理，此处不再单独覆盖 */
</style>