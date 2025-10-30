<template>
  <!-- 添加便签页：接入通用侧边栏 SideNav，用于分类快捷填充标签
       结构说明：
       - 顶部保持 AppTopBar；
       - 下方使用 layout 容器，左侧为 SideNav，右侧为 NotesBody 内容区；
       行为说明：
       - 点击侧边栏子项（网站/影视/音乐/工具/AI）将通过 quickTags 传递给 NotesBody；
       - NotesBody 监听 quickTags 变化并将其写入草稿的标签输入框，提供快捷分类。 -->
  <!-- 页面容器：设为全宽，左侧无内边距，让导航栏贴页面最左侧。
       备注：仅本页生效，不影响其他页面的居中容器。 -->
  <div class="notes-container">
    <!-- 顶栏包裹：恢复到之前的居中宽度（与 .container 一致） -->
    <div class="topbar-wrap">
      <AppTopBar @search="onSearch" />
    </div>
    <!-- 回退：移除页面级顶栏吸顶与内容渐隐遮罩，恢复原始布局与滚动行为 -->
    <section class="layout">
      <!-- 通用侧边栏：父子导航 + 默认折叠；
           说明：添加便签页的侧边导航仅用于页面结构与视觉一致性，不进行标签快捷填充或滚动锚点。 -->
      <SideNav :sections="sectionsNotes" v-model:activeId="activeId" />
      <div class="content-scroll">
        <!-- 添加便签内容区：保持原有行为，不接收 quickTags -->
        <NotesBody :query="query" />
      </div>
    </section>
  </div>
</template>

<script setup>
// 添加便签页脚本：接入通用侧边栏以保持左侧导航布局（与广场页一致）
// 说明：
// - sectionsNotes 为本页的侧边栏配置，包含“便签分类”父项与若干子项（网站/影视/音乐/工具/AI）；
// - activeId 用于高亮当前被点击的侧边栏项；
// - 不进行“快捷填充标签”等行为，点击仅改变高亮或折叠状态。 
import { ref, defineAsyncComponent } from 'vue';
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
/* 添加便签页容器：全宽铺满、取消左右内边距，使侧边栏靠页面左边 */
.notes-container { width: 100%; margin: 0; padding: 0; }
/* 顶栏包裹：限制最大宽度并居中，保持与全局 container 一致的视觉 */
.topbar-wrap { 
  /* 居中与宽度限制（与全局 container 一致） */
  max-width: 1080px; margin: 0 auto; padding: 0 16px; 
  /* 页面级吸顶：
     说明：添加便签页的主体可能在不同布局下滚动，为确保顶栏始终固定在视窗顶部，
     将顶栏包裹容器设置为粘性定位，并提升层级避免遮挡。 */
  position: sticky; top: 0; background: #ffffff; z-index: 1000;
}
/* 页面布局：左侧侧边栏 + 右侧内容区 */
.layout { display:flex; align-items:flex-start; gap:16px; }
/* 右侧内容区：恢复为原先的视觉宽度（与全局 .container 接近）
   - 设置最大宽度 1080px，并加入左右 16px 内边距
   - 这样可以在保证左侧导航贴边的同时，右侧编辑区不显得过宽 */
.content-scroll { flex: 1 1 auto; min-width:0; max-width:1080px; padding: 0 16px; }

/* 回退说明：移除了页面级 :deep(.topbar) 吸顶覆写与内容区的渐隐遮罩，
   保持添加便签页原始样式与滚动展示，不影响顶栏组件与其它页面。 */

@media (max-width: 960px){
  .layout { display:block; }
}
</style>