<!--
  应用根组件（App.vue）
  说明：
  - 仅渲染 `<router-view />` 以承载各路由页面；
  - 底部统一透明页脚（TransparentFooter）在所有页面保持一致；
  - 全局样式入口可在此组件 `<style>` 中扩展。
-->
<template>
  <router-view />
  <TransparentFooter />
</template>

<script setup>
import TransparentFooter from '@/components/TransparentFooter.vue'
</script>

<style>
/* 全局路由视图样式可在此扩展 */
</style>
