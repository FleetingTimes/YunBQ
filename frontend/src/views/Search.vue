<template>
  <!-- 两栏布局：左侧 SideNav（统一结构展示），右侧顶栏 + 搜索结果正文 -->
  <TwoPaneLayout>
    <template #left>
      <SideNav :sections="sections" v-model:activeId="activeId" :alignCenter="true" />
    </template>
    <!-- 全宽顶栏：跨越左右两列并吸顶，顶栏内容全屏铺满 -->
    <template #topFull>
      <AppTopBar fluid @search="onSearch" />
    </template>
    <template #rightMain>
      <div class="container">
        <div class="page-header">
          <h2>搜索结果</h2>
        </div>
        <NotesBody :query="query" :showComposer="false" />
      </div>
    </template>
  </TwoPaneLayout>
  
</template>

<script setup>
import { ref, watch, defineAsyncComponent } from 'vue';
const TwoPaneLayout = defineAsyncComponent(() => import('@/components/TwoPaneLayout.vue'))
const SideNav = defineAsyncComponent(() => import('@/components/SideNav.vue'))
import { useRoute, useRouter } from 'vue-router';

const AppTopBar = defineAsyncComponent(() => import('@/components/AppTopBar.vue'));
const NotesBody = defineAsyncComponent(() => import('./notes/NotesBody.vue'));
import { sideNavSections as sections } from '@/config/navSections'

const route = useRoute();
const router = useRouter();

const query = ref(String(route.query.q || ''));
const activeId = ref('')

function onSearch(q){
  query.value = q || '';
  router.replace({ path: '/search', query: { q: query.value } });
}

watch(() => route.query.q, (nv) => {
  query.value = String(nv || '');
});
</script>

<style scoped>
.page-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }
/* 回退说明：
   - 移除了页面级 :deep(.topbar) 吸顶与层级覆写；
   - 移除了内容区域的顶部渐隐遮罩；
   - 恢复到最初的布局展示，避免对顶栏组件产生间接影响。 */
</style>