<template>
  <div class="container">
    <div class="page-header">
      <h2>查看记录</h2>
<!-- 文案重命名：将“便签”统一改为“拾言”，保持与品牌一致 -->
<!-- 路由重命名说明：跳转至 /my-shiyan，保留 /my-notes 作为别名以兼容旧路径 -->
<el-button link @click="$router.push('/my-shiyan')">返回我的拾言</el-button>
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