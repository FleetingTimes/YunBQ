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
      <h1>拾·言</h1>
    </div>
    <div class="center-search">
      <el-input
        v-model="q"
        placeholder="搜索拾言..."
        clearable
        class="top-search-input"
        @keyup.enter="emitSearch"
        @clear="emitSearch"
      >
        <template #prefix>
          <img src="https://api.iconify.design/mdi/magnify.svg" alt="search" width="18" height="18" />
        </template>
      </el-input>
      <!-- 新增：拾言小镇入口按钮（位于搜索框右侧）
           说明：
           - 使用 Tooltip 提示文案；
           - 胶囊形渐变按钮，风格与顶栏一致；
           - 点击后跳转到 /shiyan-town 页面。 -->
      <el-tooltip content="拾言小镇" placement="bottom">
        <el-button class="town-btn" @click="goShiyanTown">
          <img class="icon" src="https://api.iconify.design/mdi/city-variant-outline.svg" alt="拾言小镇" width="18" height="18" />
          <span>拾言小镇</span>
        </el-button>
      </el-tooltip>
    </div>
    <div class="right-actions" aria-label="快捷入口">
      <!-- 顶栏添加拾言入口（原“记录”与“消息”已交换位置，且“记录”改为“添加拾言”）
        设计说明：
           - 与头像悬停卡片中的“添加便签”入口保持一致：复用 goNotes 跳转逻辑；
           - 图标风格沿用顶栏一贯的线性样式，避免突兀（无需着色参数）；
           - 这样首个快捷入口即为“添加便签”，提升便签创建的触达效率。 -->
      <el-tooltip content="添加拾言" placement="bottom">
        <el-button link class="icon-btn" @click="goNotes">
        <img src="https://api.iconify.design/mdi/note-plus-outline.svg" alt="添加拾言" width="22" height="22" />
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
      <!-- 消息入口：当存在未读消息时，显示 NEW 徽章（不展示数字） -->
      <el-tooltip content="消息" placement="bottom">
        <el-button link class="icon-btn has-badge" @click="goMessages">
          <img src="https://api.iconify.design/mdi/message-outline.svg" alt="消息" width="22" height="22" />
          <span v-if="hasNewMessages" class="badge new" aria-label="有新消息">NEW</span>
        </el-button>
      </el-tooltip>
      <!-- 认证渲染防闪烁：仅在 authReady=true 后根据 authed 决定显示头像或登录按钮。
           说明：避免在页面跳转时先显示“登录/注册”，待 /account/me 返回后再切换为头像的闪烁现象。
           实现：
           - authReady 初始为 false；有 token 时优先用会话缓存预填 me，再异步刷新；
           - 无 token 时立即置 authReady=true，直接显示登录/注册；
           - 当 authReady=false 时，右侧区域保持静默（不显示登录按钮），从而消除闪烁。 -->
      <template v-if="authReady && authed">
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
      <!-- 管理员：右上齿轮；右下添加拾言 -->
            <div class="top-actions" v-if="isAdmin" style="position:absolute; right:10px; top:10px; display:flex; gap:8px; z-index:1;">
              <el-tooltip content="系统管理" placement="left">
                <el-button circle size="small" @click="goAdmin" title="系统管理">
                  <img src="https://api.iconify.design/mdi/cog-outline.svg?color=%23409eff" alt="" aria-hidden="true" width="18" height="18" />
                </el-button>
              </el-tooltip>
            </div>
      <!-- 管理员：右下添加拾言入口（与普通用户不同，位置更靠近头像） -->
            <div class="add-entry-bottom" v-if="isAdmin">
              <!-- 修复：为卡片内“添加拾言”按钮增加 @click.stop，避免事件冒泡到父级 hover/tooltip 容器导致点击被吞。
                   注：用户反馈问题并非“卡片消失”，但实际 DOM 层级与包裹组件（Tooltip）可能拦截 click 冒泡。
                   加上 stop 后，可确保点击事件只触发跳转逻辑。 -->
              <el-tooltip content="添加拾言" placement="left">
                <el-button circle size="small" @click.stop="goNotes" title="添加拾言">
                  <img src="https://api.iconify.design/mdi/note-plus-outline.svg?color=%23409eff" alt="add" aria-hidden="true" width="18" height="18" />
                </el-button>
              </el-tooltip>
            </div>
      <!-- 普通用户右上角添加拾言入口 -->
            <div class="add-entry" v-if="!isAdmin">
              <!-- 同步修复：普通用户右上角“添加拾言”入口也增加 @click.stop，避免点击事件被父容器（悬浮卡片）拦截。 -->
              <el-tooltip content="添加拾言" placement="left">
                <el-button circle size="small" @click.stop="goNotes" title="添加拾言">
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
      <!-- 顺序：我的信息 → 我的拾言 → 导出拾言 → 退出登录；
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
        <img class="action-icon" src="https://api.iconify.design/mdi/note-text-outline.svg" alt="拾言" width="16" height="16" />
        <span>我的拾言</span>
            </el-button>
      <!-- 导出拾言入口：改为内联展开面板（不使用弹层） -->
            <el-button size="small" class="action-item" @click="exportInlineVisible = !exportInlineVisible">
              <img class="action-icon" src="https://api.iconify.design/mdi/file-export-outline.svg" alt="导出" width="16" height="16" />
        <span>导出拾言</span>
            </el-button>
            <transition name="fade-slide">
              <div v-if="exportInlineVisible" class="export-pop inline" aria-live="polite">
                <!-- 说明文字：在卡片内联区域呈现，不遮挡其他内容 -->
        <div class="export-title">导出我的拾言</div>
                <div class="export-desc">请选择导出格式：</div>
                <el-radio-group v-model="exportFormat" size="small" class="export-format">
                  <el-radio-button label="csv">CSV</el-radio-button>
                  <el-radio-button label="json">JSON</el-radio-button>
                </el-radio-group>
                <div class="export-actions">
                  <el-button size="small" @click="exportInlineVisible=false">取消</el-button>
                  <el-button size="small" type="primary" :loading="exportLoading" @click="exportMyNotes">导出</el-button>
                </div>
              </div>
            </transition>
            <!-- 导入拾言入口：与导出并列，采用内联面板进行文件选择与导入提交 -->
            <el-button size="small" class="action-item" @click="importInlineVisible = !importInlineVisible">
              <img class="action-icon" src="https://api.iconify.design/mdi/file-import-outline.svg" alt="导入" width="16" height="16" />
              <span>导入拾言</span>
            </el-button>
            <transition name="fade-slide">
              <div v-if="importInlineVisible" class="export-pop inline" aria-live="polite">
                <!-- 说明文字：在卡片内联区域呈现，不遮挡其他内容；复用 export-pop 样式容器 -->
                <div class="export-title">导入我的拾言</div>
                <div class="export-desc">支持从 CSV 或 JSON 文件导入；推荐使用之前导出的文件。</div>
                <div class="import-controls" style="display:flex; flex-direction:column; gap:8px;">
                  <!-- 文件选择：限制为 .csv/.json；读取后展示预览条目数与可能的错误提示 -->
                  <input type="file" accept=".csv,.json" @change="onImportFileChange" />
                  <div class="hint" v-if="importFileName" style="font-size:12px; color:#909399;">
                    已选择：{{ importFileName }}，准备导入 {{ importPreviewCount }} 条
                  </div>
                  <div class="errors" v-if="importErrors.length" style="font-size:12px; color:#f56c6c;">
                    {{ importErrors[0] }}
                  </div>
                </div>
                <div class="export-actions">
                  <el-button size="small" @click="closeImportPanel">取消</el-button>
                  <el-button size="small" type="primary" :loading="importLoading" :disabled="!canImport" @click="importMyNotes">导入</el-button>
                </div>
              </div>
            </transition>
            <el-button size="small" class="action-item" @click="logout">
              <img class="action-icon" src="https://api.iconify.design/mdi/logout.svg" alt="退出" width="16" height="16" />
              <span>退出登录</span>
            </el-button>
          </div>
        </div>
      </div>
      </template>
      <template v-else-if="authReady">
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
// 用户信息（顶栏展示）。
// 问题修复：页面跳转时出现“先显示登录/注册，稍后显示头像”的闪烁。
// 原因：组件挂载时 me 为空，模板按 authed=false 显示登录按钮；待 /account/me 返回后才切为头像。
// 方案：引入 authReady 与会话缓存预填，避免在“已登录但尚未刷新”阶段显示登录按钮。
const me = reactive({ username:'', nickname:'', avatarUrl:'', email:'', role:'', signature:'' })
const authReady = ref(false)
const ME_CACHE_KEY = 'me-cache'
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

// 未读消息状态：用于顶栏“消息”按钮显示 NEW 徽章
const hasNewMessages = ref(false)
// 可选：记录各类型未读计数（便于后续联动，如悬浮卡片里展示）
const unreadCounts = ref({ like: 0, favorite: 0, system: 0 })

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
  // 有 token：尝试会话缓存预填，随后刷新；无 token：直接标记已就绪。
  try {
    if (getToken()) {
      const cached = sessionStorage.getItem(ME_CACHE_KEY)
      if (cached) {
        try { Object.assign(me, JSON.parse(cached)) } catch {}
      }
      // 异步刷新最新用户信息，完成后标记 authReady
      loadMe().finally(() => { authReady.value = true })
    } else {
      // 无 token：无需等待刷新，直接允许显示登录入口
      authReady.value = true
    }
  } catch { authReady.value = true }
  // 首次加载未读消息情况；未登录会 401，被拦截器抑制或此处自行忽略
  loadUnread()
  // 添加滚动事件监听
  window.addEventListener('scroll', handleScroll, { passive: true })
  // 初始化滚动状态
  handleScroll()
  // 监听来自消息页的更新事件：单条已读/批量操作后立即刷新顶栏徽章
  // 说明：使用浏览器级事件总线，避免引入全局 store；多页面同域下也可独立工作
  window.addEventListener('messages-updated', onMessagesUpdated)
  // 标签重新可见时触发一次刷新，保证切换标签后信息不滞后
  document.addEventListener('visibilitychange', onVisibilityRefresh)
  // 轻量轮询：每 10 秒刷新一次未读状态，作为后端实时推送缺失时的兜底方案
  // 注意：在 onUnmounted 中清理，避免内存泄漏与重复请求
  if (!unreadPoller) unreadPoller = setInterval(() => { loadUnread() }, 10000)
})

onUnmounted(() => {
  // 清理滚动事件监听
  window.removeEventListener('scroll', handleScroll)
  // 清理消息更新与可见性事件监听
  window.removeEventListener('messages-updated', onMessagesUpdated)
  document.removeEventListener('visibilitychange', onVisibilityRefresh)
  // 清理轮询器
  try { if (unreadPoller) { clearInterval(unreadPoller); unreadPoller = null } } catch {}
})

async function loadMe(){
  try{
    // 在未登录时可能返回 401，抑制全局拦截器重定向
    const { data } = await http.get('/account/me', { suppress401Redirect: true })
    Object.assign(me, data)
    // 刷新会话缓存：加速后续页面跳转的顶栏渲染，减少闪烁
    try { sessionStorage.setItem(ME_CACHE_KEY, JSON.stringify(me)) } catch {}
  }catch(e){
    // 未登录或 token 失效时，顶栏保持未登录状态即可，避免干扰当前页面跳转
    // 受保护页面的跳转由全局路由守卫处理（redirect 到登录）
    if (e?.response?.status === 401) {
      me.username = ''
      me.nickname = ''
      me.avatarUrl = ''
      me.email = ''
      me.role = ''
      // 清除会话缓存，确保后续不误显示过期头像/昵称
      try { sessionStorage.removeItem(ME_CACHE_KEY) } catch {}
    }
  }
}

// —— 顶栏未读刷新：事件与轮询辅助 ——
let unreadPoller = null
function onMessagesUpdated(){
  // 来自消息页的事件：立即刷新顶栏 NEW 徽章与计数
  loadUnread()
}
function onVisibilityRefresh(){
  if (document.visibilityState === 'visible') loadUnread()
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
  // 路由重命名：统一识别 /my-shiyan（兼容旧别名 /my-notes）
  if (route.path === '/my-shiyan' || (typeof route.fullPath === 'string' && (route.fullPath.includes('/my-shiyan') || route.fullPath.includes('/my-notes')))){
  // 文案重命名：提示中“便签”统一改为“拾言”
  ElMessage.info('已在我的拾言页')
    return
  }
  // 登录态：直接跳转；未登录时由路由守卫(meta.requiresAuth)统一拦截并重定向到登录页
  // 路由重命名：统一跳转至 /my-shiyan（保留 /my-notes 为别名以兼容旧链接）
  router.push('/my-shiyan')
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
  // 路由重命名：统一跳转至 /shiyan（保留 /notes 为别名以兼容旧链接）
  router.push('/shiyan')
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
// 新增：拾言小镇跳转入口
// 说明：无需登录，作为专题页入口使用；供顶栏搜索右侧按钮调用。
function goShiyanTown(){
  // 若已在当前页，避免冗余导航（不提示，静默处理）
  if (route.path === '/shiyan-town' || (typeof route.fullPath === 'string' && route.fullPath.includes('/shiyan-town'))){
    return
  }
  router.push('/shiyan-town')
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

// 加载未读消息计数与是否存在新消息
// 接口：GET /messages/unread-counts → { counts: { like, favorite, system }, total, hasNew }
async function loadUnread(){
  // 未登录时禁用请求：避免 401 与多余网络开销，同时清空徽章状态
  // 详细说明：
  // - 需求：前端在未登录时，不应调用 /api/messages/counts；
  // - 实现：检查本地是否存在有效 token（getToken）；如不存在，直接复位计数并返回。
  // - UI：保持顶栏“NEW”提示与徽章为 0，避免误导用户。
  if (!getToken()){
    unreadCounts.value = { like: 0, favorite: 0, system: 0 }
    hasNewMessages.value = false
    return
  }
  try{
    // 路径修正：后端控制器为 /api/messages/counts（不是 unread-counts）
    const { data } = await http.get('/messages/counts', { suppress401Redirect: true })
    const counts = data?.counts || {}
    unreadCounts.value = {
      like: Number(counts.like || 0),
      favorite: Number(counts.favorite || 0),
      system: Number(counts.system || 0)
    }
    hasNewMessages.value = Boolean(data?.hasNew || (data?.total > 0))
    // 派发未读计数事件：供消息页等组件实时联动左侧徽章
    // 说明：同域多标签，同时有顶栏与消息页时，减少重复请求并提升同步效果
    try {
      window.dispatchEvent(new CustomEvent('messages-counts', {
        detail: {
          source: 'topbar',
          counts: unreadCounts.value,
          total: Number(data?.total || (counts.like || 0) + (counts.favorite || 0) + (counts.system || 0)),
          hasNew: hasNewMessages.value
        }
      }))
    } catch {}
  }catch(e){
    // 未登录或接口异常时，不影响顶栏显示
    hasNewMessages.value = false
  }
}

// =========================
// 导出便签功能：前端分页拉取 + 本地过滤 + CSV 下载
// =========================
// 设计说明（详细注释）：
// - 入口：头像悬浮卡片中的“导出便签”按钮，弹层选择导出范围（全部/公开/私有）。
// - 数据源：复用后端 /shiyan 接口，传参 mineOnly=true 拉取当前用户的拾言（后端保留 /notes 兼容）。
// - 拉取策略：每页 200 条，直到不足一页为止；避免大数据一次性请求造成阻塞。
// - 过滤：在前端根据选择的范围（all/public/private）进行本地过滤。
// - 导出格式：CSV（带 UTF-8 BOM，Excel 直接可读）；文件名包含时间戳。
// - 错误与提示：登录校验、加载状态、成功/失败消息均统一处理。

// 内联面板可见状态（控制卡片内展开区域），替代弹层实现
const exportInlineVisible = ref(false)
// 导出格式：'csv' 或 'json'（默认 csv）
const exportFormat = ref('csv')
// 导出按钮加载状态（避免重复点击）
const exportLoading = ref(false)

// 映射便签项到稳定字段结构，处理后端可能出现的大小写或命名差异
function mapNoteItem(it){
  return {
    id: it.id,
    userId: it.userId ?? it.user_id ?? '',
    authorName: it.authorName ?? it.author_name ?? (me.nickname || me.username || ''),
    content: it.content ?? '',
    // tags 可能是数组或字符串，这里统一为逗号分隔字符串
    tags: Array.isArray(it.tags) ? it.tags.join(',') : (it.tags ?? ''),
    color: it.color ?? '',
    archived: Boolean(it.archived ?? it.is_archived ?? false),
    isPublic: Boolean(it.isPublic ?? it.is_public ?? false),
    createdAt: it.createdAt ?? it.created_at ?? '',
    updatedAt: it.updatedAt ?? it.updated_at ?? '',
    likeCount: Number(it.likeCount ?? it.like_count ?? 0),
    favoriteCount: Number(it.favoriteCount ?? it.favorite_count ?? 0),
  }
}

// 构建 CSV 文本（包含表头，并在头部加入 UTF-8 BOM 以兼容 Excel）
function buildCsv(rows){
  const headers = ['id','userId','authorName','content','tags','color','archived','isPublic','createdAt','updatedAt','likeCount','favoriteCount']
  const esc = (v) => {
    const s = (v === null || v === undefined) ? '' : String(v)
    return '"' + s.replace(/"/g, '""') + '"'
  }
  const lines = []
  lines.push(headers.join(','))
  for (const r of rows){
    lines.push([
      esc(r.id), esc(r.userId), esc(r.authorName), esc(r.content), esc(r.tags), esc(r.color),
      esc(r.archived), esc(r.isPublic), esc(r.createdAt), esc(r.updatedAt), esc(r.likeCount), esc(r.favoriteCount)
    ].join(','))
  }
  return '\uFEFF' + lines.join('\n')
}

// 构建 JSON 文本（美化缩进，便于查看与二次处理）
function buildJson(rows){
  // 使用稳定字段的对象数组进行 JSON 序列化；缩进为 2 空格
  return JSON.stringify(rows, null, 2)
}

// 触发浏览器下载（创建临时链接并点击）
function triggerDownload(filename, text, mimeType = 'text/plain;charset=utf-8;'){
  const blob = new Blob([text], { type: mimeType })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

// 导出我的便签：分页拉取、按范围过滤、构建 CSV 并下载
async function exportMyNotes(){
  // 1) 登录校验：未登录不允许导出
  if (!getToken()){ ElMessage.warning('请先登录'); return }
  // 2) 防抖：导出进行中不重复触发
  if (exportLoading.value) return
  exportLoading.value = true
  try{
    const pageSize = 200
    let page = 1
    const all = []
    // 3) 分页拉取 /shiyan（mineOnly=true）直到不足一页
    while(true){
      // 路径切换：统一使用 /shiyan
      const { data } = await http.get('/shiyan', {
        params: { page, size: pageSize, mineOnly: true },
        suppress401Redirect: true,
      })
      const items = Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])
      if (!Array.isArray(items) || items.length === 0) break
      all.push(...items.map(mapNoteItem))
      if (items.length < pageSize) break
      page += 1
    }

    // 4) 构建导出内容：按格式生成 CSV 或 JSON
    const now = new Date(); const pad = (n) => String(n).padStart(2,'0')
    const ts = `${now.getFullYear()}${pad(now.getMonth()+1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
    let filename, payload, mime
    if (exportFormat.value === 'json'){
      // JSON：不需要 BOM，内容可读性强，适合二次处理
      // 文件名重命名：统一使用 my-shiyan_*.json 以匹配品牌“拾言”
      filename = `my-shiyan_${ts}.json`
      payload = buildJson(all)
      mime = 'application/json;charset=utf-8;'
    }else{
      // CSV：加入 BOM，兼容 Excel 显示中文
      // 文件名重命名：统一使用 my-shiyan_*.csv 以匹配品牌“拾言”
      filename = `my-shiyan_${ts}.csv`
      payload = buildCsv(all)
      mime = 'text/csv;charset=utf-8;'
    }
    triggerDownload(filename, payload, mime)

    // 6) 关闭弹层并提示成功
    exportInlineVisible.value = false
    ElMessage.success(`导出成功，共 ${all.length} 条；格式：${exportFormat.value.toUpperCase()}`)
  }catch(e){
    const msg = e?.response?.data?.message || '导出失败，请稍后重试'
    ElMessage.error(msg)
  }finally{
    exportLoading.value = false
  }
}

// =========================
// 导入便签功能：本地读取 CSV/JSON → 解析为标准结构 → 提交后端导入
// =========================
// 设计说明（详细注释）：
// - 入口：头像悬浮卡片中的“导入拾言”按钮，内联面板选择文件并提交导入；
// - 兼容格式：CSV 或 JSON（推荐使用本系统导出的文件，字段稳定且对齐）；
// - 解析策略：
//   * CSV：读取表头，按与导出相同的稳定字段（id,userId,authorName,content,tags,color,archived,isPublic,createdAt,updatedAt,likeCount,favoriteCount）解析；
//   * JSON：支持数组对象；若字段命名存在差异，尽量通过 mapNoteItem 进行规整；
// - 提交：调用后端 /shiyan/import（约定路径），提交 items 数组；
// - 反馈：展示成功导入条数；若后端返回逐条错误，可在 importErrors 中提示第一条；
// - 安全：必须登录（校验 token）；限制文件类型与大小（简单上限 5MB）。

// 内联面板可见状态（控制卡片内展开区域）
const importInlineVisible = ref(false)
// 导入加载状态（避免重复点击）
const importLoading = ref(false)
// 导入文件名（用于提示）
const importFileName = ref('')
// 导入预览条目数（解析后条数）
const importPreviewCount = ref(0)
// 是否可以导入（解析成功且条数>0）
const canImport = computed(() => importPreviewCount.value > 0 && !importLoading.value)
// 解析后的标准结构数组（提交给后端）
const importItems = ref([])
// 错误列表（仅展示首条以简洁提示）
const importErrors = ref([])

/**
 * 关闭导入面板并清空状态
 */
function closeImportPanel(){
  importInlineVisible.value = false
  importLoading.value = false
  importFileName.value = ''
  importPreviewCount.value = 0
  importItems.value = []
  importErrors.value = []
}

/**
 * 解析 CSV 文本为稳定字段数组
 * 说明：
 * - 读取首行作为表头；去除 UTF-8 BOM；
 * - 对常见字段进行类型归一：布尔/数字；
 * - 未出现的字段以默认值兜底；
 */
function parseCsvText(text){
  try{
    const clean = text.replace(/^\uFEFF/, '')
    const lines = clean.split(/\r?\n/).filter(l => l.trim().length > 0)
    if (lines.length <= 1) return []
    const header = lines[0].split(',').map(h => h.trim().replace(/^"|"$/g,''))
    const rows = []
    for (let i=1; i<lines.length; i++){
      const raw = lines[i]
      // 简单 CSV 解析：按逗号拆分并处理双引号转义；满足本系统导出的常见场景
      const cols = []
      let cur = '', inQ = false
      for (let j=0; j<raw.length; j++){
        const ch = raw[j]
        if (ch === '"'){
          if (inQ && raw[j+1] === '"'){ cur += '"'; j++ } else { inQ = !inQ }
        } else if (ch === ',' && !inQ){ cols.push(cur); cur = '' } else { cur += ch }
      }
      cols.push(cur)
      const obj = {}
      for (let k=0; k<header.length; k++){
        const key = header[k]
        let val = (cols[k] ?? '').trim()
        val = val.replace(/^"|"$/g,'').replace(/""/g,'"')
        obj[key] = val
      }
      // 归一化类型
      obj.archived = String(obj.archived || '').toLowerCase() === 'true'
      obj.isPublic = String(obj.isPublic || '').toLowerCase() === 'true'
      obj.likeCount = Number(obj.likeCount || 0)
      obj.favoriteCount = Number(obj.favoriteCount || 0)
      rows.push(obj)
    }
    return rows.map(mapNoteItem)
  }catch(e){
    importErrors.value = ['CSV 解析失败，请确认文件格式是否正确']
    return []
  }
}

/**
 * 解析 JSON 文本为稳定字段数组
 * 说明：
 * - 支持对象数组或带 items/records 的对象结构；
 * - 每项通过 mapNoteItem 归一字段；
 */
function parseJsonText(text){
  try{
    const data = JSON.parse(text)
    const items = Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])
    if (!Array.isArray(items)) return []
    return items.map(mapNoteItem)
  }catch(e){
    importErrors.value = ['JSON 解析失败，请确认文件内容是否为有效的 JSON']
    return []
  }
}

/**
 * 文件选择回调：读取文本并解析（自动按扩展名选择解析器）
 */
function onImportFileChange(ev){
  try{
    importErrors.value = []
    const file = ev.target?.files?.[0]
    if (!file) return
    // 简单大小限制：5MB
    if (file.size > 5 * 1024 * 1024){
      importErrors.value = ['文件过大，建议分批导入（最多 5MB）']
      return
    }
    importFileName.value = file.name
    const reader = new FileReader()
    reader.onload = () => {
      const text = String(reader.result || '')
      const ext = (file.name.split('.').pop() || '').toLowerCase()
      let parsed = []
      if (ext === 'csv') parsed = parseCsvText(text)
      else if (ext === 'json') parsed = parseJsonText(text)
      else {
        importErrors.value = ['不支持的文件类型，请选择 .csv 或 .json']
        parsed = []
      }
      importItems.value = parsed
      importPreviewCount.value = parsed.length
    }
    reader.onerror = () => { importErrors.value = ['读取文件失败，请重试或更换文件'] }
    reader.readAsText(file)
  }catch{}
}

/**
 * 提交导入：将解析后的 items 发送到后端
 * 说明：
 * - 路径约定：POST /shiyan/import；请求体 { items: [...] }；
 * - 后端返回：建议返回导入成功条数与错误信息列表（若有）；
 * - 成功后关闭面板并提示数量；可选跳转到“我的拾言”。
 */
async function importMyNotes(){
  // 登录校验
  if (!getToken()){ ElMessage.warning('请先登录'); return }
  if (!canImport.value){ ElMessage.warning('请先选择有效的文件'); return }
  if (importLoading.value) return
  importLoading.value = true
  try{
    const payload = { items: importItems.value }
    const { data } = await http.post('/shiyan/import', payload, { suppress401Redirect: true })
    const ok = Number(data?.success ?? data?.imported ?? importItems.value.length)
    const fails = Number(data?.failed ?? 0)
    ElMessage.success(`导入完成：成功 ${ok} 条${fails>0 ? `，失败 ${fails} 条` : ''}`)
    closeImportPanel()
    // 可选：跳转到“我的拾言”查看导入结果
    try{ router.push('/my-notes') }catch{}
  }catch(e){
    const msg = e?.response?.data?.message || '导入失败，请稍后重试'
    importErrors.value = [msg]
    ElMessage.error(msg)
  }finally{
    importLoading.value = false
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
  padding: 10px 0; /* 桌面端默认高度：10px 上下内边距 */
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
/* 让搜索输入占据主宽度，“拾言小镇”按钮紧随其右 */
.center-search .top-search-input { flex: 1 1 auto; }
.center-search .town-btn { margin-left: 10px; }

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

/* 移动端顶栏适配与精简：减少高度、隐藏非必要元素、避免挤压内容
   目标：
   - 压缩顶栏上下内边距以降低占用空间；
   - 隐藏“拾言小镇”按钮（保留搜索与右侧操作），减少拥挤；
   - 允许搜索输入自适应更窄的宽度，避免超出屏幕。 */
@media (max-width: 480px) {
  .topbar { 
    padding: 8px 0; 
    /* 栅格：品牌 + 中间（搜索 + 小镇）在第一行，右侧操作区换到第二行 */
    grid-template-columns: auto 1fr auto;
    row-gap: 6px; /* 第一行与第二行之间提供更紧凑的间距 */
  }
  .topbar .brand { margin-left: 16px; }
  .topbar .brand h1 { font-size: 16px; }
  /* 移动端：保留“拾言小镇”按钮（核心入口），缩短搜索框以并排显示 */
  .center-search { 
    justify-content: flex-start; /* 搜索靠左，按钮紧随其后，避免居中拥挤 */
    gap: 8px; /* 提供更紧凑的间距 */
  }
  .top-search-input { 
    /* 缩短搜索框：在小屏设备上收窄占用，给小镇按钮留出空间
       说明：
       - 使用更小的最大宽度（220px），避免溢出；
       - 设置 flex-basis 以在容器空间不足时优先压缩搜索框；
       - 保持 min-width:0 解除默认最小宽度约束，避免 grid/flex 下换行异常。 */
    min-width: 0; 
    max-width: 220px; 
    flex: 1 1 180px;
  }
  .center-search .town-btn { 
    /* 明确显示“拾言小镇”按钮：占据自身内容宽度，保持与搜索框并排 */
    display: inline-flex; 
    flex: 0 0 auto; 
  }
  /* 右侧操作区：在移动端换到第二行，保持所有入口（添加拾言/喜欢/收藏/消息/登录/注册）可见 */
  .right-actions {
    grid-column: 1 / -1; /* 跨整行，位于第二行 */
    padding: 0 12px; /* 两侧适度留白 */
    display: flex;
    flex-wrap: wrap; /* 允许在极窄屏幕下自动换行 */
    gap: 6px; /* 图标按钮之间更紧凑 */
    align-items: center;
    justify-content: space-between; /* 左右分散，避免挤在一侧 */
  }
  /* 图标按钮在移动端略缩小，节省空间但保持可点击性 */
  .right-actions .icon-btn img { width: 20px; height: 20px; }
  /* 未登录状态下的“登录/注册”按钮：压缩按钮尺寸以适配小屏 */
  .right-actions .el-button { font-size: 13px; padding: 6px 10px; }
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
/* 消息 NEW 徽章样式：在图标按钮右上角显示胶囊形 NEW 标签 */
.icon-btn.has-badge { position: relative; }
.icon-btn.has-badge .badge.new {
  /* NEW 徽章尺寸下调：更精致、不喧宾夺主 */
  position: absolute;
  top: -6px;
  right: -6px;
  background: #ff4d4f; /* 红色提示色 */
  color: #fff;
  font-size: 9px;   /* 原 10px → 9px */
  line-height: 14px;/* 原 16px → 14px */
  padding: 0 4px;   /* 原 6px → 4px */
  border-radius: 7px;/* 原 8px → 7px */
  box-shadow: 0 0 0 2px #fff; /* 白色描边增强可读性 */
}
/* 美化：拾言小镇胶囊渐变按钮（与顶栏风格一致） */
.town-btn {
  display: inline-flex;
  align-items: center;
  height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  border: none;
  color: #fff;
  font-weight: 600;
  letter-spacing: 0.3px;
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 50%, #91caff 100%);
  box-shadow: 0 8px 20px rgba(64, 158, 255, 0.20), inset 0 1px 0 rgba(255, 255, 255, 0.6);
  transition: transform .15s ease, box-shadow .15s ease, filter .15s ease;
}
.town-btn:hover {
  transform: translateY(-1px);
  filter: brightness(1.05);
  box-shadow: 0 10px 24px rgba(64, 158, 255, 0.26), inset 0 1px 0 rgba(255, 255, 255, 0.7);
}
.town-btn:active {
  transform: translateY(0);
  filter: brightness(0.98);
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.20), inset 0 1px 0 rgba(255, 255, 255, 0.6);
}
.town-btn .icon { width: 18px; height: 18px; margin-right: 6px; opacity: 0.95; }
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
.profile-header .add-entry { 
  /* 绝对定位右上角，确保在头像栈顶，避免被后续兄弟节点（如 avatar-line）覆盖导致点击无效 */
  position: absolute; right: 8px; top: 8px; z-index: 3; 
}
.profile-header .add-entry-bottom { 
  /* 绝对定位右下角，并提升层级避免被其它块覆盖（Pointer 事件被遮挡时将导致点击无效） */
  position: absolute; right: 8px; bottom: 8px; z-index: 3; 
}
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
.export-pop.inline { 
  /* 内联展开面板：在卡片内渲染，不使用浮层 */
  margin-top: 6px; 
  border: 1px dashed rgba(0,0,0,0.08);
  border-radius: 8px;
  background: rgba(255,255,255,0.55);
}
.export-pop .export-title { font-weight: 600; color:#303133; margin-bottom: 6px; }
.export-pop .export-desc { font-size:12px; color:#606266; margin-bottom: 8px; }
.export-pop .export-scope { display:flex; justify-content:flex-start; gap:6px; margin-bottom:10px; }
.export-pop .export-format { display:flex; justify-content:flex-start; gap:6px; margin-bottom:10px; }
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
<!--
  公共顶栏组件（AppTopBar）
  作用：
  - 统一全站品牌、搜索与快捷入口（我的拾言/消息/喜欢/收藏/管理员等）；
  - 处理登录态相关的导航行为（未登录时提示，不强制跳转）；
  - 支持透明/毛玻璃效果切换，适应不同页面的视觉需求；
  - 提供“拾言小镇”专题页入口；
  交互约定：
  - 路由重命名：我的拾言页使用 `/my-shiyan`（兼容旧别名 `/my-notes`）；添加便签页使用 `/shiyan`（兼容旧别名 `/notes`）。
  - 登录守卫：页面路由通过 `meta.requiresAuth` 管控；顶栏在未登录时仅给出轻提示，避免强制跳转。
-->