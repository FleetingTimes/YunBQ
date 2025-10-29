<template>
  <div class="header topbar">
    <div class="brand" @click="goSquare" style="cursor:pointer;">
      <img src="https://api.iconify.design/mdi/notebook-outline.svg" alt="logo" width="24" height="24" />
      <h1>云便签</h1>
    </div>
    <div class="center-search">
      <el-input
        v-model="q"
        placeholder="搜索便签..."
        clearable
        class="top-search-input"
        @keyup.enter="emitSearch"
        @clear="emitSearch"
      >
        <template #prefix>
          <img src="https://api.iconify.design/mdi/magnify.svg" alt="search" width="18" height="18" />
        </template>
      </el-input>
    </div>
    <div class="right-actions" aria-label="快捷入口">
      <el-tooltip content="消息" placement="bottom">
        <el-button link class="icon-btn" @click="goMessages">
          <img src="https://api.iconify.design/mdi/message-outline.svg" alt="消息" width="22" height="22" />
        </el-button>
      </el-tooltip>
      <el-tooltip content="喜欢" placement="bottom">
        <el-button link class="icon-btn" @click="goLikes">
          <img src="https://api.iconify.design/mdi/heart.svg" alt="喜欢" width="22" height="22" />
        </el-button>
      </el-tooltip>
      <el-tooltip content="收藏" placement="bottom">
        <el-button link class="icon-btn" @click="goFavorites">
          <img src="https://api.iconify.design/mdi/bookmark-outline.svg" alt="收藏" width="22" height="22" />
        </el-button>
      </el-tooltip>
      <el-tooltip content="记录" placement="bottom">
        <el-button link class="icon-btn" @click="goHistory">
          <img src="https://api.iconify.design/mdi/history.svg" alt="记录" width="22" height="22" />
        </el-button>
      </el-tooltip>
      <template v-if="authed">
      <div class="profile-trigger" @mouseenter="onHoverEnter" @mouseleave="onHoverLeave">
        <div class="trigger-ref" style="display:flex; align-items:center; gap:8px; cursor:pointer;">
          <img v-if="me.avatarUrl" :src="avatarUrl" alt="avatar" style="width:32px;height:32px;border-radius:50%;object-fit:cover;border:2px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,0.15);background:#fff;" />
          <img v-else src="https://api.iconify.design/mdi/account-circle.svg" alt="avatar" style="width:32px;height:32px;border-radius:50%;background:#fff;" />
          <span style="font-weight:500; color:#303133;">{{ me.nickname || me.username }}</span>
        </div>
        <div class="profile-card" v-show="profileVisible" @mouseenter="onHoverEnter" @mouseleave="onHoverLeave">
          <div class="profile-header">
            <!-- 管理员：右上齿轮；右下添加便签 -->
            <div class="top-actions" v-if="isAdmin" style="position:absolute; right:10px; top:10px; display:flex; gap:8px; z-index:1;">
              <el-tooltip content="系统管理" placement="left">
                <el-button circle size="small" @click="goAdmin" title="系统管理">
                  <img src="https://api.iconify.design/mdi/cog-outline.svg?color=%23409eff" alt="" aria-hidden="true" width="18" height="18" />
                </el-button>
              </el-tooltip>
            </div>
            <div class="add-entry-bottom" v-if="isAdmin">
              <el-tooltip content="添加便签" placement="left">
                <el-button circle size="small" @click="goNotes" title="添加便签">
                  <img src="https://api.iconify.design/mdi/note-plus-outline.svg?color=%23409eff" alt="add" aria-hidden="true" width="18" height="18" />
                </el-button>
              </el-tooltip>
            </div>
            <!-- 普通用户右上角添加便签入口 -->
            <div class="add-entry" v-if="!isAdmin">
              <el-tooltip content="添加便签" placement="left">
                <el-button circle size="small" @click="goNotes" title="添加便签">
                  <img src="https://api.iconify.design/mdi/note-plus-outline.svg?color=%23409eff" alt="add" aria-hidden="true" width="18" height="18" />
                </el-button>
              </el-tooltip>
            </div>
            <div class="avatar-line">
              <div class="avatar-wrap">
                <img v-if="me.avatarUrl" :src="avatarUrl" alt="avatar" class="avatar-lg" />
                <img v-else src="https://api.iconify.design/mdi/account-circle.svg" alt="avatar" class="avatar-lg" />
              </div>
              <div class="name-box">
                <div class="nickname">{{ me.nickname || '未设置昵称' }}</div>
              </div>
            </div>
          </div>
          <div class="profile-info-list">
            <div class="row">
              <span class="label">昵称</span>
              <span class="value">{{ me.nickname || '未设置昵称' }}</span>
            </div>
            
            <div class="row">
              <span class="label">签名</span>
              <span class="value" :class="signatureExpanded ? 'signature-full' : 'signature-ellipsis'" :title="me.signature || '未设置'">
                {{ me.signature || '未设置' }}
              </span>
              <el-button v-if="me.signature" link size="small" @click="signatureExpanded = !signatureExpanded">
                {{ signatureExpanded ? '收起' : '展开' }}
              </el-button>
            </div>
          </div>
          <div class="profile-actions">
            <el-button size="small" type="success" @click="goMyNotes">我的便签</el-button>
            <el-button size="small" @click="openMyInfo">我的信息</el-button>
            <el-button size="small" type="warning" @click="logout">退出登录</el-button>
          </div>
        </div>
      </div>
      </template>
      <template v-else>
        <div style="display:flex; gap:8px;">
          <el-button @click="goLogin">登录</el-button>
          <el-button type="primary" @click="goRegister">注册</el-button>
        </div>
      </template>
    </div>

    <el-dialog v-model="editVisible" :title="infoEditing ? '编辑信息' : '我的信息'" width="420px">
      <div class="dialog-header" style="display:flex; flex-direction:column; gap:8px; align-items:center; margin-bottom:12px; position:relative;">
        <img v-if="me.avatarUrl" :src="avatarUrl" alt="avatar" style="width:56px;height:56px;border-radius:50%;object-fit:cover;border:2px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,0.15);background:#fff;" />
        <img v-else src="https://api.iconify.design/mdi/account-circle.svg" alt="avatar" style="width:56px;height:56px;border-radius:50%;background:#fff;" />
        <template v-if="infoEditing">
          <el-upload :show-file-list="false" accept="image/*" :http-request="uploadAvatar">
            <el-button size="small">更换头像</el-button>
          </el-upload>
        </template>
        <el-button v-if="!infoEditing" class="edit-icon" circle size="small" @click="infoEditing = true" title="编辑信息" aria-label="编辑信息" role="button">
          <img src="https://api.iconify.design/mdi/pencil.svg" alt="edit" width="18" height="18" />
        </el-button>
      </div>

      <transition name="fade-slide" mode="out-in">
        <div v-if="!infoEditing" class="info-card">
          <div class="info-list">
            <div class="info-row">
              <span class="label">
                <img class="label-icon" src="https://api.iconify.design/mdi/account-outline.svg?color=%23409eff" alt="" aria-hidden="true" width="16" height="16" />
                <span>昵称：</span>
              </span>
              <span>{{ me.nickname || '未设置昵称' }}</span>
            </div>
            <div class="info-row">
              <span class="label">
                <img class="label-icon" src="https://api.iconify.design/mdi/account-box-outline.svg?color=%23409eff" alt="" aria-hidden="true" width="16" height="16" />
                <span>用户名：</span>
              </span>
              <span>{{ me.username }}</span>
            </div>
            <div class="info-row">
              <span class="label">
                <img class="label-icon" src="https://api.iconify.design/mdi/email-outline.svg?color=%23409eff" alt="" aria-hidden="true" width="16" height="16" />
                <span>邮箱：</span>
              </span>
              <span>{{ me.email || '未绑定' }}</span>
            </div>
            <div class="info-row">
              <span class="label">
                <img class="label-icon" src="https://api.iconify.design/mdi/format-quote-close.svg?color=%23409eff" alt="" aria-hidden="true" width="16" height="16" />
                <span>签名：</span>
              </span>
              <span class="signature-ellipsis">{{ me.signature || '未设置' }}</span>
            </div>
          </div>
        </div>

        <div v-else class="edit-form-card">
          <el-form :model="editForm" label-width="96px" label-position="left">
          <el-form-item label="昵称">
            <el-input v-model="editForm.nickname" placeholder="输入新昵称" />
          </el-form-item>
          <el-form-item label="用户名">
            <el-input v-model="editForm.username" disabled placeholder="用户名不可更改" />
          </el-form-item>
          <el-form-item label="个性签名">
            <el-input
              v-model="editForm.signature"
              type="textarea"
              :rows="2"
              :autosize="{ minRows: 2, maxRows: 2 }"
              maxlength="255"
              show-word-limit
              placeholder="填写一句话签名，最多255字符"
            />
            <div class="help">最多 255 字，建议简洁表达你的状态或座右铭</div>
          </el-form-item>
          <el-form-item label="邮箱">
            <div style="display:flex; align-items:center; gap:8px; width:100%;">
              <el-input v-model="editForm.email" placeholder="输入邮箱" style="flex:1 1 auto;" />
              <el-button
                class="send-code-btn"
                :style="{ '--p': (sendCountdown>0 ? (sendCountdown/60) : 0) }"
                :disabled="sendCountdown>0 || !editForm.email || sendLoading"
                @click="sendEmailCode"
                :loading="sendLoading"
              >
                {{ sendCountdown>0 ? (sendCountdown + 's') : (sentOnce ? '重新发送' : '发送验证码') }}
              </el-button>
            </div>
          </el-form-item>
          <el-form-item label="验证码">
            <el-input ref="codeInputRef" v-model="editForm.code" placeholder="输入收到的验证码" maxlength="6" />
          </el-form-item>
          </el-form>
        </div>
      </transition>
      <template #footer>
        <div v-if="!infoEditing" style="display:flex; justify-content:flex-end; gap:8px;">
          <el-button @click="editVisible=false">关闭</el-button>
        </div>
        <div v-else style="display:flex; justify-content:flex-end; gap:8px;">
          <el-button @click="editVisible=false">取消</el-button>
          <el-button type="primary" @click="saveEditInfo" :loading="editLoading">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { http, avatarFullUrl } from '@/api/http'
import { clearToken } from '@/utils/auth'
import { ElMessage } from 'element-plus'

const emit = defineEmits(['search'])

const router = useRouter()
const route = useRoute()
const q = ref('')
const me = reactive({ username:'', nickname:'', avatarUrl:'', email:'', role:'', signature:'' })
const profileVisible = ref(false)
const editVisible = ref(false)
const infoEditing = ref(false)
const editLoading = ref(false)
const editForm = reactive({ username:'', nickname:'', email:'', code:'', signature:'' })
const sendLoading = ref(false)
const sendCountdown = ref(0)
const sentOnce = ref(false)
const codeInputRef = ref(null)
let sendTimer = null
let hoverHideTimer = null
const signatureExpanded = ref(false)

const avatarUrl = computed(() => avatarFullUrl(me.avatarUrl))
const isAdmin = computed(() => (me.role || '').toUpperCase() === 'ADMIN')
const authed = computed(() => !!(me.username))

function emitSearch(){
  emit('search', q.value)
}

onMounted(() => { loadMe() })

async function loadMe(){
  try{
    const { data } = await http.get('/account/me')
    Object.assign(me, data)
  }catch(e){
    if (e?.response?.status === 401) router.replace('/')
  }
}

function openEditInfo(){
  // 保留旧方法：直接进入编辑模式
  editForm.username = me.username || ''
  editForm.nickname = me.nickname || ''
  editForm.signature = me.signature || ''
  editForm.email = me.email || ''
  editForm.code = ''
  infoEditing.value = true
  editVisible.value = true
  profileVisible.value = false
}

function openMyInfo(){
  // 新入口：默认查看模式
  editForm.username = me.username || ''
  editForm.nickname = me.nickname || ''
  editForm.signature = me.signature || ''
  editForm.email = me.email || ''
  editForm.code = ''
  infoEditing.value = false
  editVisible.value = true
  profileVisible.value = false
}

async function uploadAvatar(req){
  try{
    const form = new FormData()
    form.append('file', req.file)
    await http.post('/account/avatar', form)
    await loadMe()
    ElMessage.success('头像已更新')
  }catch(e){
    ElMessage.error(e?.response?.data?.message || '上传失败')
  }
}

async function saveEditInfo(){
  editLoading.value = true
  try{
    if(editForm.nickname && editForm.nickname !== me.nickname){
      await http.post('/account/update-nickname', { nickname: editForm.nickname })
    }
    const sigTrim = (editForm.signature || '').trim()
    const meSig = me.signature || ''
    if(sigTrim !== meSig){
      await http.post('/account/update-signature', { signature: sigTrim })
    }
    if(editForm.email && editForm.email !== me.email){
      if (!editForm.code || editForm.code.trim().length !== 6){
        throw { response: { data: { message: '请填写 6 位验证码' } } }
      }
      await http.post('/account/bind-email/confirm', { email: editForm.email.trim(), code: editForm.code.trim() })
    }
    await loadMe()
    editVisible.value = false
    ElMessage.success('信息已保存')
  }catch(e){
    ElMessage.error(e?.response?.data?.message || '保存失败')
  }finally{
    editLoading.value = false
  }
}

async function sendEmailCode(){
  if (sendLoading.value) return
  try{
    const email = (editForm.email || '').trim()
    if (!email) { ElMessage.warning('请输入邮箱'); return }
    const re = /^[\w.-]+@[\w.-]+\.[A-Za-z]{2,}$/
    if (!re.test(email)) { ElMessage.warning('邮箱格式不正确'); return }
    sendLoading.value = true
    await http.post('/account/bind-email/send-code', { email })
    ElMessage.success('验证码已发送，请查收邮箱')
    sentOnce.value = true
    sendCountdown.value = 60
    if (sendTimer) clearInterval(sendTimer)
    sendTimer = setInterval(() => {
      sendCountdown.value -= 1
      if (sendCountdown.value <= 0){ clearInterval(sendTimer); sendTimer = null }
    }, 1000)
    requestAnimationFrame(() => { codeInputRef.value?.focus?.() })
  }catch(e){
    const msg = e?.response?.data?.message || '发送失败，请稍后再试'
    ElMessage.error(msg)
  }finally{
    sendLoading.value = false
  }
}

function logout(){
  clearToken()
  // 立即更新顶栏状态与关闭弹层
  profileVisible.value = false
  me.username = ''
  me.nickname = ''
  me.avatarUrl = ''
  me.email = ''
  me.role = ''
  // 保持在当前页（如广场），但触发路由替换以刷新依赖路由的组件
  const to = route.fullPath || '/'
  router.replace(to)
}
function goMyNotes(){
  profileVisible.value = false
  router.push('/my-notes')
}
function goAdmin(){
  profileVisible.value = false
  router.push('/admin')
}
function goNotes(){
  // 跳转到 notes 页面
  profileVisible.value = false
  router.push('/notes')
}
function goMessages(){ router.push('/messages') }
function goLikes(){ router.push('/likes') }
function goFavorites(){ router.push('/favorites') }
function goHistory(){ router.push('/history') }
function goLogin(){
  const redirect = encodeURIComponent(route.fullPath || '/')
  router.push(`/login?redirect=${redirect}`)
}
function goRegister(){ router.push('/register') }
function goSquare(){
  // 点击品牌或图标，跳转到广场首页
  if (route.path !== '/') router.push('/')
}

function onHoverEnter(){
  profileVisible.value = true
  if (hoverHideTimer){ clearTimeout(hoverHideTimer); hoverHideTimer = null }
}
function onHoverLeave(){
  if (hoverHideTimer) clearTimeout(hoverHideTimer)
  hoverHideTimer = setTimeout(() => { profileVisible.value = false }, 160)
}
</script>

<style scoped>
.topbar { display: grid; grid-template-columns: 1fr minmax(260px, 520px) auto; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid #e5e7eb; }
.topbar .brand { display: inline-flex; align-items: center; gap: 8px; }
.topbar .brand h1 { font-size: 18px; margin: 0; color: #303133; }
.center-search { display: flex; justify-content: center; }
.top-search-input { --el-input-bg-color: #fff; --el-input-border-color: transparent; --el-input-hover-border-color: transparent; --el-input-focus-border-color: var(--el-color-primary); box-shadow: 0 8px 26px rgba(64,158,255,0.12), 0 2px 10px rgba(0,0,0,0.08); border-radius: 999px; padding-right: 4px; max-width: 520px; }
.right-actions { display: inline-flex; align-items: center; gap: 8px; justify-content: flex-end; }
.icon-btn { border-radius: 50%; padding: 6px; transition: transform .15s ease, filter .15s ease; }
.icon-btn:hover { transform: translateY(-1px); filter: brightness(1.05); }
.dialog-header { position: relative; }
.edit-icon { position: absolute; right: 0; top: 0; transition: transform .15s ease, box-shadow .15s ease; }
.edit-icon:hover { transform: scale(1.04); box-shadow: 0 4px 12px var(--el-color-primary-light-9); }
.info-card { padding:12px; background:#fff; border:1px solid var(--el-border-color-light); border-radius:12px; box-shadow:0 6px 18px var(--el-color-primary-light-9); }
.info-list { display:flex; flex-direction:column; gap:8px; color:#303133; font-size:14px; }
.info-row { display:flex; gap:8px; align-items:flex-start; padding:8px 10px; background:var(--el-fill-color-light); border-radius:8px; transition: background-color .15s ease; }
.info-row:hover { background: var(--el-fill-color); }
.info-row .label { display:flex; align-items:center; gap:6px; color:#606266; font-size:13px; font-weight:600; flex:0 0 auto; width:72px; }
.info-row .label-icon { opacity:0.75; }
.signature-line { display:flex; align-items:flex-start; gap:4px; color:#606266; font-size:12px; }
.signature-ellipsis {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  word-break: break-word;
}
.help { margin-top:6px; color:#909399; font-size:12px; }
.edit-form-card { padding:12px; background:#fff; border:1px solid var(--el-border-color-light); border-radius:12px; box-shadow:0 6px 18px var(--el-color-primary-light-9); }
.edit-form-card :deep(.el-form-item){ margin-bottom:12px; }
.edit-form-card :deep(.el-form-item__label){ color:#606266; font-weight:600; text-align:left; }
.edit-form-card :deep(.el-input__wrapper){ border-radius:10px; background:#f7f9fc; box-shadow:none; transition: box-shadow .15s ease, background-color .15s ease; }
.edit-form-card :deep(.el-input__wrapper:hover){ background:#f4f6f9; }
.edit-form-card :deep(.el-input__wrapper.is-focus){ background:#ffffff; box-shadow:0 0 0 2px rgba(64,158,255,0.25), 0 4px 12px rgba(0,0,0,0.06); }
.edit-form-card :deep(.el-textarea__inner){ border-radius:10px; background:#f7f9fc; resize: none; }
.edit-form-card :deep(.el-input__count){ right:8px; bottom:6px; background:rgba(0,0,0,0.04); padding:0 6px; border-radius:6px; }
.fade-slide-enter-active, .fade-slide-leave-active { transition: opacity .18s ease, transform .18s ease; }
.fade-slide-enter-from, .fade-slide-leave-to { opacity: 0; transform: translateY(6px) scale(0.98); }
@media (max-width: 460px){
  .info-card { padding:10px; border-radius:10px; }
  .edit-form-card { padding:10px; border-radius:10px; }
  .info-list { gap:6px; }
  .info-row { padding:6px 8px; gap:6px; }
  .info-row .label { width:64px; font-size:12px; }
}
.send-code-btn {
  position: relative;
  overflow: visible;
}
.send-code-btn::after {
  content: '';
  position: absolute;
  right: -6px;
  top: -6px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: conic-gradient(var(--el-color-primary) calc(var(--p, 0)*360deg), rgba(64,158,255,0.18) 0);
  box-shadow: 0 0 0 2px rgba(64,158,255,0.18) inset;
  transition: background 0.2s linear;
  z-index: 1;
}
</style>

<style scoped>
/* 自定义悬浮：定位卡片贴着头像展开 */
.profile-trigger { position: relative; display:inline-block; }
.profile-trigger .profile-card { position: absolute; right: 0; top: calc(100% + 2px); z-index: 2000; }
.profile-card {
  width: 320px;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: saturate(180%) blur(12px);
  -webkit-backdrop-filter: saturate(180%) blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(0,0,0,0.14);
}
.profile-header {
  position: relative;
  padding: 12px;
  background: transparent;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}
.profile-header .top-actions { position: absolute; right: 8px; top: 8px; display:flex; gap:8px; }
.profile-header .add-entry { position: absolute; right: 8px; top: 8px; }
.profile-header .add-entry-bottom { position: absolute; right: 8px; bottom: 8px; }
.profile-header .avatar-line { display:flex; align-items:center; gap:10px; }
.profile-header .avatar-wrap { position: relative; display: inline-block; }
.profile-header .avatar-lg { width:56px; height:56px; border-radius:50%; object-fit:cover; border:2px solid #fff; box-shadow:0 2px 6px rgba(0,0,0,0.15); background:#fff; }
.profile-header .name-box { display:flex; flex-direction:column; }
.profile-header .nickname { font-weight:700; color:#303133; }
.profile-header .sub { color:#606266; font-size:12px; }
.profile-info-list { padding: 12px; display:flex; flex-direction:column; gap:0; }
.profile-info-list .row { display:flex; align-items:flex-start; justify-content:flex-start; gap:4px; padding:8px 0; border-bottom: 1px solid var(--el-border-color-extra-light); }
.profile-info-list .row:last-child { border-bottom: none; }
.profile-info-list .label { color:#606266; font-size:13px; font-weight:500; flex:0 0 40px; text-align:left; }
.profile-info-list .value { color:#303133; font-size:13px; text-align:left; flex:1 1 auto; }
.profile-actions { display:flex; justify-content:flex-end; gap:8px; padding: 10px 12px; border-top:1px solid var(--el-border-color-extra-light); }
/* 值展示优化 */
.profile-info-list .value { color:#303133; font-size:13px; text-align:left; overflow-wrap:anywhere; flex:1 1 auto; }
.profile-info-list .value.link, .profile-info-list .value .link { color: var(--el-color-primary); text-decoration: none; }
.profile-info-list .value.signature-ellipsis, .profile-info-list .value.signature-full { text-align: left; }
.profile-info-list .value.signature-full { -webkit-line-clamp: unset; display:block; }
@media (max-width: 460px){
  .profile-card { width: 320px; }
  .profile-info-list .row { padding:6px 8px; }
  .profile-info-list .label { font-size:12px; }
}
</style>