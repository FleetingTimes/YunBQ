<template>
  <div class="admin-users">
    <div class="toolbar">
      <el-input v-model="q" placeholder="搜索用户名/昵称/邮箱" clearable @keyup.enter="reload" style="max-width: 280px" />
      <el-button type="primary" :loading="loading" @click="reload">搜索</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table :data="items" v-loading="loading" border stripe style="width:100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" width="160" />
      <el-table-column prop="nickname" label="昵称" width="160" />
      <el-table-column prop="email" label="邮箱" width="220" />
      <el-table-column prop="role" label="角色" width="120" />
      <el-table-column prop="createdAt" label="创建时间" />
    </el-table>

    <div class="pager">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="page"
        :page-size="size"
        :page-sizes="[10, 20, 50]"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { http } from '@/api/http';

const props = defineProps({ updateSummary: { type: Function, default: null } });

const loading = ref(false);
const items = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const q = ref('');

async function fetchData() {
  loading.value = true;
  try {
    const { data } = await http.get('/admin/users', { params: { page: page.value, size: size.value, q: q.value || undefined } });
    items.value = data.items || [];
    total.value = data.total || 0;
    if (props.updateSummary) props.updateSummary({ total: total.value });
  } finally {
    loading.value = false;
  }
}

function reload() {
  page.value = 1;
  fetchData();
}

function reset() {
  q.value = '';
  reload();
}

function onPageChange(p) {
  page.value = p;
  fetchData();
}

function onSizeChange(s) {
  size.value = s;
  page.value = 1;
  fetchData();
}

onMounted(fetchData);
</script>

<style scoped>
.admin-users { display: grid; grid-template-rows: auto 1fr auto; gap: 12px; }
.toolbar { display: flex; align-items: center; gap: 8px; }
.pager { display: flex; justify-content: flex-end; }
</style>