<template>
  <div class="header">
    <div class="brand" @click="goSquare" style="cursor:pointer;">
      <img src="https://api.iconify.design/mdi/notebook-outline.svg" alt="logo" width="24" height="24" />
      <h1>云便签</h1>
    </div>
    <div class="search" style="display:flex; align-items:center; gap:8px;">
      <el-input
        v-model="q"
        placeholder="搜索便签..."
        clearable
        style="width:240px;"
        @keyup.enter="emitSearch"
        @clear="emitSearch"
      />
      <el-button type="primary" @click="emitSearch">搜索</el-button>
      <template v-if="authed">
      <el-popover v-model:visible="profileVisible" placement="bottom-end" :width="320">
        <template #reference>
          <div style="display:flex; align-items:center; gap:8px; cursor:pointer;" @click="profileVisible = true">
            <img v-if="me.avatarUrl" :src="avatarUrl" alt="avatar" style="width:32px;height:32px;border-radius:50%;object-fit:cover;border:2px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,0.15);background:#fff;" />
            <img v-else src="https://api.iconify.design/mdi/account-circle.svg" alt="avatar" style="width:32px;height:32px;border-radius:50%;background:#fff;" />
            <span style="font-weight:500; color:#303133;">{{ me.nickname || me.username }}</span>
          </div>
        </template>
        <div style="position:relative; padding-top:8px;">
          <div v-if="isAdmin" style="position:absolute; right:4px; top:4px; z-index:2;">
            <el-tooltip content="系统管理" placement="left">
              <el-button circle size="small" @click="goAdmin" title="系统管理">
                <img src="https://api.iconify.design/mdi/cog-outline.svg" alt="admin" width="18" height="18" />
              </el-button>
            </el-tooltip>
          </div>
          <div style="display:flex; gap:12px; align-items:center; margin-bottom:8px;">
          <img v-if="me.avatarUrl" :src="avatarUrl" alt="avatar" style="width:56px;height:56px;border-radius:50%;object-fit:cover;border:2px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,0.15);background:#fff;" />
          <img v-else src="https://api.iconify.design/mdi/account-circle.svg" alt="avatar" style="width:56px;height:56px;border-radius:50%;background:#fff;" />
          <div>
            <div style="font-weight:600;">{{ me.nickname || '未设置昵称' }}</div>
            <div style="color:#606266; font-size:12px;">用户名：{{ me.username }}</div>
            <div style="color:#606266; font-size:12px;">邮箱：{{ me.email || '未绑定' }}</div>
          </div>
          </div>
        </div>
        <div style="display:flex; justify-content:flex-end; gap:8px;">
          <el-button size="small" type="success" @click="goMyNotes">我的便签</el-button>
          <el-button size="small" @click="openEditInfo">修改信息</el-button>
          <el-button size="small" type="warning" @click="logout">退出登录</el-button>
        </div>
      </el-popover>
      </template>
      <template v-else>
        <div style="display:flex; gap:8px;">
          <el-button @click="goLogin">登录</el-button>
          <el-button type="primary" @click="goRegister">注册</el-button>
        </div>
      </template>
    </div>

    <el-dialog v-model="editVisible" title="修改信息" width="420px">
      <div style="display:flex; flex-direction:column; gap:8px; align-items:center; margin-bottom:12px;">
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
      <template #footer>
        <div style="display:flex; justify-content:flex-end; gap:8px;">
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
const me = reactive({ username:'', nickname:'', avatarUrl:'', email:'', role:'' })
const profileVisible = ref(false)
const editVisible = ref(false)
const editLoading = ref(false)
const editForm = reactive({ nickname:'', email:'', code:'' })
const sendLoading = ref(false)
const sendCountdown = ref(0)
const sentOnce = ref(false)
const codeInputRef = ref(null)
let sendTimer = null

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
  editForm.nickname = me.nickname || ''
  editForm.email = me.email || ''
  editForm.code = ''
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
function goLogin(){
  const redirect = encodeURIComponent(route.fullPath || '/')
  router.push(`/login?redirect=${redirect}`)
}
function goRegister(){ router.push('/register') }
function goSquare(){
  // 点击品牌或图标，跳转到广场首页
  if (route.path !== '/') router.push('/')
}
</script>

<style scoped>
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