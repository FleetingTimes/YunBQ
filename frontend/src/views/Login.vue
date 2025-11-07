<template>
  <!-- 在登录页引入顶栏组件：
       目的：保持站点导航一致性，便于用户在登录页也能返回广场或进入公开页面；
       说明：顶栏自身会在未登录时抑制 401 重定向（如未读消息计数），因此可安全展示在登录页。 -->
  <AppTopBar />
  <div class="auth-wrapper with-topbar">
    <div class="auth-card p-1 rot-1">
      <div class="auth-title">
        <img src="https://api.iconify.design/mdi/account-circle.svg" alt="login" width="26" height="26"/>
  <!-- 文案重命名：品牌统一为“拾·言” -->
  <h2>登录拾·言</h2>
      </div>
      <!-- 登录模式切换：密码登录 / 邮箱登录
           交互说明：
           - 默认为“密码登录”（按用户名 + 密码）；
           - 切换为“邮箱登录”后展示邮箱输入框并按邮箱 + 密码登录；
           - 样式采用按钮式单选，贴近项目风格。 -->
      <div class="login-mode-switch">
        <el-radio-group v-model="loginMode" size="small">
          <el-radio-button label="password">密码登录</el-radio-button>
          <el-radio-button label="email">邮箱登录</el-radio-button>
        </el-radio-group>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px" @submit.prevent="onSubmit">
        <!-- 当模式为“密码登录”时：使用用户名登录 -->
        <el-form-item v-if="loginMode === 'password'" label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <!-- 当模式为“邮箱登录”时：使用邮箱登录（后端新增 /api/auth/login/email 支持） -->
        <el-form-item v-else label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱地址" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="验证码" prop="captchaCode">
          <div style="display:flex; gap:8px; align-items:center;">
            <el-input v-model="form.captchaCode" placeholder="请输入验证码" style="width:160px;" />
            <img :src="captcha.image" :key="captcha.id" alt="captcha" style="height:38px;border-radius:6px;border:1px solid #ebeef5;background:#fff;" @click="refreshCaptcha" @error="onCaptchaError" />
          </div>
        </el-form-item>
        <!-- 操作区：同一行展示，左侧“忘记密码”，右侧“登录/注册”；QQ 图标置于注册按钮下方
             目的：满足“忘记密码放左边，忘记密码和登录注册同一行；QQ 图标在注册下面”的布局需求
             说明：使用绝对定位固定到底部，容器宽度撑满卡片内容宽度；左右两块分别独立布局。 -->
        <div class="auth-actions actions-bottom-row">
          <!-- 左侧：忘记密码（文本按钮），靠左对齐 -->
          <div class="left">
            <el-button type="text" @click="$router.push('/forgot')">忘记密码</el-button>
          </div>
          <!-- 右侧：登录/注册同一行，QQ 图标放在其下方 -->
          <div class="right">
            <div class="primary-actions">
              <el-button type="primary" :loading="loading" @click="onSubmit">登录</el-button>
              <el-button @click="$router.push('/register')">去注册</el-button>
            </div>
            <!-- QQ 图标按钮置于“注册”下方：按钮组之下单独一行，右对齐 -->
            <div class="qq-below">
              <el-tooltip content="QQ 登录" placement="top">
                <el-button class="qq-button" circle @click="loginWithQQ">
                  <img src="https://api.iconify.design/mdi/qqchat.svg" alt="QQ" width="20" height="20" />
                </el-button>
              </el-tooltip>
            </div>
          </div>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { http, API_BASE } from '@/api/http';
import { setToken } from '@/utils/auth';
// 顶栏组件：用于在登录页展示全局导航与入口（如“拾言小镇”）
// 说明：组件内部对未登录的交互（添加拾言/消息/喜欢/收藏）会提示“请先登录”，不会强制跳转登录页。
import AppTopBar from '@/components/AppTopBar.vue';

const router = useRouter();
const route = useRoute();
const formRef = ref();
// 登录表单模型：根据登录模式切换字段（用户名或邮箱）
const form = reactive({ username:'', email:'', password:'', captchaCode:'' });
// 登录模式：'password' 表示用户名+密码；'email' 表示邮箱+密码
const loginMode = ref('password');
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  email: [
    { required: () => loginMode.value === 'email', message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: ['blur','change'] }
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
};
const loading = ref(false);
const captcha = reactive({ id:'', image:'' });

async function refreshCaptcha(){
  try{
    const { data } = await http.get('/captcha');
    captcha.id = data.id;
    captcha.image = data.image; // data:image/png;base64,...
  }catch(e){
    ElMessage.error('获取验证码失败');
  }
}

function onCaptchaError(){
  // 图片加载失败时自动刷新一次
  refreshCaptcha();
}

onMounted(() => { refreshCaptcha(); });

async function onSubmit(){
  await formRef.value?.validate?.();
  loading.value = true;
  try{
    const { data: cv } = await http.post('/captcha/verify', { id: captcha.id, code: form.captchaCode });
    if (!cv?.valid){
      ElMessage.error('验证码错误');
      await refreshCaptcha();
      return;
    }
    // 根据模式调用不同登录接口：
    // - 密码登录：/api/auth/login（用户名 + 密码）
    // - 邮箱登录：/api/auth/login/email（邮箱 + 密码）
    const payload = loginMode.value === 'email'
      ? { email: form.email, password: form.password }
      : { username: form.username, password: form.password };
    const url = loginMode.value === 'email' ? '/auth/login/email' : '/auth/login';
    const { data } = await http.post(url, payload);
    if (data?.token) {
      setToken(data.token, true);
      ElMessage.success('登录成功');
      // 登录成功后的跳转：优先回原目标路径
      // 说明：
      // - 路由守卫在拦截未登录访问受保护页面时，会设置 query.redirect 为“未编码的完整路径”；
      // - 全局响应拦截器在 401 时使用 window.location.hash 跳转登录页，为确保安全，
      //   会将当前 hash 路径做 encodeURIComponent 编码后放入 redirect 参数；
      // - 这里统一尝试 decodeURIComponent，若非编码字符串则保持原值，确保两类来源都能正确跳转。
      const r = route.query?.redirect;
      let to = '/';
      if (typeof r === 'string' && r) {
        try { to = decodeURIComponent(r); }
        catch { to = r; }
        // 规范化跳转目标：
        // 1) 若指向登录页本身（例如 '/login' 或 '/login?...'），回退到首页，避免“登录后仍在登录页”的循环。
        if (to.startsWith('/login')) { to = '/'; }
        // 2) 若包含 hash 片段（形如 '/#/likes'），去掉前导 '#/' 统一为 '/likes'，适配 hash 路由。
        if (to.startsWith('/#/')) { to = to.slice(2); }
      }
      router.replace(to);
    } else {
      ElMessage.error('登录失败：未返回 token');
    }
  }catch(e){
    ElMessage.error(e?.response?.data?.message || '登录失败');
  }finally{
    loading.value = false;
  }
}
function loginWithQQ(){ window.location.href = API_BASE + '/auth/qq/login'; }
function loginWithWeChat(){ window.location.href = API_BASE + '/auth/wechat/login'; }
</script>

<style scoped>
/* 顶栏占据页面顶部高度时，给登录内容增加顶部间距，避免视觉重叠。
   注：若后续调整顶栏高度或布局，可在这里同步更新数值。*/
.with-topbar { padding-top: 68px; }

/* 高度填充：正文区占满“顶栏与底栏之间”的可视高度
   计算：100vh - 顶栏高度(默认 68px) - 底栏高度(默认 0)
   说明：
   - 通过 CSS 变量支持未来全局自定义 --topbar-height / --footer-height；
   - 与上方 padding-top 配合后，总高度恰好填充视口，不会出现额外溢出。 */
.auth-wrapper.with-topbar { min-height: calc(100vh - var(--topbar-height, 68px) - var(--footer-height, 0px)); box-sizing: border-box; }

/* 正文卡片宽度控制：统一登录/注册/找回密码页的卡片宽度为 460px
   说明：覆盖全局 .auth-card 的 max-width=440px，使三页视觉一致 */
.auth-card { max-width: 460px; padding-bottom: 88px; /* 预留更充足底部空间，避免底部操作区遮挡表单内容 */ }

/* 登录模式切换条样式：靠近标题，简洁对比 */
.login-mode-switch { margin: 8px 0 12px; display: flex; justify-content: flex-start; }

/* 底部操作区：同一行左右分布，固定在卡片底部，撑满卡片宽度 */
.actions-bottom-row { position: absolute; left: 18px; right: 18px; bottom: 18px; display:flex; align-items:center; justify-content: space-between; }
.actions-bottom-row .left { display:flex; align-items:center; }
.actions-bottom-row .right { display:flex; flex-direction: column; align-items: flex-end; gap: 6px; }
.primary-actions { display:flex; gap: 8px; }
.qq-below { display:flex; justify-content: flex-end; }
.qq-button { border-color: #c0c4cc; }
</style>