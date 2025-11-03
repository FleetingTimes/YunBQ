<template>
  <div class="admin-users">
    <div class="toolbar">
      <el-input v-model="q" placeholder="搜索用户名/昵称/邮箱" clearable @keyup.enter="reload" style="max-width: 280px" />
      <el-button type="primary" :loading="loading" @click="reload">搜索</el-button>
      <el-button @click="reset">重置</el-button>
      <!-- 导入用户：通过隐藏的文件选择器触发上传 JSON 文件 -->
      <el-button type="success" :loading="importing" @click="triggerUserImport" style="margin-left: 8px;">
        导入用户
      </el-button>
      <!-- 隐藏文件选择器：只接受 .json 文件，选择后触发 onUserFileChange -->
      <input
        ref="userImportInput"
        type="file"
        accept="application/json,.json"
        style="display:none"
        @change="onUserFileChange"
      />
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
import { ElMessage } from 'element-plus';
import { http } from '@/api/http';
import { importUsers } from '@/api/admin';

const props = defineProps({ updateSummary: { type: Function, default: null } });

const loading = ref(false);
const items = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const q = ref('');
// 导入相关状态与引用
const importing = ref(false);
const userImportInput = ref(null);

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

/**
 * 触发隐藏的文件选择器，选择用于导入的 JSON 文件
 * 说明：采用隐藏 input + 按钮触发的方式，保持界面简洁；
 * - 限制文件类型为 .json；
 * - 用户选择后调用 onUserFileChange 处理上传；
 */
function triggerUserImport() {
  if (userImportInput.value) {
    userImportInput.value.click();
  }
}

/**
 * 处理用户选择的导入文件，调用后端导入接口
 * 约定：后端批量导入接口字段名固定为 `file`，内容为 JSON 数组。
 * 提示：导入完成后刷新列表，并清空 input 的值以允许重复选择同名文件。
 */
async function onUserFileChange(e) {
  const file = e.target.files?.[0];
  if (!file) return;
  // 简单的类型校验：仅接受 JSON
  if (!file.name.endsWith('.json')) {
    ElMessage.error('请选择 JSON 文件（后缀 .json）');
    e.target.value = '';
    return;
  }
  importing.value = true;
  try {
    const result = await importUsers(file);
    // 统计信息提示：总数、创建数、更新数
    ElMessage.success(`导入完成：总计 ${result.total}，新增 ${result.created}，更新 ${result.updated}`);
    reload();
  } catch (err) {
    console.error('导入用户失败：', err);
    ElMessage.error(`导入失败：${err?.message || '请检查文件格式与服务器日志'}`);
  } finally {
    importing.value = false;
    // 允许重复导入同名文件，需要清空 input 的值
    e.target.value = '';
  }
}
</script>

<style scoped>
.admin-users { display: grid; grid-template-rows: auto 1fr auto; gap: 12px; }
.toolbar { display: flex; align-items: center; gap: 8px; }
.pager { display: flex; justify-content: flex-end; }
</style>