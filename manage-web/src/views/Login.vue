<script setup>
import { reactive, ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { login } from '../api';
const router = useRouter(),
  route = useRoute(),
  loading = ref(false),
  error = ref(''),
  formRef = ref();
const form = reactive({ docCode: '', password: '' });
const rules = {
  docCode: [{ required: true, message: '请输入医生工号或管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
};
function fill(role) {
  form.docCode = role === 'admin' ? 'admin' : 'D001';
  form.password = role === 'admin' ? 'Admin123!' : 'Doctor123!';
  error.value = '';
}
async function submit() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  loading.value = true;
  error.value = '';
  try {
    await login(form);
    router.replace(
      typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
        ? route.query.redirect
        : '/orders',
    );
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
</script>
<template>
  <div class="manual-login-page">
    <section class="manual-login-box">
      <h1>体检报告管理系统</h1>
      <div class="manual-login-body">
        <h2>医生 / 管理员登录</h2>
        <el-alert
          v-if="error"
          :title="error"
          type="error"
          :closable="false"
          show-icon
          class="mb-12"
        />
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="70px"
          size="default"
          @submit.prevent="submit"
        >
          <el-form-item label="工号" prop="docCode"
            ><el-input
              v-model.trim="form.docCode"
              placeholder="请输入医生工号或管理员账号"
              autocomplete="username"
          /></el-form-item>
          <el-form-item label="密码" prop="password"
            ><el-input
              v-model="form.password"
              placeholder="请输入密码"
              type="password"
              show-password
              autocomplete="current-password"
              @keyup.enter="submit"
          /></el-form-item>
          <el-form-item
            ><el-button type="primary" :loading="loading" native-type="submit"
              >登录工作台</el-button
            ></el-form-item
          >
        </el-form>
        <div class="manual-login-demo">
          <span>快捷账号：</span
          ><el-button link type="primary" @click="fill('doctor')">体检医生</el-button
          ><el-button link type="primary" @click="fill('admin')">系统管理员</el-button>
        </div>
      </div>
    </section>
  </div>
</template>
