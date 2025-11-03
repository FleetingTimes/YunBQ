<template>
  <div class="admin-logs">
    <!-- 工具栏：请求日志筛选项（URI/状态码/requestId） -->
    <div class="toolbar">
      <el-input v-model="uri" placeholder="按 URI 模糊匹配" clearable style="width: 240px" />
      <el-select v-model="status" placeholder="状态码" clearable style="width: 140px">
        <el-option v-for="s in statusOpts" :key="s" :label="s" :value="s" />
      </el-select>
      <el-input v-model="requestId" placeholder="按 requestId 精确匹配" clearable style="width: 240px" />
      <el-button type="primary" :loading="loading" @click="reload">筛选</el-button>
      <el-button @click="reset">重置</el-button>
      <!-- 导出请求日志：支持CSV/JSON；携带当前筛选条件 -->
      <el-dropdown>
        <el-button type="success">
          导出请求日志<el-icon style="margin-left:4px"><i-ep-arrow-down /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="exportAll('csv')">导出为 CSV</el-dropdown-item>
            <el-dropdown-item @click="exportAll('json')">导出为 JSON</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 列表：请求日志数据 -->
    <el-table :data="items" v-loading="loading" border stripe style="width:100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="createdAt" label="时间" width="180" />
      <el-table-column prop="method" label="方法" width="100" />
      <el-table-column prop="status" label="状态码" width="100" />
      <el-table-column prop="durationMs" label="耗时(ms)" width="120" />
      <el-table-column prop="uri" label="URI" />
      <el-table-column prop="ip" label="IP" width="160" />
      <el-table-column prop="userAgent" label="UA" />
      <el-table-column prop="userId" label="用户ID" width="120" />
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
// 请求日志管理页面：
// 作用：按 URI、状态码、requestId 对 request_logs 进行分页查询与展示；
// 说明：requestId 可用于跨表串联到认证/错误日志页面，支持快速排障定位。
import { ref, onMounted } from 'vue';
import { http } from '@/api/http';
import { exportRequestLogs } from '@/api/admin';

const props = defineProps({ updateSummary: { type: Function, default: null } });

const loading = ref(false);
const items = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const uri = ref('');
const status = ref(undefined);
const requestId = ref('');
const statusOpts = [200, 201, 204, 400, 401, 403, 404, 500];

async function fetchData(){
  loading.value = true;
  try {
    const params = { page: page.value, size: size.value };
    if (uri.value) params.uri = uri.value;
    if (typeof status.value === 'number') params.status = status.value;
    if (requestId.value) params.requestId = requestId.value;
    const { data } = await http.get('/admin/request-logs', { params });
    items.value = data.items || [];
    total.value = data.total || 0;
    if (props.updateSummary) props.updateSummary({ total: total.value });
  } finally {
    loading.value = false;
  }
}

function reload(){ page.value = 1; fetchData(); }
function reset(){ uri.value=''; status.value=undefined; requestId.value=''; reload(); }
function onPageChange(p){ page.value = p; fetchData(); }
function onSizeChange(s){ size.value = s; page.value = 1; fetchData(); }

onMounted(fetchData);

/**
 * 导出请求日志（CSV/JSON）。
 * 实现：调用后端导出接口获取Blob，创建临时链接并触发下载；文件名随格式变化。
 */
async function exportAll(format){
  const params = {};
  if (uri.value) params.uri = uri.value;
  if (typeof status.value === 'number') params.status = status.value;
  if (requestId.value) params.requestId = requestId.value;
  const blob = await exportRequestLogs(params, format || 'csv');
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `request-logs.${format==='json'?'json':'csv'}`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
</script>

<style scoped>
.admin-logs { display: grid; grid-template-rows: auto 1fr auto; gap: 12px; }
.toolbar { display: flex; align-items: center; gap: 8px; }
.pager { display: flex; justify-content: flex-end; }
</style>