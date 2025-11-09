<template>
  <!-- 在注册页引入顶栏组件：
       目的：与其他页面保持一致的导航体验，用户可随时返回广场或进入公开内容；
       说明：顶栏内部的未登录交互已做提示与 401 抑制，不会导致跳转干扰注册流程。 -->
  <AppTopBar />
  <div class="auth-wrapper with-topbar">
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
          <el-input v-model="form.email" placeholder="可选，用于找回密码" />
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
import AppTopBar from '@/components/AppTopBar.vue';

const router = useRouter();
const formRef = ref();
const form = reactive({ username:'', password:'', nickname:'', email:'', captchaCode:'' });
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
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
/* 顶栏占据页面顶部高度时，为注册卡片增加顶部间距，避免被遮挡。 */
.with-topbar { padding-top: 68px; }

/* 高度填充：正文区占满“顶栏与底栏之间”的可视高度
   计算：100vh - 顶栏高度(默认 68px) - 底栏高度(默认 0)
   说明：
   - 可通过 CSS 变量在全局层面配置 --topbar-height / --footer-height；
   - 与上方 padding-top 占位配合，确保总高度不超过视口。 */
.auth-wrapper.with-topbar { min-height: calc(100vh - var(--topbar-height, 68px) - var(--footer-height, 0px)); box-sizing: border-box; }

/* 正文卡片宽度控制：统一登录/注册/找回密码页的卡片宽度为 460px
   说明：覆盖全局 .auth-card 的 max-width=440px，使三页视觉一致 */
.auth-card { max-width: 460px; }

/* 移动端布局优化：自适应窄屏，避免验证码与按钮拥挤 */
@media (max-width: 480px) {
  /* 顶部间距略缩小，提升可视空间 */
  .with-topbar { padding-top: 60px; }
  /* 卡片宽度自适应窄屏 */
  .auth-card { max-width: 420px; }
  /* 验证码输入与图片在窄屏下换行，避免过度挤压 */
  .auth-card .el-form-item [style*="display:flex"] { flex-wrap: wrap; }
}
</style>