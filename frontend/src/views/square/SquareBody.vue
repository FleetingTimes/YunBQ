<template>
  <div>
    <header class="square-header">
      <div class="brand">
        <img src="https://api.iconify.design/mdi/notebook-outline.svg" alt="logo" width="28" height="28" />
        <h1>云便签 · 广场</h1>
      </div>
    </header>

    <section class="hero">
      <h2>记录灵感，分享片段</h2>
      <p>在广场探索大家公开的便签，或登录后开始创作。</p>
      <div class="hero-actions">
        <el-button type="primary" @click="$router.push('/notes')">进入便签</el-button>
        <el-button @click="$router.push('/my-notes')">我的便签</el-button>
      </div>
    </section>

    <section class="placeholder">
      <div class="card">
        <div class="card-title">欢迎来到广场</div>
        <div class="card-desc">后续可在此展示公开便签的弹幕或列表。</div>
      </div>
    </section>
  </div>
</template>

<script setup>
// 可根据父组件传入的 query 加载公开便签；目前内容静态
defineProps({ query: { type: String, default: '' } })
</script>

<style scoped>
.square-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.brand { display: flex; align-items: center; gap: 8px; }
.brand h1 { font-size: 20px; margin: 0; }
.actions { display: flex; gap: 8px; }
.hero { text-align: center; padding: 48px 12px; background: linear-gradient(135deg, #f0f7ff, #ffffff); border: 1px solid #e5e7eb; border-radius: 12px; margin-bottom: 20px; }
.hero h2 { font-size: 24px; margin: 0 0 8px; }
.hero p { color: #606266; margin: 0 0 16px; }
.hero-actions { display: flex; gap: 12px; justify-content: center; }
.placeholder { display: grid; grid-template-columns: 1fr; gap: 12px; }
.card { border: 1px solid #e5e7eb; border-radius: 12px; padding: 16px; background: #fff; }
.card-title { font-weight: 600; margin-bottom: 6px; }
.card-desc { color: #606266; }
</style>