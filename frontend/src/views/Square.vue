<template>
  <div class="square-container">
    <NotesTopBar @search="onSearch" />
    <SquareBody :query="query" />
    <div v-if="false">
      <!-- 原广场内容已迁移到 SquareBody 中，保留备份块防止误删 -->
      <header class="square-header">
        <div class="brand">
          <img src="https://api.iconify.design/mdi/notebook-outline.svg" alt="logo" width="28" height="28" />
          <h1>云便签 · 广场</h1>
        </div>
        <div class="actions">
          <el-button @click="$router.push('/login?redirect=%2F')">登录</el-button>
          <el-button type="primary" @click="$router.push('/register')">注册</el-button>
        </div>
      </header>
    </div>
  </div>
  
</template>

<script setup>
import { ref, defineAsyncComponent } from 'vue'

const NotesTopBar = defineAsyncComponent(() => import('./notes/NotesTopBar.vue'))
const SquareBody = defineAsyncComponent(() => import('./square/SquareBody.vue'))

const query = ref('')
function onSearch(q){ query.value = q || '' }
</script>

<style scoped>
.square-container { max-width: 960px; margin: 0 auto; padding: 16px; }
</style>