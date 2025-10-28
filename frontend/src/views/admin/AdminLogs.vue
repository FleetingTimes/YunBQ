<template>
  <div class="admin-logs">
    <div class="toolbar">
      <el-select v-model="level" placeholder="筛选级别" clearable style="width: 160px">
        <el-option label="INFO" value="INFO" />
        <el-option label="WARN" value="WARN" />
        <el-option label="ERROR" value="ERROR" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="reload">筛选</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table :data="items" v-loading="loading" border stripe style="width:100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="createdAt" label="时间" width="200" />
      <el-table-column prop="level" label="级别" width="100" />
      <el-table-column prop="userId" label="用户ID" width="120" />
      <el-table-column prop="message" label="消息" />
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
const level = ref('');

async function fetchData() {
  loading.value = true;
  try {
    const { data } = await http.get('/admin/logs', { params: { page: page.value, size: size.value, level: level.value || undefined } });
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
  level.value = '';
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
.admin-logs { display: grid; grid-template-rows: auto 1fr auto; gap: 12px; }
.toolbar { display: flex; align-items: center; gap: 8px; }
.pager { display: flex; justify-content: flex-end; }
</style>