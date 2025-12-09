<template>
  <el-dialog v-model="visible" :title="title"  width="70vw" :close-on-click-modal="true" :append-to-body="true" class="contact-modal">
    <div class="modal-layout">
      <aside class="menu">
        <button v-for="it in tabs" :key="it.key" class="menu-item" :class="{ active: activeKey === it.key }" @click="setActive(it.key)">
          <img :src="it.icon" alt="icon" width="18" height="18" />
          <span>{{ it.label }}</span>
        </button>
      </aside>
      <section class="content" ref="contentRef">
        <div v-if="activeKey==='feedback'" class="panel">
          <h2 class="panel-title">问题反馈</h2>
          <div class="tips">
            <h3>填写提示</h3>
            <ul>
              <li>尽量提供出现问题的页面与操作步骤。</li>
              <li>如为交互问题，请描述期望结果与实际结果的差异。</li>
              <li>可附上截图或屏幕录制链接以帮助复现。</li>
            </ul>
          </div>
          <!-- 问题反馈表单：根据需求移除“复现步骤/期望结果/实际结果/GitHub”，保留核心字段 -->
          <el-form :model="feedbackForm" label-width="96px" class="form">
            <el-form-item label="所属模块">
              <el-select v-model="feedbackForm.module" placeholder="选择模块">
                <el-option label="广场" value="广场" />
                <el-option label="我的便签" value="我的便签" />
                <el-option label="搜索" value="搜索" />
                <el-option label="喜欢" value="喜欢" />
                <el-option label="收藏" value="收藏" />
                <el-option label="消息" value="消息" />
                <el-option label="用户拾言" value="用户拾言" />
              </el-select>
            </el-form-item>
            <el-form-item label="页面路径">
              <el-input v-model="feedbackForm.pagePath" placeholder="如 /、/my/shiyan、/search?q=..." />
            </el-form-item>
            <el-form-item label="标题">
              <el-input v-model="feedbackForm.title" placeholder="问题简述" />
            </el-form-item>
            <el-form-item label="问题描述">
              <el-input type="textarea" v-model="feedbackForm.description" :rows="4" placeholder="详细描述问题" />
            </el-form-item>
            <el-form-item label="联系邮箱">
              <el-input v-model="feedbackForm.contactEmail" placeholder="可选" />
            </el-form-item>
            <el-form-item label="QQ">
              <el-input v-model="feedbackForm.contactQq" placeholder="可选" />
            </el-form-item>
            <!-- 根据需求：移除 GitHub 联系方式，仅保留邮箱/QQ -->
            <el-form-item>
              <el-button type="primary" :loading="feedbackSubmitting" @click="submitFeedback">提交问题反馈</el-button>
            </el-form-item>
          </el-form>
        </div>
        <div v-else-if="activeKey==='suggest'" class="panel">
          <h2 class="panel-title">站点建议</h2>
          <p class="desc">欢迎为正在开发中的站点与功能提出改进建议。</p>
          <div class="tips">
            <h3>建议方向</h3>
            <ul>
              <li>新增栏目或分类、导航排序优化。</li>
              <li>拾言卡片、搜索、点赞收藏等交互改进。</li>
              <li>性能与移动端体验优化。</li>
            </ul>
          </div>
          <!-- 站点建议表单：根据需求移除“预期收益/GitHub”，保留分类/标题/内容与联系方式 -->
          <el-form :model="suggestForm" label-width="96px" class="form">
            <el-form-item label="建议分类">
              <el-select v-model="suggestForm.category" placeholder="选择分类">
                <el-option label="新增栏目" value="新增栏目" />
                <el-option label="导航优化" value="导航优化" />
                <el-option label="交互改进" value="交互改进" />
                <el-option label="性能优化" value="性能优化" />
                <el-option label="移动端体验" value="移动端体验" />
                <el-option label="其他" value="其他" />
              </el-select>
            </el-form-item>
            <el-form-item label="标题">
              <el-input v-model="suggestForm.title" placeholder="建议概述（可选）" />
            </el-form-item>
            <el-form-item label="建议内容">
              <el-input type="textarea" v-model="suggestForm.description" :rows="4" placeholder="详细建议与场景" />
            </el-form-item>
            <el-form-item label="联系邮箱">
              <el-input v-model="suggestForm.contactEmail" placeholder="可选" />
            </el-form-item>
            <el-form-item label="QQ">
              <el-input v-model="suggestForm.contactQq" placeholder="可选" />
            </el-form-item>
            <!-- 根据需求：移除 GitHub 联系方式，仅保留邮箱/QQ -->
            <el-form-item>
              <el-button type="primary" :loading="suggestSubmitting" @click="submitSuggest">提交站点建议</el-button>
            </el-form-item>
          </el-form>
        </div>
        <div v-else-if="activeKey==='contact'" class="panel">
          <h2 class="panel-title">联系方式</h2>
          <ul class="contact-list">
            <li>
              <span>QQ：</span>
              <a :href="qqLink" target="_blank" rel="noopener">{{ contactQQ }}</a>
            </li>
            <li>
              <span>邮箱：</span>
              <span>{{ contactEmail }}</span>
            </li>
            <li>
              <span>GitHub：</span>
              <a :href="githubLink" target="_blank" rel="noopener">{{ contactGithub }}</a>
            </li>
          </ul>
        </div>
        <div v-else-if="['copyright','privacy','terms'].includes(activeKey)">
          <component :is="LegalPanels[activeKey]" />
        </div>
      </section>
    </div>
    <template #footer>
      <div class="footer-actions">
        <el-button @click="visible=false">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, defineAsyncComponent } from 'vue'
import { http } from '@/api/http'
import { ElMessage } from 'element-plus'

const visible = ref(false)
const activeKey = ref('feedback')
const contentRef = ref(null)

const tabs = [
  { key: 'feedback', label: '问题反馈', icon: 'https://api.iconify.design/mdi/bug.svg' },
  { key: 'suggest', label: '站点建议', icon: 'https://api.iconify.design/mdi/lightbulb-on.svg' },
  { key: 'contact', label: '联系方式', icon: 'https://api.iconify.design/mdi/account-box.svg' },
  { key: 'copyright', label: '版权声明', icon: 'https://api.iconify.design/mdi/copyright.svg' },
  { key: 'privacy', label: '隐私政策', icon: 'https://api.iconify.design/mdi/shield-key.svg' },
  { key: 'terms', label: '用户协议', icon: 'https://api.iconify.design/mdi/file-document.svg' },
]

const title = computed(() => {
  const it = tabs.find(t => t.key === activeKey.value)
  return it ? it.label : '联系我们'
})

function setActive(k){
  activeKey.value = k
  try{ contentRef.value?.scrollTo?.({ top: 0, behavior: 'smooth' }) }catch{}
}

const contactEmail = 'wsk7931@163.com'
const contactQQ = (import.meta.env.VITE_CONTACT_QQ || '123456789')
const contactGithub = (import.meta.env.VITE_CONTACT_GITHUB || 'your-github')
const qqLink = computed(() => `https://wpa.qq.com/msgrd?v=3&uin=${encodeURIComponent(contactQQ)}&site=qq&menu=yes`)
  const githubLink = computed(() => `https://github.com/${encodeURIComponent(contactGithub)}`)

// 法律文本面板：按需异步加载，便于后期维护与独立更新
const LegalPanels = {
  copyright: defineAsyncComponent(() => import('./legal/CopyrightPanel.vue')),
  privacy: defineAsyncComponent(() => import('./legal/PrivacyPanel.vue')),
  terms: defineAsyncComponent(() => import('./legal/TermsPanel.vue')),
}

function onOpen(ev){
  try{
    const d = ev?.detail || {}
    const tab = String(d.tab || '').trim()
    visible.value = true
    if (tab) setActive(tab)
  }catch{}
}

function copy(text){
  try{
    if (navigator.clipboard?.writeText){ navigator.clipboard.writeText(text) }
  }catch{}
}

onMounted(() => { window.addEventListener('open-contact-modal', onOpen) })
onUnmounted(() => { try{ window.removeEventListener('open-contact-modal', onOpen) }catch{} })

// —— 表单状态：问题反馈 ——
// 表单数据结构调整：移除 steps/expected/actual/contactGithub，仅保留必要字段
const feedbackForm = ref({ module:'', pagePath:'', title:'', description:'', contactEmail:'', contactQq:'' })
const feedbackSubmitting = ref(false)
async function submitFeedback(){
  if (feedbackSubmitting.value) return
  feedbackSubmitting.value = true
  try{
    const payload = { ...feedbackForm.value }
    const { data } = await http.post('/feedback/issue', payload)
    if (data?.ok){
      ElMessage.success('问题反馈已提交，感谢您的支持！')
      // 重置为当前字段集合
      feedbackForm.value = { module:'', pagePath:'', title:'', description:'', contactEmail:'', contactQq:'' }
    }
  }catch(e){ ElMessage.error(e?.response?.data?.message || '提交失败，请稍后重试') }
  finally{ feedbackSubmitting.value = false }
}

// —— 表单状态：站点建议 ——
// 表单数据结构调整：移除 expectedBenefit/contactGithub，仅保留必要字段
const suggestForm = ref({ category:'', title:'', description:'', contactEmail:'', contactQq:'' })
const suggestSubmitting = ref(false)
async function submitSuggest(){
  if (suggestSubmitting.value) return
  suggestSubmitting.value = true
  try{
    const payload = { ...suggestForm.value }
    const { data } = await http.post('/feedback/suggest', payload)
    if (data?.ok){
      ElMessage.success('站点建议已提交，感谢您的建议！')
      // 重置为当前字段集合
      suggestForm.value = { category:'', title:'', description:'', contactEmail:'', contactQq:'' }
    }
  }catch(e){ ElMessage.error(e?.response?.data?.message || '提交失败，请稍后重试') }
  finally{ suggestSubmitting.value = false }
}
</script>

<style scoped>
:deep(.el-overlay.contact-modal .el-dialog){
  width: 70vw;
  height: 70vh !important;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}
:deep(.el-dialog.contact-modal){
  width: 70vw;
  height: 70vh !important;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}
:deep(.el-overlay.contact-modal .el-dialog__header){ flex: 0 0 auto; }
:deep(.el-dialog.contact-modal .el-dialog__header){ flex: 0 0 auto; }
:deep(.el-overlay.contact-modal .el-dialog__body){
  flex: 1 1 auto;
  min-height: 0;
  padding: 0;
  overflow: hidden;
}
:deep(.el-dialog.contact-modal .el-dialog__body){
  flex: 1 1 auto;
  min-height: 0;
  padding: 0;
  overflow: hidden;
}
:deep(.el-overlay.contact-modal .el-dialog__footer){ flex: 0 0 auto; }
:deep(.el-dialog.contact-modal .el-dialog__footer){ flex: 0 0 auto; }
.modal-layout{ 
  display: grid; 
  grid-template-columns: 220px 1fr; 
  height: 100%; 
  min-height: 0; }
.menu{ border-right: 1px solid #e5e7eb; padding: 12px; display: flex; flex-direction: column; gap: 6px; overflow: auto; }
.menu-item{ display: flex; align-items: center; gap: 8px; width: 100%; padding: 10px 12px; border: 1px solid transparent; border-radius: 8px; background: transparent; cursor: pointer; color: #374151; }
.menu-item:hover{ background: #f3f4f6; }
.menu-item.active{ background: #eef2ff; border-color: #c7d2fe; color: #1f2937; }
.content{ 
  padding: 16px 20px; 
  overflow: auto; 
  height: 100%; 
  box-sizing: border-box; }
.content :deep(.panel){ display: grid; gap: 12px; min-height: 0; }
.content :deep(.panel-title){ font-size: 18px; margin: 0; }
.desc{ color: #6b7280; }
.template{ border: 1px solid #e5e7eb; border-radius: 10px; overflow: hidden; }
.template-header{ display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; background: #f9fafb; border-bottom: 1px solid #e5e7eb; }
/* 移动端适配：保持 70% 视窗尺寸，内部滚动 */
@media (max-width: 768px){
  :deep(.el-overlay.contact-modal .el-dialog){ width: 70vw; height: 70vh; }
  :deep(.el-dialog.contact-modal){ width: 70vw; height: 70vh; }
  .modal-layout{ grid-template-columns: 1fr; }
}
.copy-btn{ padding: 6px 10px; background: #4f46e5; color: #fff; border: none; border-radius: 6px; cursor: pointer; }
.copy-btn:hover{ background: #4338ca; }
.template-body{ margin: 0; padding: 12px; white-space: pre-wrap; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace; }
.tips h3{ margin: 0; font-size: 16px; }
.tips ul{ margin: 0; padding-left: 18px; color: #4b5563; }
.contact-list{ list-style: none; padding: 0; margin: 0; display: grid; gap: 8px; }
.contact-list li{ display: flex; gap: 8px; align-items: center; }
.content :deep(.rich p){ margin: 0; line-height: 1.8; color: #374151; }
@media (max-width: 768px){
  .modal-layout{ grid-template-columns: 1fr; }
  .menu{ border-right: none; border-bottom: 1px solid #e5e7eb; flex-direction: row; flex-wrap: wrap; gap: 8px; }
  .menu-item{ width: auto; }
}
</style>
<style>
.el-dialog.contact-modal{
  width: 70vw;
  height: 70vh !important;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}
.el-dialog.contact-modal .el-dialog__header{ flex: 0 0 auto; }
.el-dialog.contact-modal .el-dialog__body{ 
  flex: 1 1 auto; 
  min-height: 0; 
  padding: 0; 
  overflow: hidden; }
.el-dialog.contact-modal .el-dialog__footer{ flex: 0 0 auto; }
@media (max-width: 768px){
  .el-dialog.contact-modal{ width: 70vw; height: 70vh; }
}
</style>
