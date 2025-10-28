<template>
  <div class="container">
    <NotesTopBar @search="onSearch" />
    <NotesBody :query="query" />
  </div>
</template>

<script setup>
import { ref, defineAsyncComponent } from 'vue';
const NotesTopBar = defineAsyncComponent(() => import('./notes/NotesTopBar.vue'));
const NotesBody = defineAsyncComponent(() => import('./notes/NotesBody.vue'));
const query = ref('');
function onSearch(q){ query.value = q || ''; }
</script>