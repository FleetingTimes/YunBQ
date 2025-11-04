<template>
  <div class="admin-users">
    <div class="toolbar">
      <el-input v-model="q" placeholder="搜索用户名/昵称/邮箱" clearable @keyup.enter="reload" style="max-width: 280px" />
      <el-button type="primary" :loading="loading" @click="reload">搜索</el-button>
      <el-button @click="reset">重置</el-button>
      <!-- 新增用户：打开“新增用户”弹窗，支持设置基础信息与可选密码 -->
      <el-button type="primary" plain @click="openAddDialog" style="margin-left: 8px;">新增用户</el-button>
      <!-- 导入用户：通过隐藏的文件选择器触发上传 JSON 文件 -->
      <el-button type="success" :loading="importing" @click="triggerUserImport" style="margin-left: 8px;">
        导入用户
      </el-button>
      <!-- 导出用户：支持 CSV 和 JSON 格式 -->
      <el-dropdown @command="handleExport" style="margin-left: 8px;">
        <el-button type="info" :loading="exporting">
          导出用户 <el-icon class="el-icon--right"><arrow-down /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="csv">导出为 CSV</el-dropdown-item>
            <el-dropdown-item command="json">导出为 JSON</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <!-- 高级导出：包含密码哈希，需二次确认 -->
      <el-button type="danger" :loading="advExporting" @click="openAdvancedExportDialog" style="margin-left: 8px;">
        高级导出
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

    <!--
      用户列表表格：展示基础信息 + 头像地址 + 密码状态
      说明：
      - 密码不展示真实值，仅展示"已设置/未设置"状态标签；
      - 头像地址显示为可点击链接，同时展示 32×32 缩略图；
      - 角色沿用后端返回的字符串（ADMIN/USER）。
    -->
    <el-table :data="items" v-loading="loading" border stripe style="width:100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" width="160" />
      <el-table-column prop="nickname" label="昵称" width="160" />
      <el-table-column prop="email" label="邮箱" width="220" />
      <el-table-column prop="role" label="角色" width="120" />
      <!-- 密码状态列：不显示密码，仅显示是否已设置 -->
      <el-table-column label="密码" width="120">
        <template #default="{ row }">
          <el-tag :type="row.hasPassword ? 'success' : 'warning'">
            {{ row.hasPassword ? '已设置' : '未设置' }}
          </el-tag>
        </template>
      </el-table-column>
      <!-- 头像地址列：显示缩略图与可点击链接 -->
      <el-table-column label="头像地址" width="260">
        <template #default="{ row }">
          <div class="avatar-cell" v-if="row.avatarUrl">
            <img :src="avatarFullUrl(row.avatarUrl)" alt="avatar" class="avatar-thumb" />
            <a :href="avatarFullUrl(row.avatarUrl)" target="_blank" rel="noopener">{{ avatarFullUrl(row.avatarUrl) }}</a>
          </div>
          <span v-else class="text-muted">(无)</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" />
      <!-- 操作列：编辑与删除 -->
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
          <el-button link type="danger" @click="confirmDelete(row)">删除</el-button>
        </template>
      </el-table-column>
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

  <!-- 高级导出确认弹窗：包含格式选择与强确认输入 -->
  <el-dialog v-model="advExportVisible" title="高级导出确认" width="520px">
    <!-- 提示文案：明确包含敏感信息与使用场景 -->
    <p>
      该操作将导出所有用户完整信息（包含敏感的 <code>passwordHash</code>）。
      此功能仅用于数据迁移或备份，请确保在安全环境下执行并妥善保存导出文件。
    </p>
    <p style="color:#c33; font-weight:600;">风险提示：导出文件一旦泄露将带来严重安全风险！</p>

    <!-- 导出格式选择：CSV/JSON -->
    <div style="margin-top:12px;">
      <label style="display:block;margin-bottom:6px;">选择导出格式：</label>
      <el-radio-group v-model="advExportFormat">
        <el-radio-button label="csv">CSV（含表头，UTF-8 BOM）</el-radio-button>
        <el-radio-button label="json">JSON（完整字段）</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 强确认输入：必须输入大写 CONFIRM 才能继续 -->
    <div style="margin-top:16px;">
      <label style="display:block;margin-bottom:6px;">输入大写 <code>CONFIRM</code> 以确认：</label>
      <el-input v-model="advConfirmText" placeholder="请输入 CONFIRM 以继续" />
    </div>

    <template #footer>
      <el-button @click="cancelAdvancedExport" :disabled="advExporting">取消</el-button>
      <el-button type="danger" :loading="advExporting" @click="confirmAdvancedExport">确认导出</el-button>
    </template>
  </el-dialog>

  <!-- 新增用户弹窗：设置基础信息与可选密码 -->
  <el-dialog v-model="addVisible" title="新增用户" width="560px">
    <!-- 说明：用户创建时 username 必填；password 为可选明文，后端统一哈希；role 默认 USER。 -->
    <el-form :model="addForm" label-width="96px">
      <el-form-item label="用户名" required>
        <el-input v-model="addForm.username" placeholder="请输入用户名" />
      </el-form-item>
      <el-form-item label="昵称">
        <el-input v-model="addForm.nickname" placeholder="可选：昵称" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="addForm.email" placeholder="可选：邮箱" />
      </el-form-item>
      <el-form-item label="签名">
        <el-input v-model="addForm.signature" placeholder="可选：个性签名" />
      </el-form-item>
      <el-form-item label="头像URL">
        <el-input v-model="addForm.avatarUrl" placeholder="可选：/uploads/avatars/xxx.png 或 http://..." />
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="addForm.role" placeholder="请选择角色">
          <el-option label="管理员" value="ADMIN" />
          <el-option label="普通用户" value="USER" />
        </el-select>
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="addForm.password" type="password" placeholder="可选：不填则为不可登录用户" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="addVisible = false" :disabled="addSubmitting">取消</el-button>
      <el-button type="primary" :loading="addSubmitting" @click="submitAdd">创建</el-button>
    </template>
  </el-dialog>

  <!-- 编辑用户弹窗：允许更新基础信息与可选重置密码 -->
  <el-dialog v-model="editVisible" title="编辑用户" width="560px">
    <!-- 说明：编辑时可修改 username/email（后端做唯一性检查），密码为可选重置（明文）。 -->
    <el-form :model="editForm" label-width="96px">
      <el-form-item label="用户名">
        <el-input v-model="editForm.username" placeholder="用户名" />
      </el-form-item>
      <el-form-item label="昵称">
        <el-input v-model="editForm.nickname" placeholder="昵称" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="editForm.email" placeholder="邮箱" />
      </el-form-item>
      <el-form-item label="签名">
        <el-input v-model="editForm.signature" placeholder="个性签名" />
      </el-form-item>
      <el-form-item label="头像URL">
        <el-input v-model="editForm.avatarUrl" placeholder="/uploads/avatars/xxx.png 或 http://..." />
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="editForm.role" placeholder="角色">
          <el-option label="管理员" value="ADMIN" />
          <el-option label="普通用户" value="USER" />
        </el-select>
      </el-form-item>
      <el-form-item label="重置密码">
        <el-input v-model="editForm.password" type="password" placeholder="可选：留空则不修改密码" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false" :disabled="editSubmitting">取消</el-button>
      <el-button type="primary" :loading="editSubmitting" @click="submitEdit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { ArrowDown } from '@element-plus/icons-vue';
import { http } from '@/api/http';
import { importUsers, exportUsersAdvanced, createUser, updateUser, deleteUser } from '@/api/admin';

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
// 导出相关状态
const exporting = ref(false);
// 高级导出相关状态
const advExportVisible = ref(false);
const advExportFormat = ref('csv'); // 默认 CSV
const advConfirmText = ref('');
const advExporting = ref(false);

// 新增/编辑弹窗与表单状态
const addVisible = ref(false);
const addSubmitting = ref(false);
const addForm = ref({
  // 字段说明：与后端 AdminController#createUser 对齐
  username: '', // 必填：用户名（唯一）
  nickname: '',
  email: '',
  signature: '',
  avatarUrl: '',
  role: 'USER', // 默认普通用户
  password: '' // 可选明文密码：不填则为不可登录用户
});

const editVisible = ref(false);
const editSubmitting = ref(false);
// 记录当前编辑的用户ID（后端需要）
const editingId = ref(null);
const editForm = ref({
  // 字段说明：与后端 AdminController#updateUser 对齐
  username: '',
  nickname: '',
  email: '',
  signature: '',
  avatarUrl: '',
  role: 'USER',
  password: '' // 可选：设置则重置密码；留空不更新密码
});

/**
 * 将后端返回的头像地址转换为可访问的完整 URL。
 * 兼容情况：
 * - 若已是完整 URL（以 http:// 或 https:// 开头）直接返回；
 * - 若为相对路径（如 /uploads/avatars/xxx.png），拼接后端基础地址。
 */
function avatarFullUrl(url) {
  if (!url) return '';
  if (/^https?:\/\//i.test(url)) return url;
  // 约定：后端上传目录通过静态资源映射为 /uploads/** 可直接访问；
  // 若生产环境有反向代理，请将 BASE_API 指向网关域名。
  const BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api';
  // 当 url 已以 / 开头，去掉 api 前缀，确保访问的是静态资源路径
  return url.startsWith('/') ? (BASE.replace(/\/api$/, '') + url) : (BASE.replace(/\/api$/, '') + '/' + url);
}

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
    // 新增：在上传前解析并转换 JSON 内容，以实现迁移场景下的密码字段识别与保留
    // 目标：避免对已是 BCrypt 哈希的密码进行二次加密；若提供明文密码，则由后端进行统一哈希
    // 步骤：
    // 1) 读取文件文本并解析为数组；
    // 2) 遍历每个用户对象，检测 password/passwordHash 字段是否为 BCrypt 格式；
    //    - 若存在 passwordHash 且为 BCrypt：保留为 passwordHash，移除 password（若存在）；
    //    - 若仅存在 password 且为 BCrypt：转移为 passwordHash 并移除 password；
    //    - 若存在明文 password：保留 password（由后端进行 BCrypt 哈希）；
    //    - 若不提供密码：保持为空，后端不更新密码；
    // 3) 将转换后的数组重新打包为 Blob 并通过现有 import 接口上传。

    // 读取文件内容
    const text = await file.text();
    let users = [];
    try {
      const parsed = JSON.parse(text);
      if (!Array.isArray(parsed)) {
        throw new Error('导入文件内容必须是 JSON 数组');
      }
      users = parsed;
    } catch (parseErr) {
      console.error('解析导入 JSON 失败：', parseErr);
      throw new Error(parseErr.message || '解析导入 JSON 失败');
    }

    // BCrypt 哈希格式检测：
    // 说明：BCrypt 哈希通常形如 $2a$10$... / $2b$12$... / $2y$...，总长度约 60 字符。
    // 这里使用较严格的正则进行识别，避免误判普通字符串为哈希。
    const BCRYPT_REGEX = /^\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}$/;

    // 字段转换：确保迁移过程中不对已哈希密码进行二次加密
    const transformed = users.map((u, idx) => {
      const item = { ...u };
      const pwd = item.password;
      const pwdHash = item.passwordHash;

      // 若存在 passwordHash 且符合 BCrypt 格式：保留为 passwordHash
      if (typeof pwdHash === 'string' && BCRYPT_REGEX.test(pwdHash)) {
        delete item.password; // 移除可能存在的明文字段，避免后端误处理
        return item;
      }

      // 若仅提供 password 且为 BCrypt 格式：转移到 passwordHash
      if (typeof pwd === 'string' && BCRYPT_REGEX.test(pwd)) {
        item.passwordHash = pwd;
        delete item.password; // 删除明文字段，避免后端二次加密
        return item;
      }

      // 若提供的是明文密码：保留为 password，由后端统一进行 BCrypt 哈希
      if (typeof pwd === 'string' && pwd.length > 0) {
        // 保留 password，不设置 passwordHash，让后端进行加密
        return item;
      }

      // 未提供密码：不设置 password/passwordHash，后端应保持原密码不变
      delete item.password;
      delete item.passwordHash;
      return item;
    });

    // 重新打包为 Blob，并复用现有上传接口（multipart/form-data，字段名依旧为 file）
    const transformedBlob = new Blob([JSON.stringify(transformed)], { type: 'application/json' });
    const result = await importUsers(transformedBlob);

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

/**
 * 处理导出用户数据
 * 支持 CSV 和 JSON 格式，会根据当前搜索条件过滤导出数据
 * @param {string} format - 导出格式：'csv' 或 'json'
 */
async function handleExport(format) {
  if (exporting.value) return;
  
  exporting.value = true;
  try {
    // 构建导出请求参数，包含当前搜索条件
    const params = {
      format: format,
      q: q.value || undefined
    };
    
    // 调用后端导出接口，获取文件数据
    const response = await http.get('/admin/users/export', {
      params,
      responseType: 'blob' // 重要：指定响应类型为 blob 以处理二进制数据
    });
    
    // 从响应头获取文件名
    const contentDisposition = response.headers['content-disposition'];
    let filename = `users.${format}`;
    if (contentDisposition) {
      const filenameMatch = contentDisposition.match(/filename=(.+)/);
      if (filenameMatch) {
        filename = filenameMatch[1];
      }
    }
    
    // 创建下载链接并触发下载
    const blob = new Blob([response.data]);
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    
    // 清理资源
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    
    // 显示成功提示
    const formatName = format === 'csv' ? 'CSV' : 'JSON';
    ElMessage.success(`${formatName} 文件导出成功！`);
    
  } catch (err) {
    console.error('导出用户失败：', err);
    ElMessage.error(`导出失败：${err?.message || '请检查网络连接与服务器状态'}`);
  } finally {
    exporting.value = false;
  }
}

/**
 * 打开高级导出确认弹窗。
 * 说明：高级导出会包含敏感字段（passwordHash），因此在触发前弹窗提示风险并要求确认。
 */
function openAdvancedExportDialog() {
  advConfirmText.value = '';
  advExportFormat.value = 'csv';
  advExportVisible.value = true;
}

/**
 * 执行高级导出：包含密码哈希等敏感字段。
 * 安全措施：要求用户在确认框输入“CONFIRM”以明确同意风险。
 */
async function confirmAdvancedExport() {
  if (advExporting.value) return;
  // 简单的强确认：必须输入完整大写单词 CONFIRM
  if (advConfirmText.value.trim() !== 'CONFIRM') {
    ElMessage.error('请输入大写的 CONFIRM 以确认敏感导出');
    return;
  }
  advExporting.value = true;
  try {
    const params = { q: q.value || undefined };
    const blob = await exportUsersAdvanced(params, advExportFormat.value);
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `users-advanced.${advExportFormat.value === 'json' ? 'json' : 'csv'}`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    const formatName = advExportFormat.value === 'json' ? 'JSON' : 'CSV';
    ElMessage.success(`高级导出（${formatName}）成功。请妥善保管包含密码哈希的备份文件！`);
    advExportVisible.value = false;
  } catch (err) {
    console.error('高级导出失败：', err);
    ElMessage.error(`高级导出失败：${err?.message || '请检查网络连接与服务器状态'}`);
  } finally {
    advExporting.value = false;
  }
}

function cancelAdvancedExport() {
  if (advExporting.value) return;
  advExportVisible.value = false;
}

/**
 * 打开新增用户弹窗并重置表单
 */
function openAddDialog() {
  addForm.value = { username: '', nickname: '', email: '', signature: '', avatarUrl: '', role: 'USER', password: '' };
  addVisible.value = true;
}

/**
 * 提交新增用户：调用后端创建接口
 * 成功后关闭弹窗并刷新列表；失败提示具体错误（如 409 冲突）。
 */
async function submitAdd() {
  if (addSubmitting.value) return;
  if (!addForm.value.username || !addForm.value.username.trim()) {
    ElMessage.error('用户名为必填项');
    return;
  }
  addSubmitting.value = true;
  try {
    const payload = { ...addForm.value };
    const data = await createUser(payload);
    ElMessage.success(`创建成功（ID=${data.id}）`);
    addVisible.value = false;
    reload();
  } catch (err) {
    const msg = err?.response?.data?.message || err?.message || '创建失败';
    ElMessage.error(msg);
  } finally {
    addSubmitting.value = false;
  }
}

/**
 * 打开编辑弹窗并填充当前行数据
 */
function openEditDialog(row) {
  editingId.value = row.id;
  editForm.value = {
    username: row.username || '',
    nickname: row.nickname || '',
    email: row.email || '',
    signature: row.signature || '',
    avatarUrl: row.avatarUrl || '',
    role: row.role || 'USER',
    password: ''
  };
  editVisible.value = true;
}

/**
 * 提交编辑保存：调用后端更新接口
 * 成功后关闭弹窗并刷新列表；失败提示具体错误（如 409 冲突）。
 */
async function submitEdit() {
  if (editSubmitting.value) return;
  editSubmitting.value = true;
  try {
    const payload = { ...editForm.value };
    // 若未设置密码，避免发送空字符串（后端会将空字符串视为非空）
    if (!payload.password) delete payload.password;
    const data = await updateUser(editingId.value, payload);
    ElMessage.success(`保存成功（ID=${data.id}）`);
    editVisible.value = false;
    reload();
  } catch (err) {
    const msg = err?.response?.data?.message || err?.message || '保存失败';
    ElMessage.error(msg);
  } finally {
    editSubmitting.value = false;
  }
}

/**
 * 删除确认与执行
 */
async function confirmDelete(row) {
  // 简单确认：可替换为 ElMessageBox 确认弹窗
  const ok = window.confirm(`确认删除用户：${row.username}（ID=${row.id}）？`);
  if (!ok) return;
  try {
    await deleteUser(row.id);
    ElMessage.success('删除成功');
    reload();
  } catch (err) {
    const msg = err?.response?.data?.message || err?.message || '删除失败';
    ElMessage.error(msg);
  }
}
</script>

<style scoped>
.admin-users { display: grid; grid-template-rows: auto 1fr auto; gap: 12px; }
.toolbar { display: flex; align-items: center; gap: 8px; }
.pager { display: flex; justify-content: flex-end; }
/* 头像缩略图样式：32x32，圆角 */
.avatar-cell { display: flex; align-items: center; gap: 8px; }
.avatar-thumb { width: 32px; height: 32px; border-radius: 4px; object-fit: cover; }
.text-muted { color: #999; }
</style>