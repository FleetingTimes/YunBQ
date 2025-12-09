<template>
  <!-- 顶栏由根布局承载，此页仅渲染正文卡片 -->
  <div class="auth-wrapper">
    <div class="auth-card p-2 rot-2">
      <div class="auth-title">
        <img src="https://api.iconify.design/mdi/account-plus.svg" alt="register" width="26" height="26"/>
        <h2>注册新用户</h2>
      </div>
      <el-form ref="formRef" @submit.prevent="onSubmit" label-width="80px" :model="form" :rules="rules">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="可选" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱，用于找回密码" />
        </el-form-item>
        <el-form-item label="验证码" prop="captchaCode">
          <div style="display:flex; gap:8px; align-items:center;">
            <el-input v-model="form.captchaCode" placeholder="请输入验证码" style="width:160px;" />
            <img :src="captcha.image" :key="captcha.id" alt="captcha" style="height:38px;border-radius:6px;border:1px solid #ebeef5;background:#fff;" @click="refreshCaptcha" @error="onCaptchaError" />
          </div>
        </el-form-item>
        <div class="auth-actions">
          <el-button type="primary" :loading="loading" @click="onSubmit">注册</el-button>
          <el-button @click="$router.push('/login')">去登录</el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { http } from '@/api/http';
// 顶栏组件：在注册页展示全局导航与链接（如“拾言小镇”），保持站点一致性
// 顶栏由根布局承载，此页不再单独引入

const router = useRouter();
const formRef = ref();
const form = reactive({ username:'', password:'', nickname:'', email:'', captchaCode:'' });
// 表单校验规则：
// - 用户名：3-20 位，仅字母/数字/下划线/短横线，首尾必须为字母或数字；禁止空格与保留词
// - 密码：至少 8 位，包含大小写字母与数字；
// - 验证码：必填；
// - 邮箱：若填写，需符合 Email 格式（后端亦校验）。
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度需 3-20 个字符', trigger: ['blur','change'] },
    {
      validator: (rule, value, cb) => {
        const v = String(value || '').trim()
        if (!v) return cb(new Error('请输入用户名'))
        // 禁止空白字符
        if (/\s/.test(v)) return cb(new Error('用户名不允许空格'))
        // 仅允许字母/数字/_/-，且首尾为字母或数字；长度通过上面的 min/max 控制
        const ok = /^[A-Za-z0-9](?:[A-Za-z0-9_-]{1,18}[A-Za-z0-9])?$/.test(v)
        if (!ok) return cb(new Error('仅限字母、数字、下划线、短横线，且首尾需为字母或数字'))
        // 不允许连续符号（可选限制）
        if (/__|--/.test(v)) return cb(new Error('不允许连续下划线或短横线'))
        // 保留词拦截（大小写不敏感）
        const reserved = ['admin','root','system','support']
        if (reserved.includes(v.toLowerCase())) return cb(new Error('该用户名为保留词，不能使用'))
        cb()
      },
      trigger: ['blur','change']
    }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度需 8-64 位', trigger: ['blur','change'] },
    {
      validator: (rule, value, cb) => {
        const v = String(value || '')
        const strong = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/.test(v)
        if (!strong) return cb(new Error('需包含大小写字母与数字，且不少于8位'))
        cb()
      },
      trigger: ['blur','change']
    }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: ['blur','change'] }
  ],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
};
const loading = ref(false);
const captcha = reactive({ id:'', image:'' });

async function refreshCaptcha(){
  try{
    const { data } = await http.get('/captcha');
    captcha.id = data.id;
    captcha.image = data.image; // 期望为 data:image/png;base64,...
  }catch(e){
    ElMessage.error('获取验证码失败');
  }
}

function onCaptchaError(){
  refreshCaptcha();
}

onMounted(() => { refreshCaptcha(); });

async function onSubmit(){
  await formRef.value?.validate?.();
  loading.value = true;
  try{
    const { data: cv } = await http.post('/captcha/verify', { id: captcha.id, code: form.captchaCode });
    if (!cv?.valid) {
      ElMessage.error('验证码错误');
      await refreshCaptcha();
      return;
    }
    const { data } = await http.post('/auth/register', { username:form.username, password:form.password, nickname:form.nickname, email:form.email });
    if (data?.id || data?.success) {
      ElMessage.success('注册成功');
      router.replace('/login');
    } else {
      ElMessage.error(data?.message || '注册失败');
    }
  }catch(e){
    ElMessage.error(e?.response?.data?.message || '注册失败');
  }finally{
    loading.value = false;
  }
}
</script>

<style scoped>
/* 高度填充：根布局已处理顶栏高度，本页仅需占满可用空间 */
.auth-wrapper { min-height: calc(100vh - var(--footer-height, 0px)); box-sizing: border-box; }

/* 正文卡片宽度控制：统一登录/注册/找回密码页的卡片宽度为 460px
   说明：覆盖全局 .auth-card 的 max-width=440px，使三页视觉一致 */
.auth-card { max-width: 460px; }

/* 移动端布局优化：自适应窄屏，避免验证码与按钮拥挤 */
@media (max-width: 480px) {
  /* 顶部间距略缩小，提升可视空间 */
  /* 根布局已承载顶栏，无需额外占位 */
  /* 卡片宽度自适应窄屏 */
  .auth-card { max-width: 420px; }
  /* 验证码输入与图片在窄屏下换行，避免过度挤压 */
  .auth-card .el-form-item [style*="display:flex"] { flex-wrap: wrap; }
}
</style>
