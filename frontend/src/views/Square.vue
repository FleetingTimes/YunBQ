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
.square-container { max-width: 960px; margin: 0 auto; padding: 16px; }
</style>