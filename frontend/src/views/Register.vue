<template>
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