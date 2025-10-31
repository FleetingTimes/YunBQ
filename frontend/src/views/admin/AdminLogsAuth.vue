<template>
  <div class="admin-logs">
    <!-- 工具栏：认证日志筛选项（用户名/成功与否/requestId） -->
    <div class="toolbar">
      <el-input v-model="username" placeholder="按用户名模糊匹配" clearable style="width: 240px" />
      <el-select v-model="success" placeholder="成功/失败" clearable style="width: 160px">
        <el-option label="成功" :value="true" />
        <el-option label="失败" :value="false" />
      </el-select>
      <el-input v-model="requestId" placeholder="按 requestId 精确匹配" clearable style="width: 240px" />
      <el-button type="primary" :loading="loading" @click="reload">筛选</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <!-- 列表：认证日志数据 -->
    <el-table :data="items" v-loading="loading" border stripe style="width:100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="createdAt" label="时间" width="180" />
      <el-table-column prop="userId" label="用户ID" width="120" />
      <el-table-column prop="username" label="用户名" width="200" />
      <el-table-column prop="success" label="结果" width="100">
        <template #default="scope">{{ scope.row.success ? '成功' : '失败' }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="失败原因" />
      <el-table-column prop="ip" label="IP" width="160" />
      <el-table-column prop="userAgent" label="UA" />
      <el-table-column prop="requestId" label="requestId" width="220" />
    </el-table>

    <!-- 分页器 -->
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
// 认证日志管理页面：
// 作用：按用户名、成功/失败与 requestId 对 auth_logs 进行分页查询；
// 说明：requestId 可用于串联同一次请求的认证与错误日志，便于定位问题。
import { ref, onMounted } from 'vue';
import { http } from '@/api/http';

const props = defineProps({ updateSummary: { type: Function, default: null } });

const loading = ref(false);
const items = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const username = ref('');
const success = ref(undefined);
const requestId = ref('');

async function fetchData(){
  loading.value = true;
  try {
    const params = { page: page.value, size: size.value };
    if (username.value) params.username = username.value;
    if (typeof success.value === 'boolean') params.success = success.value;
    if (requestId.value) params.requestId = requestId.value;
    const { data } = await http.get('/admin/auth-logs', { params });
    items.value = data.items || [];
    total.value = data.total || 0;
    if (props.updateSummary) props.updateSummary({ total: total.value });
  } finally {
    loading.value = false;
  }
}

function reload(){ page.value = 1; fetchData(); }
function reset(){ username.value=''; success.value=undefined; requestId.value=''; reload(); }
function onPageChange(p){ page.value = p; fetchData(); }
function onSizeChange(s){ size.value = s; page.value = 1; fetchData(); }

onMounted(fetchData);
</script>

<style scoped>
.admin-logs { display: grid; grid-template-rows: auto 1fr auto; gap: 12px; }
.toolbar { display: flex; align-items: center; gap: 8px; }
.pager { display: flex; justify-content: flex-end; }
</style>