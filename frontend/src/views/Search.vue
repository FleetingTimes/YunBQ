<template>
  <div class="container">
    <AppTopBar @search="onSearch" />
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
.page-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }
</style>