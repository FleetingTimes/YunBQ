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

    <div class="danmu-section">
      <div class="danmu-track" v-for="row in danmuRowList" :key="row" :style="trackStyle(row)">
        <div class="danmu-item" v-for="item in danmuItemsForRow(row)" :key="item.id" :style="danmuStyle(item)">
           <span class="danmu-text">{{ item.content }}</span>
           <span class="like-badge" @click.stop="toggleLikeById(item.id)">{{ item.liked ? '♥' : '♡' }} {{ item.likeCount || 0 }}</span>
         </div>
      </div>
    </div>

    <div class="grid">
      <!-- 便签列表改为顶部弹幕展示，移除卡片列表 -->

      <div class="sticky composer p-2 rot-2">
        <div class="title">新建便签</div>
        <!-- 移除标题输入框 -->
        <!-- <el-input v-model="draft.title" placeholder="标题" style="margin-bottom:6px;" /> -->
        <el-input v-model="draft.tags" placeholder="标签（用逗号分隔）" style="margin-bottom:6px;" />
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
import { ElMessage } from 'element-plus';

const router = useRouter();
const q = ref('');
const notes = ref([]);
// 新增：标记刚创建的便签 ID 或回退为首条
const justCreatedId = ref(null);
const justCreatedFirst = ref(false);

// 顶部弹幕行数与数据
const danmuRows = 6;
const danmuRowList = computed(() => Array.from({ length: danmuRows }, (_, i) => i + 1));
// 增加弹幕速度系数，使整体更慢一些
const danmuSpeedScale = 1.35;

function hueFromNote(n, idx) {
  const tagsStr = Array.isArray(n.tags) ? n.tags.join(',') : (n.tags || '');
  const s = (n.content || '') + tagsStr + String(n.id ?? idx);
  let hash = 0;
  for (let i = 0; i < s.length; i++) {
    hash = (hash * 31 + s.charCodeAt(i)) >>> 0;
  }
  return hash % 360;
}

const danmuCache = ref({});
const danmuItems = computed(() => notes.value.map((n, idx) => {
  const h = hueFromNote(n, idx);
  const cached = danmuCache.value[n.id] || {
    row: (idx % danmuRows) + 1,
    delay: Math.random() * 8,
    duration: 15, // 固定持续时间15秒，确保所有弹幕速度一致
  };
  danmuCache.value[n.id] = cached;
  return {
    id: n.id,
    content: n.content,
    row: cached.row,
    delay: cached.delay,
    duration: cached.duration,
    h,
    bg: `hsla(${h}, 85%, 88%, 0.95)`,
    fg: `hsl(${h}, 35%, 22%)`,
    liked: !!n.liked,
    likeCount: n.likeCount || 0,
  };
}));
function danmuItemsForRow(row) {
  return danmuItems.value.filter(i => i.row === row);
}
function danmuStyle(it) {
  return {
    animationDuration: (it.duration * danmuSpeedScale) + 's',
    animationDelay: it.delay + 's',
    background: it.bg,
    color: it.fg,
    border: `1px solid hsla(${it.h}, 70%, 80%, 1)`
  };
}
function trackStyle(row) {
  const h = 100 / danmuRows;
  return { top: ((row - 1) * h) + '%', height: h + '%' };
}
// 更新：移除 title 字段
const draft = reactive({ content: '', isPublic: false, tags: '' });
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
    const items = Array.isArray(data) ? data : (data?.items ?? data?.records ?? []);
    // 统一字段命名，兼容后端 SNAKE_CASE
    notes.value = (items || []).map(it => ({
      ...it,
      isPublic: it.isPublic ?? it.is_public ?? false,
      likeCount: Number(it.likeCount ?? it.like_count ?? 0),
      liked: Boolean(it.liked ?? it.likedByMe ?? it.liked_by_me ?? false),
    }));
    // 新增：若刚创建，则让对应弹幕立即开始移动（delay=0）
    if (justCreatedId.value != null || justCreatedFirst.value) {
      let targetId = justCreatedId.value;
      if (!targetId && notes.value.length > 0) targetId = notes.value[0].id;
      const idx = notes.value.findIndex(n => n.id === targetId);
      const row = (idx >= 0 ? (idx % danmuRows) + 1 : 1);
      danmuCache.value[targetId] = { row, delay: 0, duration: 15 }; // 固定持续时间15秒
      justCreatedId.value = null;
      justCreatedFirst.value = false;
    }
  }catch(e){
    ElMessage.error('加载便签失败');
  }
}

async function toggleLike(n){
  if (n.likeLoading) return;
  n.likeLoading = true;
  try{
    const url = n.liked ? `/notes/${n.id}/unlike` : `/notes/${n.id}/like`;
    const { data } = await http.post(url);
    n.likeCount = Number(data?.count ?? data?.like_count ?? (n.likeCount || 0));
    n.liked = Boolean((data?.likedByMe ?? data?.liked_by_me ?? n.liked));
  }catch(e){
    ElMessage.error('操作失败');
  }finally{
    n.likeLoading = false;
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
  if (!draft.content) { ElMessage.warning('请填写内容'); return; }
  try{
    // 更新：移除 title 字段
    const payload = { content: draft.content, is_public: draft.isPublic, tags: (draft.tags || '').trim() };
    const { data } = await http.post('/notes', payload);
    // 新增：记录刚创建的便签，令其弹幕 delay = 0
    const createdId = data?.id ?? data?.note?.id ?? data?.data?.id ?? null;
    if (createdId) justCreatedId.value = createdId; else justCreatedFirst.value = true;
    ElMessage.success('已添加');
    // 更新：移除 title 重置
    draft.content = ''; draft.tags = '';
    draft.isPublic = false;
    load();
  }catch(e){
    const status = e?.response?.status;
    if (status === 401){
      ElMessage.error('未登录，请先登录');
      router.replace('/login');
    } else if (status === 403){
      ElMessage.error('无权限，请检查登录状态或稍后重试');
    } else {
      ElMessage.error(e?.response?.data?.message || e?.message || '添加失败');
    }
  }
}

async function togglePublic(n){
  try{
    const tagsStr = Array.isArray(n.tags) ? n.tags.join(',') : (n.tags || '');
    const currentPublic = (n.isPublic ?? n.is_public ?? false);
    // 更新：移除 title 字段 + 修正 is_public
    const payload = { content: n.content, tags: tagsStr, archived: n.archived, is_public: !currentPublic };
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
function parsedTags(tags){
  if (Array.isArray(tags)) return tags;
  if (typeof tags === 'string') return tags.split(',').map(t => t.trim()).filter(Boolean);
  return [];
}
function toggleLikeById(id){
  const n = notes.value.find(x => x.id === id);
  if (!n) return;
  if (n.likeLoading === undefined) n.likeLoading = false;
  toggleLike(n);
}
</script>
<style scoped>
.danmu-section {
  position: relative;
  height: 33vh; /* 上方区域：高度为下方的 1/2，即占 1/3 页面 */
  overflow: hidden;
  background: transparent;
  border-bottom: 1px solid #e5e7eb;
}
.danmu-track {
  position: absolute;
  left: 0;
  width: 100%;
}
.danmu-item {
  position: absolute;
  right: -200px;
  white-space: nowrap;
  color: #333;
  background: rgba(255,255,255,0.85);
  border-radius: 16px;
  padding: 6px 12px;
  box-shadow: 0 2px 6px rgba(0,0,0,0.08);
  animation-name: danmu-move;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
  animation-fill-mode: backwards;
  will-change: transform;
}
@keyframes danmu-move {
  0% { transform: translateX(100vw); }
  100% { transform: translateX(-120vw); }
}
.danmu-item:hover {
  animation-play-state: paused;
  cursor: pointer;
}
.like-badge {
  display: inline-block;
  margin-left: 8px;
  font-weight: 600;
  color: currentColor;
  background: rgba(255,255,255,0.6);
  border-radius: 12px;
  padding: 2px 6px;
}
.danmu-text {
  display: inline-block;
}
</style>