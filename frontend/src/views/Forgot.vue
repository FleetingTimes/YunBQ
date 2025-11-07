<template>
  <!-- 顶栏：与登录/注册页保持一致的导航体验 -->
  <AppTopBar />
  <!-- 正文：加入 with-topbar 占位，避免卡片与顶栏重叠 -->
  <div class="auth-wrapper with-topbar">
    <div class="auth-card p-1 rot-1">
      <div class="auth-title">
        <img src="https://api.iconify.design/mdi/lock-reset.svg" alt="forgot" width="26" height="26"/>
        <h2>找回密码</h2>
      </div>

      <!-- 单页联动布局：邮箱 -> 邮箱验证码(缩短，后跟发送按钮) -> 新密码 -> 图片验证码（放在新密码下面） -->
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <!-- 邮箱 -->
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入绑定邮箱" />
        </el-form-item>

        <!-- 邮箱验证码（输入框缩短） + 发送验证码按钮（紧随邮箱验证码后） -->
        <el-form-item label="邮箱验证码" prop="code">
          <div style="display:flex; gap:8px; align-items:center;">
            <el-input v-model="form.code" placeholder="请输入邮箱验证码（6位数字）" style="width:160px;" />
            <!-- 发送按钮：允许无图形验证码，仅需邮箱格式正确；倒计时中禁用 -->
            <el-button type="primary" @click="send" :loading="loadingSend" :disabled="sendCooldown > 0 || !canSendEmailOnly">发送验证码</el-button>
            <!-- 自动校验状态提示：当输入满6位并校验通过时展示对勾图标 -->
            <svg v-if="verified" class="ok-icon" viewBox="0 0 16 16" aria-label="邮箱验证码已验证" title="邮箱验证码已验证">
              <path fill="#67c23a" d="M8 1a7 7 0 1 0 0 14A7 7 0 0 0 8 1Zm3.146 4.646a.5.5 0 0 1 .708.708l-4.5 4.5a.5.5 0 0 1-.708 0l-2-2a.5.5 0 1 1 .708-.708L7 9.793l4.146-4.147Z"/>
            </svg>
          </div>
        </el-form-item>

        <!-- 新密码（强度校验） -->
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password placeholder="请输入新密码" />
          <!-- 本地强度校验通过则显示对勾图标（用户输入即反馈，提升体验） -->
          <svg v-if="passwordStrong" class="ok-icon" viewBox="0 0 16 16" aria-label="密码强度达标" title="密码强度达标">
            <path fill="#67c23a" d="M8 1a7 7 0 1 0 0 14A7 7 0 0 0 8 1Zm3.146 4.646a.5.5 0 0 1 .708.708l-4.5 4.5a.5.5 0 0 1-.708 0l-2-2a.5.5 0 1 1 .708-.708L7 9.793l4.146-4.147Z"/>
          </svg>
          <div style="color:#909399; font-size:13px; margin-top:6px;">
            密码至少 8 位，需包含大小写字母与数字。
          </div>
        </el-form-item>

        <!-- 图片验证码（放在新密码下面；仅支持点击图片刷新，不提供“换一张”按钮） -->
        <el-form-item label="验证码" prop="captchaCode">
          <div style="display:flex; gap:8px; align-items:center;">
            <el-input v-model="form.captchaCode" placeholder="请输入图片验证码" style="width:160px;" />
            <img :src="captcha.image" :key="captcha.id" alt="captcha" style="height:38px;border-radius:6px;border:1px solid #ebeef5;background:#fff;"
                 @click="refreshCaptcha" @error="onCaptchaError" />
            <!-- 图形验证码“已填写且格式符合”时显示对勾（最终校验在后端重置时进行，不预消费） -->
            <svg v-if="captchaReady" class="ok-icon" viewBox="0 0 16 16" aria-label="验证码已填写" title="验证码已填写">
              <path fill="#67c23a" d="M8 1a7 7 0 1 0 0 14A7 7 0 0 0 8 1Zm3.146 4.646a.5.5 0 0 1 .708.708l-4.5 4.5a.5.5 0 0 1-.708 0l-2-2a.5.5 0 1 1 .708-.708L7 9.793l4.146-4.147Z"/>
            </svg>
          </div>
          <template #error>
            <div style="color:#f56c6c;">图片验证码区分大小写，请正确输入</div>
          </template>
        </el-form-item>

        <div class="auth-actions" style="justify-content:space-between;">
          <el-button type="text" @click="$router.push('/login')">返回登录</el-button>
          <el-button type="primary" @click="reset" :loading="loadingReset">重置密码</el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { http } from '@/api/http';
// 引入顶栏组件：在找回密码页同样展示全局导航与入口
import AppTopBar from '@/components/AppTopBar.vue';

// 单页联动：在一个表单里完成“发送邮箱验证码”与“重置密码”两项操作
// 好处：减少来回切换，视觉更聚焦；同时保留严格的校验与风控提示。

// 加载状态区分“发送验证码”与“重置密码”两个动作，避免按钮互相影响
const loadingSend = ref(false);
const loadingReset = ref(false);

// 发送验证码倒计时（与后端 60 秒频率限制一致），>0 时禁用“发送验证码”按钮
const sendCooldown = ref(0);
let cooldownTimer = null;

// 图形验证码对象：id + image（data:image/png;base64,...）
const captcha = reactive({ id:'', image:'' });

// 表单模型：统一在一个对象里管理所有字段
const form = reactive({ email:'', captchaCode:'', code:'', newPassword:'' });
// 邮箱验证码验证状态
const verified = ref(false);
const verifying = ref(false);

// 通用校验规则：邮箱格式、验证码格式、密码强度
const EMAIL_RE = /^[\w.-]+@[\w.-]+\.[A-Za-z]{2,}$/;
const CODE_RE = /^\d{6}$/; // 邮箱验证码：6 位数字
function isStrongPassword(pwd){
  if (!pwd || pwd.length < 8) return false;
  const hasLower = /[a-z]/.test(pwd);
  const hasUpper = /[A-Z]/.test(pwd);
  const hasDigit = /\d/.test(pwd);
  return hasLower && hasUpper && hasDigit;
}

// Element Plus 表单 rules：为每个字段单独定义校验
const rules = {
  email: [
    { required: true, message: '邮箱不能为空', trigger: 'blur' },
    { validator: (_, v, cb) => { if (!v || !EMAIL_RE.test(v.trim())) cb(new Error('邮箱格式不正确')); else cb(); }, trigger: 'blur' }
  ],
  captchaCode: [
    { required: true, message: '图片验证码不能为空', trigger: 'blur' },
    { min: 4, max: 6, message: '验证码长度 4-6 位', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '邮箱验证码不能为空', trigger: 'blur' },
    { validator: (_, v, cb) => { if (!v || !CODE_RE.test((v||'').trim())) cb(new Error('验证码需为 6 位数字')); else cb(); }, trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '新密码不能为空', trigger: 'blur' },
    { validator: (_, v, cb) => { if (!isStrongPassword((v||'').trim())) cb(new Error('密码至少 8 位，需包含大小写字母与数字')); else cb(); }, trigger: 'blur' }
  ]
};

// 发送按钮可用性（仅需邮箱格式正确，不再要求图片验证码）：
const canSendEmailOnly = computed(() => EMAIL_RE.test((form.email||'').trim()));
// 新密码强度（本地即时校验）：满足强度要求则展示对勾
const passwordStrong = computed(() => isStrongPassword((form.newPassword||'').trim()));
// 图形验证码准备就绪（仅检查已填写且长度 4-6；最终校验在后端进行，避免预消费验证码）
const captchaReady = computed(() => {
  const code = (form.captchaCode||'').trim();
  return !!captcha.id && code.length >= 4 && code.length <= 6;
});

// 刷新图形验证码（后端返回 id + base64 图片）：失败时提示并允许重试
async function refreshCaptcha(){
  try{
    const { data } = await http.get('/captcha');
    captcha.id = data.id;
    captcha.image = data.image; // data:image/png;base64,...
  }catch(e){
    ElMessage.error('获取验证码失败');
  }
}

// 图片加载失败时自动刷新
function onCaptchaError(){
  refreshCaptcha();
}

// 启动倒计时（默认 60 秒），期间禁用“发送验证码”按钮
function startCooldown(seconds = 60){
  // 若已在倒计时中，先清理
  if (cooldownTimer) clearInterval(cooldownTimer);
  sendCooldown.value = seconds;
  cooldownTimer = setInterval(() => {
    if (sendCooldown.value <= 1) {
      clearInterval(cooldownTimer);
      cooldownTimer = null;
      sendCooldown.value = 0;
    } else {
      sendCooldown.value -= 1;
    }
  }, 1000);
}

onMounted(() => { refreshCaptcha(); });
onUnmounted(() => { if (cooldownTimer) clearInterval(cooldownTimer); });

// 表单引用（配合 Element Plus rules 局部校验）
const formRef = ref(null);

// 发送邮箱验证码：仅校验邮箱与图片验证码字段，通过 /auth/forgot 触发后端发送 6 位数字验证码
async function send(){
  try{
    const elForm = /** @type {import('element-plus').FormInstance} */ (/** @type {any} */(formRef.value));
    // 仅校验邮箱字段，允许无图片验证码发送
    await elForm.validateField(['email']);
    loadingSend.value = true;
    const payload = { email: (form.email||'').trim() };
    const { data } = await http.post('/auth/forgot', payload);
    if (data?.ok){
      ElMessage.success('已发送邮箱验证码，请查收');
      // 启动 60 秒倒计时（与后端频率限制一致），避免用户短时间内重复点击
      startCooldown(60);
    } else {
      const msg = data?.message || '发送失败';
      ElMessage.error(msg);
    }
  }catch(e){
    const status = e?.response?.status;
    const msg = e?.response?.data?.message || '发送失败';
    ElMessage.error(msg);
    // 当后端返回 429（发送过于频繁）时，仍然启动倒计时，避免用户连续点击
    if (status === 429) startCooldown(60);
  }finally{ loadingSend.value = false; }
}

// 自动校验邮箱验证码：当输入满 6 位数字时，自动调用后端验证接口（不消费验证码）
watch(() => form.code, async (val) => {
  verified.value = false; // 每次输入变化先重置状态
  const code = (val || '').trim();
  const email = (form.email || '').trim();
  // 仅在邮箱格式正确且验证码恰好为 6 位数字时触发
  if (!EMAIL_RE.test(email)) return;
  if (!CODE_RE.test(code)) return;
  // 避免重复触发：若正在验证中则跳过
  if (verifying.value) return;
  try {
    verifying.value = true;
    const payload = { email, code };
    const { data } = await http.post('/auth/verify', payload);
    const ok = data?.ok ?? data === true;
    verified.value = !!ok;
    if (!ok) {
      // 失败时给出提示，便于用户立即修正输入
      ElMessage.error(data?.message || '验证码不正确或已过期');
    }
  } catch (e) {
    verified.value = false;
    const msg = e?.response?.data?.message || '验证失败';
    ElMessage.error(msg);
  } finally {
    verifying.value = false;
  }
});

// 邮箱变更时，重置已验证状态，避免跨邮箱复用验证结果
watch(() => form.email, () => { verified.value = false; });

// 提交重置：校验邮箱 + 6 位数字邮箱验证码 + 新密码（强度校验），调用 /auth/reset 完成重置
async function reset(){
  try{
    const elForm = /** @type {import('element-plus').FormInstance} */ (/** @type {any} */(formRef.value));
    await elForm.validateField(['email','code','newPassword','captchaCode']);
    loadingReset.value = true;
    const payload = {
      email: (form.email||'').trim(),
      code: (form.code||'').trim(),
      newPassword: (form.newPassword||'').trim(),
      // 后端要求最终重置必须提供图形验证码 id 与文本
      captchaId: (captcha.id||'').trim(),
      captchaCode: (form.captchaCode||'').trim()
    };
    const { data } = await http.post('/auth/reset', payload);
    if (data?.ok){
      ElMessage.success('密码重置成功，请登录');
      window.location.hash = '#/login';
    } else {
      const msg = data?.message || '重置失败';
      ElMessage.error(msg);
    }
  }catch(e){
    const msg = e?.response?.data?.message || '重置失败';
    ElMessage.error(msg);
  }finally{ loadingReset.value = false; }
}
</script>

<style scoped>
/* 顶栏占位：为正文增加顶部内边距，防止被吸顶顶栏遮挡
   注：若后续顶栏高度变更，可在此同步调整数值 */
.with-topbar { padding-top: 68px; }

/* 高度填充：让正文容器占满“顶栏与底栏之间”的可视高度
   计算：100vh - 顶栏高度(默认 68px) - 底栏高度(默认 0)
   说明：
   - 使用 CSS 变量以便未来在全局定义 --topbar-height / --footer-height；
   - 保留上方占位 padding-top:68px；配合 min-height 计算后，总高度不超过视口（避免滚动条异常）。 */
.auth-wrapper.with-topbar { min-height: calc(100vh - var(--topbar-height, 68px) - var(--footer-height, 0px)); box-sizing: border-box; }

/* 正文卡片宽度控制：统一与登录/注册页保持一致
   说明：覆盖全局 .auth-card 的 max-width=440px，将三页统一为 460px */
.auth-card { max-width: 460px; }

/* 已验证/达标的小对勾图标（已在前文添加），尺寸统一收敛在页面局部 */
.ok-icon { width: 14px; height: 14px; display:inline-block; vertical-align:middle; margin-left: 4px; }
</style>
<style scoped>
/* 绿色对勾图标样式：统一缩小尺寸，保持对齐与间距 */
.ok-icon {
  width: 14px;   /* 图标宽度（缩小） */
  height: 14px;  /* 图标高度（缩小） */
  margin-left: 4px; /* 与输入控件保持适度间距 */
  vertical-align: middle; /* 与文本/输入框居中对齐 */
  display: inline-block;  /* 保证在行内布局下尺寸生效 */
  flex-shrink: 0;         /* 在 flex 布局中不被压缩 */
}
</style>