<!--
  管理后台视图（Admin）
  说明：
  - 左侧竖栏菜单：用户管理、导航管理、站点管理、日志管理（审计/请求/认证/错误）；
  - 右侧主区：顶部提示当前功能与管理员权限状态，主体区域按菜单切换具体子页面；
  - 路由守卫：进入本页需管理员权限（meta.requiresAdmin），前端通过后端健康检查确认。
-->
<template>
  <div class="admin-layout">
    <!-- 左侧竖列标题栏 -->
    <aside class="sidebar">
      <div class="brand">
        <img src="https://api.iconify.design/mdi/cog-outline.svg" alt="logo" width="28" height="28" />
        <h1>系统管理</h1>
      </div>
      <el-menu :default-active="active" class="menu" @select="onSelect">
        <!-- 系统管理主项：用户管理 -->
        <el-menu-item index="users">用户管理</el-menu-item>
        <!-- 内容管理 -->
        <el-menu-item index="navigation">导航管理</el-menu-item>
        <el-menu-item index="sites">站点管理</el-menu-item>
        <!-- 日志管理：按类型拆分为独立子页面，便于分别筛选与查看 -->
        <el-menu-item index="logs_audit">审计日志</el-menu-item>
        <el-menu-item index="logs_request">请求日志</el-menu-item>
        <el-menu-item index="logs_auth">认证日志</el-menu-item>
        <el-menu-item index="logs_error">错误日志</el-menu-item>
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
        <span class="spacer"></span>
        <el-button type="primary" size="small" @click="$router.push('/')">退出管理</el-button>
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

// 动态子页面映射：按菜单选择加载对应管理页面
const components = {
  users: defineAsyncComponent(() => import('./admin/AdminUsers.vue')),
  // 内容管理
  navigation: defineAsyncComponent(() => import('./admin/AdminNavigation.vue')),
  sites: defineAsyncComponent(() => import('./admin/AdminSites.vue')),
  // 审计日志（系统操作或审计事件）
  logs_audit: defineAsyncComponent(() => import('./admin/AdminLogs.vue')),
  // 请求日志（HTTP 请求流量与状态）
  logs_request: defineAsyncComponent(() => import('./admin/AdminLogsRequest.vue')),
  // 认证日志（登录/令牌校验）
  logs_auth: defineAsyncComponent(() => import('./admin/AdminLogsAuth.vue')),
  // 错误日志（未处理异常）
  logs_error: defineAsyncComponent(() => import('./admin/AdminLogsError.vue')),
};

const CurrentComp = computed(() => components[active.value]);
// 顶栏当前功能提示：根据选择的子页面显示友好中文
const activeLabel = computed(() => {
  switch(active.value){
    case 'users': return '用户管理';
    case 'navigation': return '导航管理';
    case 'sites': return '站点管理';
    case 'logs_audit': return '审计日志';
    case 'logs_request': return '请求日志';
    case 'logs_auth': return '认证日志';
    case 'logs_error': return '错误日志';
    default: return '系统管理';
  }
});

function onSelect(key) {
  active.value = key;
}

function updateSummary(val) {
  summary.value.total = val?.total ?? null;
}

onMounted(async () => {
  try {
  // 说明：管理员页在加载时会获取当前用户信息。
  // 若未登录或 token 暂时校验失败，后端可能返回 401。
  // 这里设置 suppress401Redirect=true，避免全局拦截器清除 token 或强制重定向，
  // 由页面自身决定如何处理（例如展示“请登录”或引导至登录页）。
  const { data } = await http.get('/account/me', { suppress401Redirect: true });
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
.topbar .spacer { flex: 1; }
.content { padding: 16px; overflow: auto; }
</style>