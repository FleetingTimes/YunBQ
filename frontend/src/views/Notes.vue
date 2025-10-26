<template>
  <div class="container">
    <div class="header">
      <div class="brand">
        <img src="https://api.iconify.design/mdi/notebook-outline.svg" alt="logo" width="24" height="24" />
        <h1>云便签</h1>
      </div>
      <div class="search">
        <el-input v-model="q" placeholder="搜索便签..." clearable style="width:240px;" />
        <el-button type="primary" @click="load">搜索</el-button>
        <el-popover v-model:visible="profileVisible" placement="bottom-end" :width="280">
          <template #reference>
            <div style="display:flex; align-items:center; gap:8px; cursor:pointer;" @click="profileVisible = true">
              <img v-if="me.avatarUrl" :src="avatarUrl" alt="avatar" style="width:32px;height:32px;border-radius:50%;object-fit:cover;border:2px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,0.15);background:#fff;" />
              <img v-else src="https://api.iconify.design/mdi/account-circle.svg" alt="avatar" style="width:32px;height:32px;border-radius:50%;background:#fff;" />
              <span style="font-weight:500; color:#303133;">{{ me.nickname || me.username }}</span>
            </div>
          </template>
          <div style="display:flex; gap:12px; align-items:center; margin-bottom:8px;">
            <img v-if="me.avatarUrl" :src="avatarUrl" alt="avatar" style="width:56px;height:56px;border-radius:50%;object-fit:cover;border:2px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,0.15);background:#fff;" />
            <img v-else src="https://api.iconify.design/mdi/account-circle.svg" alt="avatar" style="width:56px;height:56px;border-radius:50%;background:#fff;" />
            <div>
              <div style="font-weight:600;">{{ me.nickname || '未设置昵称' }}</div>
              <div style="color:#606266; font-size:12px;">用户名：{{ me.username }}</div>
              <div style="color:#606266; font-size:12px;">邮箱：{{ me.email || '未绑定' }}</div>
            </div>
          </div>
          <div style="display:flex; justify-content:flex-end; gap:8px;">
            <el-button size="small" @click="openEditInfo">修改信息</el-button>
            <el-button size="small" type="warning" @click="logout">退出登录</el-button>
          </div>
        </el-popover>
      </div>
    </div>

    <el-dialog v-model="editVisible" title="修改信息" width="420px">
      <div style="display:flex; gap:12px; align-items:center; margin-bottom:12px;">
        <img v-if="me.avatarUrl" :src="avatarUrl" alt="avatar" style="width:56px;height:56px;border-radius:50%;object-fit:cover;border:2px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,0.15);background:#fff;" />
        <img v-else src="https://api.iconify.design/mdi/account-circle.svg" alt="avatar" style="width:56px;height:56px;border-radius:50%;background:#fff;" />
        <el-upload :show-file-list="false" accept="image/*" :http-request="uploadAvatar">
          <el-button size="small">更换头像</el-button>
        </el-upload>
      </div>
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="昵称">
          <el-input v-model="editForm.nickname" placeholder="输入新昵称" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.email" placeholder="输入邮箱" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div style="display:flex; justify-content:flex-end; gap:8px;">
          <el-button @click="editVisible=false">取消</el-button>
          <el-button type="primary" @click="saveEditInfo" :loading="editLoading">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <div class="grid">
      <div v-for="n in notes" :key="n.id" class="sticky" :class="paperClass(n) + ' ' + rotClass(n)">
        <div class="ribbon" v-if="n.archived">已归档</div>
        <div class="actions">
          <el-button size="small" @click="archive(n)">{{ n.archived ? '取消归档' : '归档' }}</el-button>
          <el-button size="small" type="danger" @click="remove(n)">删除</el-button>
        </div>
        <div class="title">{{ n.title }}</div>
        <div class="meta" style="display:flex; justify-content:space-between; align-items:center; margin:4px 0;">
          <el-tag size="small" :type="n.isPublic ? 'success' : 'info'">{{ n.isPublic ? '公开' : '私有' }}</el-tag>
          <el-button size="small" type="primary" link @click="togglePublic(n)">{{ n.isPublic ? '设为私有' : '设为公开' }}</el-button>
        </div>
        <div class="content">{{ n.content }}</div>
        <div class="tags">
          <el-tag v-for="t in n.tags" :key="t" type="info" size="small">{{ t }}</el-tag>
        </div>
      </div>

      <div class="sticky composer p-2 rot-2">
        <div class="title">新建便签</div>
        <el-input v-model="draft.title" placeholder="标题" style="margin-bottom:6px;" />
        <el-input v-model="draft.content" type="textarea" :rows="4" placeholder="内容" />
        <div style="display:flex; align-items:center; justify-content:space-between; margin-top:6px;">
          <el-switch v-model="draft.isPublic" active-text="公开" inactive-text="私有" />
          <div class="auth-actions" style="justify-content:flex-end;">
            <el-button type="primary" @click="create">添加</el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="footer">
      <el-tag type="info">共 {{ notes.length }} 条</el-tag>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { http } from '@/api/http';
import { avatarFullUrl } from '@/api/http';
import { clearToken } from '@/utils/auth';

const router = useRouter();
const q = ref('');
const notes = ref([]);
const draft = reactive({ title: '', content: '', isPublic: false });
const me = reactive({ username:'', nickname:'', avatarUrl:'', email:'' });
const profileVisible = ref(false);
const editVisible = ref(false);
const editLoading = ref(false);
const editForm = reactive({ nickname:'', email:'' });

onMounted(() => { load(); loadMe(); });

const avatarUrl = computed(() => avatarFullUrl(me.avatarUrl));

async function loadMe(){
  try{
    const { data } = await http.get('/account/me');
    Object.assign(me, data);
  }catch(e){
    if (e?.response?.status === 401) router.replace('/login');
  }
}

async function load(){
  try{
    const { data } = await http.get('/notes', { params: { q: q.value } });
    notes.value = Array.isArray(data) ? data : (data?.items || []);
  }catch(e){
    ElMessage.error('加载便签失败');
  }
}

function openEditInfo(){
  editForm.nickname = me.nickname || '';
  editForm.email = me.email || '';
  editVisible.value = true;
  profileVisible.value = false;
}

async function uploadAvatar(req){
  try{
    const form = new FormData();
    form.append('file', req.file);
    await http.post('/account/avatar', form);
    await loadMe();
    ElMessage.success('头像已更新');
  }catch(e){
    ElMessage.error(e?.response?.data?.message || '上传失败');
  }
}

async function saveEditInfo(){
  editLoading.value = true;
  try{
    if(editForm.nickname && editForm.nickname !== me.nickname){
      await http.post('/account/update-nickname', { nickname: editForm.nickname });
    }
    if(editForm.email && editForm.email !== me.email){
      // 简化：直接绑定邮箱，不涉及验证码流程
      await http.post('/account/bind-email/confirm', { email: editForm.email, code: '000000' });
    }
    await loadMe();
    editVisible.value = false;
    ElMessage.success('信息已保存');
  }catch(e){
    ElMessage.error(e?.response?.data?.message || '保存失败');
  }finally{
    editLoading.value = false;
  }
}

function paperClass(n){
  const idx = (n.id || 0) % 6 + 1;
  return 'p-' + idx;
}
function rotClass(n){
  const idx = (n.id || 0) % 4;
  return 'rot-' + idx;
}

async function archive(n){
  try{
    await http.post(`/notes/${n.id}/archive`, { archived: !n.archived });
    ElMessage.success('已更新归档状态');
    load();
  }catch(e){
    ElMessage.error('更新归档失败');
  }
}

async function remove(n){
  try{
    await http.delete(`/notes/${n.id}`);
    ElMessage.success('已删除');
    load();
  }catch(e){
    ElMessage.error('删除失败');
  }
}

async function create(){
  if (!draft.title || !draft.content) { ElMessage.warning('请填写标题与内容'); return; }
  try{
    await http.post('/notes', draft);
    ElMessage.success('已添加');
    draft.title = ''; draft.content = '';
    draft.isPublic = false;
    load();
  }catch(e){
    ElMessage.error('添加失败');
  }
}

async function togglePublic(n){
  try{
    const payload = { title: n.title, content: n.content, tags: n.tags, archived: n.archived, isPublic: !n.isPublic };
    await http.put(`/notes/${n.id}`, payload);
    ElMessage.success('已更新可见性');
    load();
  }catch(e){
    ElMessage.error('更新可见性失败');
  }
}

function logout(){
  clearToken();
  router.replace('/login');
}
</script>