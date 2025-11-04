<template>
  <!-- 顶栏根节点：根据滚动状态自动切换背景透明/毛玻璃
       说明：
       - 默认透明：与页面背景融合，提供沉浸式体验；
       - 滚动时毛玻璃：当页面滚动超过阈值时，自动切换为透明毛玻璃效果，保持内容可读性；
       - 支持外部 solid 属性强制控制状态；
       - 支持外部 fluid 属性启用“铺满模式”，让中间区域（通常为搜索）撑满顶栏宽度。 -->
  <div class="header topbar" :class="{ 'glass-effect': shouldShowGlass, fluid }">
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
      <!-- 顶栏添加便签入口（原“记录”与“消息”已交换位置，且“记录”改为“添加便签”）
           设计说明：
           - 与头像悬停卡片中的“添加便签”入口保持一致：复用 goNotes 跳转逻辑；
           - 图标风格沿用顶栏一贯的线性样式，避免突兀（无需着色参数）；
           - 这样首个快捷入口即为“添加便签”，提升便签创建的触达效率。 -->
      <el-tooltip content="添加便签" placement="bottom">
        <el-button link class="icon-btn" @click="goNotes">
          <img src="https://api.iconify.design/mdi/note-plus-outline.svg" alt="添加便签" width="22" height="22" />
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
      <!-- 将“消息”移动到原“记录”的位置，实现两者调换位置 -->
      <el-tooltip content="消息" placement="bottom">
        <el-button link class="icon-btn" @click="goMessages">
          <img src="https://api.iconify.design/mdi/message-outline.svg" alt="消息" width="22" height="22" />
        </el-button>
      </el-tooltip>
      <template v-if="authed">
      <div class="profile-trigger" @mouseenter="onHoverEnter" @mouseleave="onHoverLeave">
        <div class="trigger-ref" style="display:flex; align-items:center; gap:8px; cursor:pointer;">
          <img v-if="me.avatarUrl" :src="avatarUrl" alt="avatar" style="width:32px;height:32px;border-radius:50%;object-fit:cover;border:2px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,0.15);background:#fff;" />
          <img v-else src="https://api.iconify.design/mdi/account-circle.svg" alt="avatar" style="width:32px;height:32px;border-radius:50%;background:#fff;" />
          <span style="font-weight:500; color:#303133;">{{ me.nickname || me.username }}</span>
        </div>
        <div
          class="profile-card"
          v-show="profileVisible"
          @mouseenter="onHoverEnter"
          @mouseleave="onHoverLeave"
          :style="{ '--card-extra-width': CARD_EXTRA_WIDTH + 'px' }"
        >
          <div class="profile-header">
            <!-- 管理员：右上齿轮；右下添加便签 -->
            <div class="top-actions" v-if="isAdmin" style="position:absolute; right:10px; top:10px; display:flex; gap:8px; z-index:1;">
              <el-tooltip content="系统管理" placement="left">
                <el-button circle size="small" @click="goAdmin" title="系统管理">
                  <img src="https://api.iconify.design/mdi/cog-outline.svg?color=%23409eff" alt="" aria-hidden="true" width="18" height="18" />
                </el-button>
              </el-tooltip>
            </div>
            <!-- 管理员：右下添加便签入口（与普通用户不同，位置更靠近头像） -->
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
            <!-- 头像与昵称采用垂直居中排版，贴近示例图
                 说明：不影响原有右上角管理与添加入口，仅调整视觉结构。 -->
            <div class="avatar-line">
              <!-- 头像与昵称居中显示：保持与示例一致的纵向排版，不改变交互逻辑 -->
              <div class="avatar-wrap" style="display:flex; flex-direction:column; align-items:center; gap:8px; width:100%;">
                <img v-if="me.avatarUrl" :src="avatarUrl" alt="avatar" class="avatar-lg" />
                <img v-else src="https://api.iconify.design/mdi/account-circle.svg" alt="avatar" class="avatar-lg" />
                <div class="nickname">{{ me.nickname || me.username }}</div>
              </div>
            </div>
          </div>
          <div class="profile-info-list">
            <!-- 签名居中显示：无内容时展示默认文案；
                 仅显示三行，超出省略；悬停通过 Tooltip 展示全文。 -->
            <div class="row center-row" style="border-bottom:none;">
              <el-tooltip :content="me.signature || defaultSig" placement="top" effect="light">
                <span class="value signature-ellipsis center" :title="me.signature || defaultSig">
                  {{ me.signature || defaultSig }}
                </span>
              </el-tooltip>
            </div>
          </div>
          <div class="profile-actions">
            <!-- 顺序：我的信息 → 我的便签 → 导出便签 → 退出登录；
                 为每项加入线性图标，保持居中与统一间距 -->
            <!-- 个人信息入口（唯一保留）
                 说明：此前此处存在两个完全相同的按钮，导致出现一个按钮不在
                 .profile-actions 容器的 40px 左内边距内，从视觉上比其他项更靠左。
                 移除重复后，所有项目统一受容器的左内边距影响，左对齐一致。 -->
            <el-button size="small" class="action-item" @click="openMyInfo">
              <img class="action-icon" src="https://api.iconify.design/mdi/account-outline.svg" alt="信息" width="16" height="16" />
              <span>个人信息</span>
            </el-button>
            <el-button size="small" class="action-item" @click="goMyNotes">
              <img class="action-icon" src="https://api.iconify.design/mdi/note-text-outline.svg" alt="便签" width="16" height="16" />
              <span>我的便签</span>
            </el-button>
            <!-- 导出便签入口（弹出卡片选择范围）：不改逻辑，仅换为扁平风格 -->
            <el-popover
              v-model:visible="exportVisible"
              placement="top"
              width="280"
              popper-class="export-pop"
            >
              <div class="export-title">导出我的便签</div>
              <div class="export-desc">请选择要导出的范围：</div>
              <el-radio-group v-model="exportScope" size="small" class="export-scope">
                <el-radio-button label="all">全部</el-radio-button>
                <el-radio-button label="public">公开</el-radio-button>
                <el-radio-button label="private">私有</el-radio-button>
              </el-radio-group>
              <div class="export-actions">
                <el-button size="small" @click="exportVisible=false">取消</el-button>
                <el-button size="small" type="primary" :loading="exportLoading" @click="exportMyNotes">导出</el-button>
              </div>
              <template #reference>
                <el-button size="small" class="action-item">
                  <img class="action-icon" src="https://api.iconify.design/mdi/file-export-outline.svg" alt="导出" width="16" height="16" />
                  <span>导出便签</span>
                </el-button>
              </template>
            </el-popover>
            <el-button size="small" class="action-item" @click="logout">
              <img class="action-icon" src="https://api.iconify.design/mdi/logout.svg" alt="退出" width="16" height="16" />
              <span>退出登录</span>
            </el-button>
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
// 顶栏组件：支持吸顶与可控背景模式，增加滚动检测和毛玻璃效果
// 说明：
// - props.solid：当为 true 时强制启用毛玻璃效果；为 false 时根据滚动自动切换；
// - props.transparent：当为 true 时“始终透明”，即使滚动也不启用毛玻璃（优先级最高）；
// - props.fluid：当为 true 时启用“铺满模式”，让中间区域（搜索）在可用空间内尽可能拉伸；
// - 自动检测页面滚动，当滚动距离超过阈值时自动切换为毛玻璃效果（除非 transparent 为 true）。
const { solid = false, transparent = false, fluid = false } = defineProps({ 
  // 控制是否强制毛玻璃效果
  solid: { type: Boolean, default: false },
  // 控制是否始终透明；开启后将禁用一切毛玻璃切换
  transparent: { type: Boolean, default: false },
  // 控制顶栏布局是否“铺满”
  fluid: { type: Boolean, default: false }
})
import { reactive, ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { http, avatarFullUrl } from '@/api/http'
import { clearToken, getToken } from '@/utils/auth'
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
// 默认签名文案：当未设置签名时显示该提示
const defaultSig = '写个签名开心一下吧 ^ -^'
// 卡片额外宽度常量：最终宽度 = 头像宽度 + CARD_EXTRA_WIDTH
// 你可以在此处修改数值以快速调整卡片宽度
const CARD_EXTRA_WIDTH = 160
const signatureExpanded = ref(false)

// 滚动检测相关状态
const scrollY = ref(0)
const isScrolled = ref(false)

// 计算属性：确定是否应该显示毛玻璃效果
// 逻辑说明：
// - 当 transparent=true 时，顶栏“始终透明”，不显示毛玻璃（最高优先级）；
// - 否则，如果 solid=true，则强制显示毛玻璃；
// - 其余情况由滚动状态决定（超过 20px 显示毛玻璃）。
const shouldShowGlass = computed(() => {
  // 透明优先：一旦开启始终透明，直接返回 false
  if (transparent) return false
  // 强制毛玻璃或按滚动自动切换
  return solid || isScrolled.value
})

const avatarUrl = computed(() => avatarFullUrl(me.avatarUrl))
const isAdmin = computed(() => (me.role || '').toUpperCase() === 'ADMIN')
const authed = computed(() => !!(me.username))

// 滚动事件处理函数
function handleScroll() {
  scrollY.value = window.scrollY || document.documentElement.scrollTop
  // 当滚动距离超过 20px 时，认为已经滚动，需要显示毛玻璃效果
  isScrolled.value = scrollY.value > 20
}

function emitSearch(){
  emit('search', q.value)
  const qp = encodeURIComponent(q.value || '')
  router.push(`/search?q=${qp}`)
}

onMounted(() => { 
  loadMe()
  // 添加滚动事件监听
  window.addEventListener('scroll', handleScroll, { passive: true })
  // 初始化滚动状态
  handleScroll()
})

onUnmounted(() => {
  // 清理滚动事件监听
  window.removeEventListener('scroll', handleScroll)
})

async function loadMe(){
  try{
    // 在未登录时可能返回 401，抑制全局拦截器重定向
    const { data } = await http.get('/account/me', { suppress401Redirect: true })
    Object.assign(me, data)
  }catch(e){
    // 未登录或 token 失效时，顶栏保持未登录状态即可，避免干扰当前页面跳转
    // 受保护页面的跳转由全局路由守卫处理（redirect 到登录）
    if (e?.response?.status === 401) {
      me.username = ''
      me.nickname = ''
      me.avatarUrl = ''
      me.email = ''
      me.role = ''
    }
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
  // 退出登录后自动跳转到广场
  router.replace('/')
}
function goMyNotes(){
  // 关闭个人信息弹层，避免遮挡交互
  profileVisible.value = false
  // 跳转防抖：若当前已在“我的便签”页，避免重复导航并给予轻提示
  // 说明：Vue Router 在尝试跳转到同一路由时会忽略或抛出冗余导航错误；
  // 这里主动判断当前路径，提升用户的感知与体验。
  if (route.path === '/my-notes' || (typeof route.fullPath === 'string' && route.fullPath.includes('/my-notes'))){
    ElMessage.info('已在我的便签页')
    return
  }
  // 登录态：直接跳转；未登录时由路由守卫(meta.requiresAuth)统一拦截并重定向到登录页
  router.push('/my-notes')
}
function goAdmin(){
  profileVisible.value = false
  router.push('/admin')
}
function goNotes(){
  // 添加便签统一入口：
  // - 顶栏“添加便签”按钮与头像悬停卡片的“添加便签”均复用此跳转；
  // - 交互保持一致性，避免出现两个入口逻辑不一致的情况；
  profileVisible.value = false
  // 需求变更：未登录时不要跳转登录，仅提示“请先登录”
  // 说明：使用 token 判断登录态，避免顶栏 me 状态未刷新导致误判
  if (!getToken()) { ElMessage.warning('请先登录'); return }
  router.push('/notes')
}
function goMessages(){
  // 需求变更：未登录点击“消息”不重定向登录，仅弹出提示
  if (!getToken()) { ElMessage.warning('请先登录'); return }
  router.push('/messages')
}
function goLikes(){
  // 需求变更：未登录点击“喜欢”不重定向登录，仅弹出提示
  if (!getToken()) { ElMessage.warning('请先登录'); return }
  router.push('/likes')
}
function goFavorites(){
  // 需求变更：未登录点击“收藏”不重定向登录，仅弹出提示
  if (!getToken()) { ElMessage.warning('请先登录'); return }
  router.push('/favorites')
}
// 顶栏已不再使用“历史记录”入口（已改为“添加便签”并交换位置），
// 如其他页面仍需要历史记录入口，可继续复用此方法。
function goHistory(){
  // 历史页需要登录：交给路由守卫处理
  router.push('/history')
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

function onHoverEnter(){
  profileVisible.value = true
  if (hoverHideTimer){ clearTimeout(hoverHideTimer); hoverHideTimer = null }
}
function onHoverLeave(){
  if (hoverHideTimer) clearTimeout(hoverHideTimer)
  hoverHideTimer = setTimeout(() => { profileVisible.value = false }, 160)
}

// =========================
// 导出便签（重新实现）
// =========================
// 说明：
// - 导出入口在头像悬浮卡片的“导出便签”按钮中（使用 Popover 展示范围选择）；
// - 这里重新实现导出逻辑，前端分页拉取“我的便签”，在本地按范围过滤并生成 CSV 文件；
// - 默认导出格式为 CSV（含 UTF-8 BOM，兼容 Excel 打开）；后续如需 JSON/Excel，可继续扩展；
// - 后端 /notes 接口支持 mineOnly=true 拉取当前用户的便签；未提供导出专用 API，因此采用前端聚合。

// 导出弹窗可见性
const exportVisible = ref(false)
// 导出范围：all（全部）/ public（公开）/ private（私有）
const exportScope = ref('all')
// 导出按钮加载状态
const exportLoading = ref(false)

// 将后端返回的便签项统一映射为稳定字段，以便导出
// 说明：不同页面/接口可能出现 camelCase 与 snake_case 混用，此处进行兜底兼容。
function mapNoteItem(it){
  return {
    id: it.id,
    userId: it.userId ?? it.user_id ?? '',
    authorName: it.authorName ?? it.author_name ?? (me.nickname || me.username || ''),
    content: it.content ?? '',
    // tags 后端通常为逗号分隔字符串；若返回数组或其他结构，这里统一为字符串
    tags: Array.isArray(it.tags) ? it.tags.join(',') : (it.tags ?? ''),
    color: it.color ?? '',
    archived: Boolean(it.archived ?? it.is_archived ?? false),
    isPublic: Boolean(it.isPublic ?? it.is_public ?? false),
    // 日期字段：优先使用 camelCase，其次 snake_case
    createdAt: it.createdAt ?? it.created_at ?? '',
    updatedAt: it.updatedAt ?? it.updated_at ?? '',
    // 交互统计：兼容多种返回形态
    likeCount: Number(it.likeCount ?? it.like_count ?? 0),
    favoriteCount: Number(it.favoriteCount ?? it.favorite_count ?? 0),
  }
}

// 构建 CSV 文本（包含表头）
// 注意：
// - 使用引号包裹字段，内部双引号需转义为两个双引号；
// - 在最前加入 UTF-8 BOM（\uFEFF），避免 Excel 乱码；
function buildCsv(rows){
  const headers = [
    'id','userId','authorName','content','tags','color','archived','isPublic','createdAt','updatedAt','likeCount','favoriteCount'
  ]
  // 字段转义为 CSV 单元格
  const esc = (v) => {
    const s = (v === null || v === undefined) ? '' : String(v)
    // 将双引号转义为两个双引号
    const escaped = s.replace(/"/g, '""')
    return `"${escaped}"`
  }
  const lines = []
  lines.push(headers.join(','))
  for (const r of rows){
    const line = [
      esc(r.id), esc(r.userId), esc(r.authorName), esc(r.content), esc(r.tags), esc(r.color),
      esc(r.archived), esc(r.isPublic), esc(r.createdAt), esc(r.updatedAt), esc(r.likeCount), esc(r.favoriteCount)
    ].join(',')
    lines.push(line)
  }
  // 头部加入 BOM，兼容 Excel
  return '\uFEFF' + lines.join('\n')
}

// 触发浏览器下载（创建临时链接并点击）
function triggerDownload(filename, text){
  const blob = new Blob([text], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

// 导出我的便签（按范围）
// 实现步骤：
// 1) 校验登录态；
// 2) 分页拉取 /notes?mineOnly=true 的全部数据（页大小 200）；
// 3) 本地按 exportScope 过滤（all/public/private）；
// 4) 构建 CSV 并触发下载；
// 5) 成功/失败提示，并关闭弹窗。
async function exportMyNotes(){
  if (!getToken()){ ElMessage.warning('请先登录'); return }
  if (exportLoading.value) return
  exportLoading.value = true
  try{
    const pageSize = 200
    let page = 1
    const all = []
    // 分页拉取直至无更多数据
    while(true){
      const { data } = await http.get('/notes', {
        params: { page, size: pageSize, mineOnly: true },
        suppress401Redirect: true,
      })
      const items = Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])
      const mapped = items.map(mapNoteItem)
      all.push(...mapped)
      // 终止条件：本页数量不足 pageSize，或没有返回数组
      if (!Array.isArray(items) || items.length < pageSize) break
      page += 1
    }

    // 按范围过滤
    let filtered = all
    if (exportScope.value === 'public') filtered = all.filter(n => n.isPublic === true)
    else if (exportScope.value === 'private') filtered = all.filter(n => n.isPublic === false)

    // 构建 CSV 并下载
    const csv = buildCsv(filtered)
    const now = new Date()
    const pad = (n) => String(n).padStart(2,'0')
    const filename = `my-notes_${now.getFullYear()}${pad(now.getMonth()+1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}.csv`
    triggerDownload(filename, csv)

    exportVisible.value = false
    ElMessage.success(`导出成功，共 ${filtered.length} 条`)
  }catch(e){
    const msg = e?.response?.data?.message || '导出失败，请稍后重试'
    ElMessage.error(msg)
  }finally{
    exportLoading.value = false
  }
}
</script>

<style scoped>
/* 顶栏吸顶与背景切换
   说明：
   - 吸顶：position: sticky; top: 0; 保持顶栏在视窗顶部；
   - 背景：默认透明（避免遮挡）；当 .solid 类存在时变为纯白（提升可读性）；
   - 层级：统一设置 z-index，避免被页面内容覆盖。 */
.topbar {
  /* 布局：左中右三段（品牌 / 搜索 / 操作） */
  display: grid;
  grid-template-columns: 1fr minmax(260px, 520px) auto;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  /* 吸顶：粘性定位 + 顶部距离 */
  position: sticky;
  top: 0;
  /* 覆盖层级 */
  z-index: 1000;
  /* 默认背景透明：提供沉浸式体验，与页面背景融合 */
  background: transparent;
  border-bottom: 1px solid transparent;
  /* 平滑过渡效果 */
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 毛玻璃效果：滚动时激活，提供透明毛玻璃背景 */
.topbar.glass-effect { 
  /* 半透明白色背景 */
  background: rgba(255, 255, 255, 0.75);
  /* 毛玻璃模糊效果 */
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  /* 轻微边框增强层次感 */
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  /* 柔和阴影提升层次 */
  box-shadow: 0 1px 20px rgba(0, 0, 0, 0.08);
}

/* 铺满模式（fluid）：让中间列在可用空间内尽可能拉伸，达到“内容铺满顶栏”的效果
   说明：
   - 将中间列改为 1fr，左右两侧自适应内容宽度（auto）；
   - 中间区域（.center-search）与输入框采用宽度 100%，以占满该列。 */
.topbar.fluid {
  /* 左列（品牌）auto + 中间列 1fr + 右列（操作）auto */
  grid-template-columns: auto 1fr auto;
}
.topbar.fluid .center-search { 
  /* 中间区域占满该列的可用空间；
     注意：flex 的 justify-content 不支持 stretch，这里保持居中并允许子元素 100% 宽度 */
  justify-content: center; 
  /* 允许子元素计算宽度不被默认最小宽度限制（避免 grid 中 1fr 列内溢出） */
  min-width: 0;
}
.topbar.fluid .top-search-input { 
  /* 搜索输入占满中间区域 */
  width: 100%; 
}

/* 说明：
   - 默认透明状态提供沉浸式体验；
   - 滚动时自动切换为毛玻璃效果，保持内容可读性的同时维持视觉美感；
   - 使用 backdrop-filter 实现真正的毛玻璃效果，对底层内容进行模糊处理；
   - 平滑过渡确保状态切换的流畅性。 */
/* 回退：移除品牌悬停增强效果，保留基础布局在文件后部定义 */

/* 回退：移除搜索输入的毛玻璃态与深度选择器覆写，保留基础样式在文件后部定义 */

/* 回退：移除图标按钮的额外背景与按压反馈，保留基础样式在文件后部定义 */

/* 回退：移除额外响应式调整，保持原有布局行为 */
.topbar .brand {
  /* 品牌区域（云便签）：左侧增加边距，避免贴边
     设计目的：
     - 保持品牌与窗口左侧有舒适留白，提高整体视觉平衡；
     - 在小屏设备上同样生效，因为顶栏为粘性定位。 */
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-left: 24px; /* 左边距：12px，数值可根据视觉需求微调 */
}
.topbar .brand h1 { font-size: 18px; margin: 0; color: #303133; }
/* 搜索框容器：居中布局，提供响应式适配 */
.center-search { 
  display: flex; 
  justify-content: center; 
  align-items: center;
  position: relative;
}

/* 搜索框美化样式：现代化设计，提升用户体验
   设计要点：
   - 采用毛玻璃背景，与顶栏整体风格保持一致
   - 圆角胶囊形状，符合现代UI趋势
   - 渐变阴影，增加层次感和立体效果
   - 平滑过渡动画，提升交互体验
   - 响应式宽度，适配不同屏幕尺寸 */
.top-search-input { 
  /* Element Plus 变量覆写：自定义输入框颜色 */
  --el-input-bg-color: rgba(255, 255, 255, 0.85);
  --el-input-border-color: rgba(255, 255, 255, 0.3);
  --el-input-hover-border-color: rgba(64, 158, 255, 0.4);
  --el-input-focus-border-color: var(--el-color-primary);
  --el-input-text-color: #303133;
  --el-input-placeholder-color: #909399;
  
  /* 基础布局与尺寸 */
  max-width: 520px;
  min-width: 280px;
  width: 100%;
  
  /* 毛玻璃背景效果 */
  backdrop-filter: saturate(130%) blur(16px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  
  /* 圆角胶囊造型 */
  border-radius: 24px;
  
  /* 渐变立体阴影：营造浮起效果 */
  box-shadow: 
    0 4px 20px rgba(64, 158, 255, 0.08),
    0 2px 8px rgba(0, 0, 0, 0.04),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
  
  /* 平滑过渡动画 */
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  
  /* 内边距调整：为图标和清除按钮预留空间 */
  padding: 0 16px 0 12px;
}

/* 搜索框悬停状态：增强视觉反馈 */
.top-search-input:hover {
  /* 增强阴影效果 */
  box-shadow: 
    0 6px 24px rgba(64, 158, 255, 0.12),
    0 3px 12px rgba(0, 0, 0, 0.06),
    inset 0 1px 0 rgba(255, 255, 255, 0.7);
  
  /* 轻微上浮效果 */
  transform: translateY(-1px);
}

/* 搜索框聚焦状态：突出当前操作 */
.top-search-input.is-focus {
  /* 聚焦时的强化阴影 */
  box-shadow: 
    0 8px 32px rgba(64, 158, 255, 0.16),
    0 4px 16px rgba(0, 0, 0, 0.08),
    0 0 0 3px rgba(64, 158, 255, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
  
  /* 聚焦时的上浮效果 */
  transform: translateY(-2px);
}

/* 深度选择器：自定义 Element Plus 内部组件样式 */
.top-search-input :deep(.el-input__wrapper) {
  /* 移除默认边框和背景，使用父级样式 */
  background: transparent;
  border: none;
  box-shadow: none;
  padding: 12px 0;
  
  /* 圆角继承 */
  border-radius: inherit;
}

.top-search-input :deep(.el-input__inner) {
  /* 文本样式优化 */
  font-size: 15px;
  font-weight: 400;
  line-height: 1.4;
  color: var(--el-input-text-color);
  
  /* 占位符样式 */
  &::placeholder {
    color: var(--el-input-placeholder-color);
    font-weight: 300;
  }
}

/* 搜索图标样式优化 */
.top-search-input :deep(.el-input__prefix) {
  /* 图标容器定位 */
  left: 16px;
  
  /* 图标样式 */
  img {
    opacity: 0.6;
    transition: opacity 0.2s ease;
  }
}

/* 聚焦时图标高亮 */
.top-search-input.is-focus :deep(.el-input__prefix img) {
  opacity: 0.8;
}

/* 清除按钮样式优化 */
.top-search-input :deep(.el-input__suffix) {
  right: 16px;
}

.top-search-input :deep(.el-input__clear) {
  /* 清除按钮样式 */
  color: #909399;
  font-size: 16px;
  transition: color 0.2s ease, transform 0.2s ease;
  
  &:hover {
    color: #606266;
    transform: scale(1.1);
  }
}
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
  /* 按需显示三行，多余以省略号处理 */
  -webkit-line-clamp: 3;
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
/* 搜索框响应式优化：适配不同屏幕尺寸 */
@media (max-width: 768px) {
  .top-search-input {
    /* 平板尺寸：适度缩小最大宽度 */
    max-width: 400px;
    min-width: 240px;
  }
}

@media (max-width: 640px) {
  .center-search {
    /* 移动端：调整容器布局 */
    padding: 0 8px;
  }
  
  .top-search-input {
    /* 移动端：进一步缩小尺寸，优化触摸体验 */
    max-width: 100%;
    min-width: 200px;
    border-radius: 20px;
    
    /* 移动端阴影简化：减少性能消耗 */
    box-shadow: 
      0 2px 12px rgba(64, 158, 255, 0.06),
      0 1px 4px rgba(0, 0, 0, 0.04),
      inset 0 1px 0 rgba(255, 255, 255, 0.5);
  }
  
  .top-search-input:hover {
    /* 移动端悬停效果简化 */
    transform: none;
    box-shadow: 
      0 3px 16px rgba(64, 158, 255, 0.08),
      0 2px 6px rgba(0, 0, 0, 0.05),
      inset 0 1px 0 rgba(255, 255, 255, 0.6);
  }
  
  .top-search-input.is-focus {
    /* 移动端聚焦效果优化 */
    transform: translateY(-1px);
    box-shadow: 
      0 4px 20px rgba(64, 158, 255, 0.12),
      0 2px 8px rgba(0, 0, 0, 0.06),
      0 0 0 2px rgba(64, 158, 255, 0.08),
      inset 0 1px 0 rgba(255, 255, 255, 0.7);
  }
  
  .top-search-input :deep(.el-input__wrapper) {
    /* 移动端内边距调整 */
    padding: 10px 0;
  }
  
  .top-search-input :deep(.el-input__inner) {
    /* 移动端字体大小优化 */
    font-size: 14px;
  }
}

@media (max-width: 460px){
  .info-card { padding:10px; border-radius:10px; }
  .edit-form-card { padding:10px; border-radius:10px; }
  .info-list { gap:6px; }
  .info-row { padding:6px 8px; gap:6px; }
  .info-row .label { width:64px; font-size:12px; }
  
  /* 超小屏幕：搜索框进一步优化 */
  .top-search-input {
    min-width: 180px;
    border-radius: 18px;
    padding: 0 12px 0 10px;
  }
  
  .top-search-input :deep(.el-input__prefix) {
    left: 12px;
  }
  
  .top-search-input :deep(.el-input__suffix) {
    right: 12px;
  }
  
  .top-search-input :deep(.el-input__prefix img) {
    width: 16px;
    height: 16px;
  }
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
.profile-trigger {
  /* 头像触发器：右侧增加边距，避免贴近顶栏右缘
     说明：
     - 顶栏右侧采用 flex-end 对齐，头像与右缘的距离受此边距控制；
     - 添加右边距后，悬浮卡片的定位不受影响（absolute 定位基于本容器）。 */
  position: relative;
  display:inline-block;
  margin-right: 36px; /* 右边距：12px，提供舒适的右侧留白 */
  margin-left: 20px;
}
/* 自定义悬浮：定位卡片贴着头像展开 */
.profile-trigger .profile-card { position: absolute; right: 0; top: calc(100% + 12px); z-index: 2000; }
.profile-card {
  /* 卡片宽度 = 头像宽度 + 常量（默认 160px，可在脚本中修改） */
  width: calc(var(--avatar-size, 56px) + var(--card-extra-width, 160px));
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
.profile-header .avatar-line { 
  /* 头像与昵称垂直居中显示，更贴近示例图 */
  display:flex; flex-direction:column; align-items:center; justify-content:center; gap:8px;
}
.profile-header .avatar-wrap { position: relative; display: inline-block; }
.profile-header .avatar-lg { width:var(--avatar-size, 56px); height:var(--avatar-size, 56px); border-radius:50%; object-fit:cover; border:2px solid #fff; box-shadow:0 2px 6px rgba(0,0,0,0.15); background:#fff; }
.profile-header .name-box { display:flex; flex-direction:column; align-items:center; }
.profile-header .nickname { font-weight:700; color:#303133; font-size:16px; }
.profile-header .sub { color:#606266; font-size:12px; }
.profile-info-list { padding: 12px; display:flex; flex-direction:column; gap:0; }
.profile-info-list .row { display:flex; align-items:flex-start; justify-content:flex-start; gap:4px; padding:8px 0; border-bottom: 1px solid var(--el-border-color-extra-light); }
.profile-info-list .row.center-row { justify-content: center; }
.profile-info-list .row:last-child { border-bottom: none; }
.profile-info-list .label { color:#606266; font-size:13px; font-weight:500; flex:0 0 40px; text-align:left; }
.profile-info-list .value { color:#303133; font-size:13px; text-align:left; flex:1 1 auto; }
.profile-actions {
  /*
   * 控件容器：柔和的玻璃卡片外观
   * - 竖直排列，留出足够的间距
   * - 取消顶部分隔线，用背景和阴影来分组
   * - 按需左对齐：根据需求将整体左边距设为 40px
   *   这样控件会在视觉上与签名区域形成一致的左起始线
   */
  display:flex;
  flex-direction:column;
  align-items:stretch;
  /* 控件间距：8px，保持与示例图一致 */
  gap:8px;
  /* 左边距 40px（其余保持 12px），满足“左对齐但左边距 40px”的要求 */
  padding: 12px 12px 12px 40px;
  /* 取消顶部分隔线，用背景和阴影来分组 */
  border-top:none;
  /* 玻璃背景与边框（可通过 CSS 变量覆写） */
  background: var(--actions-bg, rgba(255,255,255,0.45));
  /* 玻璃效果： backdrop-filter 与 -webkit-backdrop-filter 分别处理不同浏览器 */
  backdrop-filter: saturate(180%) blur(10px);
  /* 兼容 Webkit 浏览器（如 Safari）的玻璃效果 */
  -webkit-backdrop-filter: saturate(180%) blur(10px);
  /* 边框：1px 半透明白色，与玻璃效果协调 */
  border: 1px solid var(--actions-border, rgba(255,255,255,0.65));
  /* 圆角：12px，与示例图一致 */
  border-radius: 12px;
  /* 内外阴影：增加层次感 */
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.6), 0 6px 18px rgba(0,0,0,0.08);
}
/* 扁平按钮样式：居中淡黑色，统一尺寸与间距 */
.profile-actions :deep(.action-item){
  width: 100%;
  /* 左对齐，配合容器的左内边距形成统一起始线 */
  justify-content: flex-start; /* 左对齐显示按钮内容 */
  background: transparent !important;
  /* 按钮边框透明，避免遮挡 */
  border-color: transparent !important;
  color: rgba(94, 94, 94, 0.80) !important; /* 淡黑色（略深提高可读性） */
  /* 按钮圆角 */
  border-radius: 8px;
  /* 按钮文字：稍小更精致 */
  font-size:  16px; /* 略小更精致 */
  /* 按钮文字：加粗更突出 */
  font-weight: 600;
  /* 按钮文字：轻微增加间距，更易读 */
  letter-spacing: 0.3px;
  /* 按钮悬停效果 */
  transition: background-color .18s ease, box-shadow .18s ease;
  /* 统一高度与点击面积 */
  min-height: 36px;
}
/* 修正：Element Plus 为相邻按钮（.el-button + .el-button）添加左外边距 12px。
   在竖直排列（flex-direction: column）中，这个左外边距会让除第一个按钮外的其它按钮
   看起来更靠右，造成“第一个更靠左”的视觉不一致。统一将相邻按钮的左外边距重置为 0。*/
.profile-actions :deep(.el-button + .el-button){
  margin-left: 0 !important;
}
.profile-actions :deep(.el-popover__reference .el-button){
  /* 作为 Popover 引用的按钮也保持无左外边距，避免与相邻规则冲突 */
  margin-left: 0 !important;
}
.profile-actions :deep(.action-icon){
  /* 按钮左侧小图标的统一样式：尺寸、间距与对齐 */
  width:16px; height:16px; margin-right:8px; opacity:0.9; vertical-align:middle;
}
.profile-actions :deep(.action-item:hover){
  /* 悬停：轻微加深 + 柔软阴影 */
  background: var(--action-hover-bg, rgba(0,0,0,0.08)) !important;
  box-shadow: 0 2px 10px rgba(0,0,0,0.12);
  color: rgba(0,0,0,0.85) !important;
}
/* 统一焦点/激活状态：不出现默认“浅灰胶囊”导致视觉偏移 */
.profile-actions :deep(.action-item:focus),
.profile-actions :deep(.action-item:focus-visible),
.profile-actions :deep(.action-item.is-focus),
.profile-actions :deep(.action-item:active){
  background: transparent !important;
  box-shadow: none !important;
  outline: none !important;
  color: rgba(0,0,0,0.85) !important;
}
/* 文本容器：保证居中与一致的内边距 */
/* 文本容器：由居中改为左对齐，增强视觉一致性 */
.profile-actions :deep(.action-item .el-button__inner){ text-align: left; padding:10px 12px; width:100%; }
/* 导出便签弹出卡片样式（与顶栏卡片一致） */
.export-pop { padding: 10px 12px; }
.export-pop .export-title { font-weight: 600; color:#303133; margin-bottom: 6px; }
.export-pop .export-desc { font-size:12px; color:#606266; margin-bottom: 8px; }
.export-pop .export-scope { display:flex; justify-content:flex-start; gap:6px; margin-bottom:10px; }
.export-pop .export-actions { display:flex; justify-content:flex-end; gap:8px; }
/* 值展示优化 */
.profile-info-list .value { color:#303133; font-size:13px; text-align:left; overflow-wrap:anywhere; flex:1 1 auto; }
.profile-info-list .value.link, .profile-info-list .value .link { color: var(--el-color-primary); text-decoration: none; }
.profile-info-list .value.signature-ellipsis, .profile-info-list .value.signature-full { text-align: center; }
.profile-info-list .value.signature-full { -webkit-line-clamp: unset; display:block; }
/* 缩窄卡片宽度：与头像宽度联动，保持紧凑 */
.profile-card { width: calc(var(--avatar-size, 56px) + var(--card-extra-width, 160px)); }
@media (max-width: 460px){
  .profile-card { width: calc(var(--avatar-size, 56px) + var(--card-extra-width, 160px)); }
  .profile-info-list .row { padding:6px 8px; }
  .profile-info-list .label { font-size:12px; }
}
</style>