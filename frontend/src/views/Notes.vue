<template>
  <div class="container">
<AppTopBar @search="onSearch" />
    <NotesBody :query="query" />
  </div>
</template>

<script setup>
import { ref, defineAsyncComponent } from 'vue';
const AppTopBar = defineAsyncComponent(() => import('@/components/AppTopBar.vue'));
const NotesBody = defineAsyncComponent(() => import('./notes/NotesBody.vue'));
const query = ref('');
function onSearch(q){ query.value = q || ''; }
</script>