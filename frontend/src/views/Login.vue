<template>
  <div class="auth-wrapper">
    <div class="auth-card p-1 rot-1">
      <div class="auth-title">
        <img src="https://api.iconify.design/mdi/account-circle.svg" alt="login" width="26" height="26"/>
        <h2>登录云便签</h2>
      </div>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px" @submit.prevent="onSubmit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
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
        <div class="auth-actions">
          <el-button type="primary" :loading="loading" @click="onSubmit">登录</el-button>
          <el-button @click="$router.push('/register')">去注册</el-button>
          <el-button type="text" @click="$router.push('/forgot')">忘记密码</el-button>
          <el-button @click="loginWithQQ">QQ 登录</el-button>
          <el-button @click="loginWithWeChat">微信登录</el-button>
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

const router = useRouter();
const route = useRoute();
const formRef = ref();
const form = reactive({ username:'', password:'', captchaCode:'' });
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
    const { data } = await http.post('/auth/login', { username: form.username, password: form.password });
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