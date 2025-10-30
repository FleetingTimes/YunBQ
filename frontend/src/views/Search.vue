<template>
  <!-- 统一顶栏结构：将顶栏放置在页面容器之外，并使用 topbar-wrap 控制宽度与左右安全边距
       这样每个页面的顶栏都以一致的 1080px 最大宽度居中显示，不受各页面内部 .container/.square-container 的宽度与内边距影响 -->
  <div class="topbar-wrap">
    <AppTopBar @search="onSearch" />
  </div>
  <div class="container">
    <!-- 回退：移除页面级顶栏吸顶与内容渐隐遮罩，恢复原始布局与滚动行为 -->
    <div class="page-header">
      <h2>搜索结果</h2>
    </div>
    <NotesBody :query="query" :showComposer="false" />
  </div>
  
</template>

<script setup>
import { ref, watch, defineAsyncComponent } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const AppTopBar = defineAsyncComponent(() => import('@/components/AppTopBar.vue'));
const NotesBody = defineAsyncComponent(() => import('./notes/NotesBody.vue'));

const route = useRoute();
const router = useRouter();

const query = ref(String(route.query.q || ''));

function onSearch(q){
  query.value = q || '';
  router.replace({ path: '/search', query: { q: query.value } });
}

watch(() => route.query.q, (nv) => {
  query.value = String(nv || '');
});
</script>

<style scoped>
.topbar-wrap { 
  /* 统一宽度与居中显示 */
  max-width: 1080px; margin: 0 auto; padding: 0 16px; 
  /* 页面级吸顶：
     - 将顶栏外层容器设为粘性定位，确保在本页的滚动上下文中也能固定于顶部；
     - 设置背景与层级，避免与下方内容发生视觉冲突。 */
  position: sticky; top: 0; background: #ffffff; z-index: 1000;
}
.page-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }
/* 回退说明：
   - 移除了页面级 :deep(.topbar) 吸顶与层级覆写；
   - 移除了内容区域的顶部渐隐遮罩；
   - 恢复到最初的布局展示，避免对顶栏组件产生间接影响。 */
</style>