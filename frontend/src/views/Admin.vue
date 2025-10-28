<template>
  <div class="admin-layout">
    <!-- 左侧竖列标题栏 -->
    <aside class="sidebar">
      <div class="brand">
        <img src="https://api.iconify.design/mdi/cog-outline.svg" alt="logo" width="28" height="28" />
        <h1>系统管理</h1>
      </div>
      <el-menu :default-active="active" class="menu" @select="onSelect">
        <el-menu-item index="users">用户管理</el-menu-item>
        <el-menu-item index="logs">日志管理</el-menu-item>
      </el-menu>
      <div class="sidebar-actions">
        <el-button @click="$router.push('/')">返回广场</el-button>
      </div>
    </aside>

    <!-- 右侧上下两栏 -->
    <section class="main">
      <!-- 顶栏小信息 -->
      <div class="topbar">
        <el-alert v-if="!isAdmin" title="仅管理员可访问此页面" type="error" show-icon />
        <template v-else>
          <span>当前功能：{{ activeLabel }}</span>
          <span v-if="summary.total !== null"> · 总数：{{ summary.total }}</span>
        </template>
      </div>

      <!-- 内容栏：动态加载子页面，仅管理员可见 -->
      <div class="content">
        <component v-if="isAdmin" :is="CurrentComp" :updateSummary="updateSummary" />
      </div>
    </section>
  </div>
  </template>

<script setup>
import { ref, computed, defineAsyncComponent, onMounted } from 'vue';
import { http } from '@/api/http';

const isAdmin = ref(false);
const active = ref('users');
const summary = ref({ total: null });

const components = {
  users: defineAsyncComponent(() => import('./admin/AdminUsers.vue')),
  logs: defineAsyncComponent(() => import('./admin/AdminLogs.vue')),
};

const CurrentComp = computed(() => components[active.value]);
const activeLabel = computed(() => (active.value === 'users' ? '用户管理' : '日志管理'));

function onSelect(key) {
  active.value = key;
}

function updateSummary(val) {
  summary.value.total = val?.total ?? null;
}

onMounted(async () => {
  try {
    const { data } = await http.get('/api/account/me');
    isAdmin.value = data && data.role === 'ADMIN';
  } catch {
    isAdmin.value = false;
  }
});
</script>

<style scoped>
.admin-layout { display: grid; grid-template-columns: 240px 1fr; height: 100vh; }
.sidebar { border-right: 1px solid #e5e7eb; padding: 16px; display: flex; flex-direction: column; gap: 12px; }
.brand { display: flex; align-items: center; gap: 8px; }
.brand h1 { font-size: 18px; margin: 0; }
.menu { flex: 1; }
.sidebar-actions { display: flex; gap: 8px; }
.main { display: grid; grid-template-rows: auto 1fr; }
.topbar { border-bottom: 1px solid #e5e7eb; padding: 12px 16px; display: flex; align-items: center; gap: 8px; }
.content { padding: 16px; overflow: auto; }
</style>