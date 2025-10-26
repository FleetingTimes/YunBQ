<template>
  <div class="auth-wrapper">
    <div class="auth-card p-1 rot-1">
      <div class="auth-title">
        <img src="https://api.iconify.design/mdi/lock-reset.svg" alt="forgot" width="26" height="26"/>
        <h2>找回密码</h2>
      </div>
      <el-tabs v-model="tab">
        <el-tab-pane label="发送验证码" name="send">
          <el-form ref="sendRef" :model="sendForm" label-width="80px">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="sendForm.email" placeholder="请输入绑定邮箱" />
            </el-form-item>
            <el-form-item label="验证码" prop="captchaCode">
              <div style="display:flex; gap:8px; align-items:center;">
                <el-input v-model="sendForm.captchaCode" placeholder="请输入图片验证码" style="width:160px;" />
                <img :src="captcha.image" :key="captcha.id" alt="captcha" style="height:38px;border-radius:6px;border:1px solid #ebeef5;background:#fff;" @click="refreshCaptcha" @error="onCaptchaError" />
              </div>
            </el-form-item>
            <div class="auth-actions" style="justify-content:space-between;">
              <el-button type="text" @click="$router.push('/login')">返回登录</el-button>
              <el-button type="primary" @click="send" :loading="loading">发送验证码</el-button>
            </div>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="重置密码" name="reset">
          <el-form ref="resetRef" :model="resetForm" label-width="80px">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="resetForm.email" placeholder="请输入绑定邮箱" />
            </el-form-item>
            <el-form-item label="邮箱验证码" prop="code">
              <el-input v-model="resetForm.code" placeholder="请输入邮箱验证码" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="resetForm.newPassword" type="password" show-password placeholder="请输入新密码" />
            </el-form-item>
            <div class="auth-actions" style="justify-content:flex-end;">
              <el-button type="primary" @click="reset" :loading="loading">重置密码</el-button>
            </div>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { http } from '@/api/http';

const tab = ref('send');
const loading = ref(false);
const captcha = reactive({ id:'', image:'' });
const sendForm = reactive({ email:'', captchaCode:'' });
const resetForm = reactive({ email:'', code:'', newPassword:'' });

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
  refreshCaptcha();
}

onMounted(() => { refreshCaptcha(); });

async function send(){
  try{
    loading.value = true;
    const payload = { email: sendForm.email, captchaId: captcha.id, captchaCode: sendForm.captchaCode };
    const { data } = await http.post('/auth/forgot', payload);
    if (data?.ok){
      ElMessage.success('已发送邮箱验证码，请查收');
      tab.value = 'reset';
    } else {
      const msg = data?.message || '发送失败';
      ElMessage.error(msg);
    }
  }catch(e){
    const msg = e?.response?.data?.message || '发送失败';
    ElMessage.error(msg);
  }finally{ loading.value = false; }
}

async function reset(){
  try{
    loading.value = true;
    const payload = { email: resetForm.email, code: resetForm.code, newPassword: resetForm.newPassword };
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
  }finally{ loading.value = false; }
}
</script>