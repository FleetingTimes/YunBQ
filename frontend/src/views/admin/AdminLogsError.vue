<template>
  <div class="admin-logs">
    <!-- 工具栏：错误日志筛选项（异常类名/requestId） -->
    <div class="toolbar">
      <el-input v-model="exception" placeholder="按异常类名模糊匹配" clearable style="width: 240px" />
      <el-input v-model="requestId" placeholder="按 requestId 精确匹配" clearable style="width: 240px" />
      <el-button type="primary" :loading="loading" @click="reload">筛选</el-button>
      <el-button @click="reset">重置</el-button>
      <!-- 导出错误日志：支持CSV/JSON；携带当前筛选条件 -->
      <el-dropdown>
        <el-button type="success">
          导出错误日志<el-icon style="margin-left:4px"><i-ep-arrow-down /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="exportAll('csv')">导出为 CSV</el-dropdown-item>
            <el-dropdown-item @click="exportAll('json')">导出为 JSON</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 列表：错误日志数据（支持查看堆栈详情） -->
    <el-table :data="items" v-loading="loading" border stripe style="width:100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="createdAt" label="时间" width="180" />
      <el-table-column prop="userId" label="用户ID" width="120" />
      <el-table-column prop="path" label="路径" />
      <el-table-column prop="exception" label="异常类名" width="200" />
      <el-table-column prop="message" label="错误消息" />
      <el-table-column prop="requestId" label="requestId" width="220" />
      <el-table-column label="操作" width="120">
        <template #default="scope">
          <el-button size="small" @click="openStack(scope.row)">查看堆栈</el-button>
        </template>
      </el-table-column>
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

    <!-- 堆栈查看对话框 -->
    <el-dialog v-model="stackVisible" title="堆栈详情" width="60%">
      <pre class="stack-pre">{{ currentStack }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
// 错误日志管理页面：
// 作用：按异常类名与 requestId 分页查询 error_logs，并支持查看堆栈详情；
// 说明：堆栈较长，使用弹窗预格式化显示，便于开发/运维排障。
import { ref, onMounted } from 'vue';
import { http } from '@/api/http';
import { exportErrorLogs } from '@/api/admin';

const props = defineProps({ updateSummary: { type: Function, default: null } });

const loading = ref(false);
const items = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const exception = ref('');
const requestId = ref('');

const stackVisible = ref(false);
const currentStack = ref('');

async function fetchData(){
  loading.value = true;
  try {
    const params = { page: page.value, size: size.value };
    if (exception.value) params.exception = exception.value;
    if (requestId.value) params.requestId = requestId.value;
    const { data } = await http.get('/admin/error-logs', { params });
    items.value = data.items || [];
    total.value = data.total || 0;
    if (props.updateSummary) props.updateSummary({ total: total.value });
  } finally {
    loading.value = false;
  }
}

function reload(){ page.value = 1; fetchData(); }
function reset(){ exception.value=''; requestId.value=''; reload(); }
function onPageChange(p){ page.value = p; fetchData(); }
function onSizeChange(s){ size.value = s; page.value = 1; fetchData(); }

function openStack(row){
  // 打开堆栈详情弹窗：若为空则显示占位提示
  currentStack.value = String(row.stackTrace || '无堆栈信息');
  stackVisible.value = true;
}

/**
 * 导出错误日志（CSV/JSON）。
 * 实现：调用后端导出接口获取Blob，创建临时链接并触发下载；文件名随格式变化。
 */
async function exportAll(format){
  const params = {};
  if (exception.value) params.exception = exception.value;
  if (requestId.value) params.requestId = requestId.value;
  const blob = await exportErrorLogs(params, format || 'csv');
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `error-logs.${format==='json'?'json':'csv'}`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

onMounted(fetchData);
</script>

<style scoped>
.admin-logs { display: grid; grid-template-rows: auto 1fr auto; gap: 12px; }
.toolbar { display: flex; align-items: center; gap: 8px; }
.pager { display: flex; justify-content: flex-end; }
.stack-pre { white-space: pre-wrap; word-break: break-word; background: #f7f7f9; padding: 12px; border-radius: 6px; }
</style>