<template>
  <div class="admin-page">
    <header class="admin-header">
      <div class="brand">
        <img src="https://api.iconify.design/mdi/cog-outline.svg" alt="logo" width="28" height="28" />
        <h1>系统管理</h1>
      </div>
      <div class="spacer"></div>
      <el-button @click="$router.push('/')">返回广场</el-button>
    </header>

    <el-alert v-if="!isAdmin" title="仅管理员可访问此页面" type="error" show-icon class="mb-12" />

    <el-tabs v-model="activeTab" type="border-card" v-if="isAdmin">
      <el-tab-pane label="用户管理" name="users">
        <div class="toolbar">
          <el-button type="primary" @click="loadUsers" :loading="loadingUsers">刷新</el-button>
          <span class="tip">接口预留：后续将提供角色变更、禁用/启用等操作。</span>
        </div>
        <el-table :data="users" style="width: 100%" v-loading="loadingUsers" empty-text="暂时没有数据或接口待接入">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="username" label="用户名" width="180" />
          <el-table-column prop="nickname" label="昵称" width="180" />
          <el-table-column prop="email" label="邮箱" width="220" />
          <el-table-column prop="role" label="角色" width="120" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="日志管理" name="logs">
        <div class="toolbar">
          <el-button type="primary" @click="loadLogs" :loading="loadingLogs">刷新</el-button>
          <span class="tip">接口预留：后续将接入系统审计日志与操作日志。</span>
        </div>
        <el-table :data="logs" style="width: 100%" v-loading="loadingLogs" empty-text="暂时没有数据或接口待接入">
          <el-table-column prop="time" label="时间" width="200" />
          <el-table-column prop="level" label="级别" width="100" />
          <el-table-column prop="message" label="消息" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { http } from '@/api/http';

const isAdmin = ref(false);
const activeTab = ref('users');

const users = ref([]);
const loadingUsers = ref(false);

const logs = ref([]);
const loadingLogs = ref(false);

onMounted(async () => {
  // 二次确认管理员身份，避免前端被篡改
  try {
    const { data } = await http.get('/api/account/me');
    isAdmin.value = data && data.role === 'ADMIN';
    if (isAdmin.value) {
      loadUsers();
    }
  } catch (e) {
    isAdmin.value = false;
  }
});

async function loadUsers() {
  loadingUsers.value = true;
  try {
    // 预留：真实接口 /api/admin/users（尚未实现），当前用占位数据
    users.value = [
      { id: 1, username: 'alice', nickname: 'Alice', email: 'alice@example.com', role: 'USER' },
      { id: 2, username: 'bob', nickname: 'Bob', email: 'bob@example.com', role: 'ADMIN' },
    ];
  } finally {
    loadingUsers.value = false;
  }
}

async function loadLogs() {
  loadingLogs.value = true;
  try {
    // 预留：真实接口 /api/admin/logs（尚未实现），当前用占位数据
    logs.value = [
      { time: '2025-10-28 10:00:00', level: 'INFO', message: '系统启动完成' },
      { time: '2025-10-28 10:15:12', level: 'WARN', message: '用户 alice 多次登录失败' },
    ];
  } finally {
    loadingLogs.value = false;
  }
}
</script>

<style scoped>
.admin-page { max-width: 1080px; margin: 0 auto; padding: 16px; }
.admin-header { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.brand { display: flex; align-items: center; gap: 8px; }
.brand h1 { font-size: 20px; margin: 0; }
.spacer { flex: 1; }
.mb-12 { margin-bottom: 12px; }
.toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.tip { color: #606266; }
</style>