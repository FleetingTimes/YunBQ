<template>
  <div class="container">
    <div class="page-header">
      <h2>查看记录</h2>
      <el-button link @click="$router.push('/my-notes')">返回我的便签</el-button>
    </div>
    <div class="placeholder">此处展示你浏览或编辑的历史记录。</div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.page-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }
.placeholder { color:#606266; background:#fff; border-radius:12px; padding:16px; box-shadow:0 4px 12px rgba(0,0,0,0.06); }
</style>