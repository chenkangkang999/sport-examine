<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { get, post, loadSession } from '../api';
import { state, clearDraft, notify, localDate } from '../state';
import FaIcon from '../components/FaIcon.vue';
import AsyncState from '../components/AsyncState.vue';
import ConfirmDialog from '../components/ConfirmDialog.vue';
const router = useRouter(),
  loading = ref(true),
  error = ref(''),
  tab = ref('menu'),
  busy = ref(false),
  formError = ref(''),
  logout = ref(false),
  profile = reactive({}),
  password = reactive({ oldPassword: '', newPassword: '', confirm: '' });
async function load() {
  loading.value = true;
  error.value = '';
  try {
    Object.assign(profile, await get('/users/me'), { identityCard: '' });
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
async function saveProfile() {
  busy.value = true;
  formError.value = '';
  try {
    const payload = { ...profile };
    if (!payload.identityCard) delete payload.identityCard;
    await post('/users/updateProfile', payload);
    await loadSession(true);
    await load();
    notify('个人资料已更新', 'success');
  } catch (e) {
    formError.value = e.message;
  } finally {
    busy.value = false;
  }
}
async function changePassword() {
  formError.value = '';
  if (password.newPassword !== password.confirm) {
    formError.value = '两次输入的新密码不一致';
    return;
  }
  busy.value = true;
  try {
    await post('/users/changePassword', {
      oldPassword: password.oldPassword,
      newPassword: password.newPassword,
    });
    Object.assign(password, { oldPassword: '', newPassword: '', confirm: '' });
    notify('登录密码已更新', 'success');
  } catch (e) {
    formError.value = e.message;
  } finally {
    busy.value = false;
  }
}
async function signout() {
  busy.value = true;
  try {
    await post('/auth/logout');
    state.user = null;
    state.ready = false;
    state.csrf = '';
    clearDraft();
    router.replace('/login');
  } catch (e) {
    notify(e.message);
  } finally {
    busy.value = false;
    logout.value = false;
  }
}
function switchTab(value) {
  tab.value = value;
  formError.value = '';
}
function explain(name) {
  notify(`${name}为原型保留入口，本课程项目暂未接入对应外部服务。`, 'info');
}
onMounted(load);
</script>
<template>
  <div class="prototype-personal">
    <template v-if="tab === 'menu'"
      ><section class="personal-info">
        <img src="/img/user.png" alt="用户头像" />
        <div>
          <p>{{ state.user?.realName }}</p>
          <small>账号：{{ state.user?.userId }}</small>
        </div>
      </section>
      <nav class="personal-links">
        <RouterLink to="/appointmentlist"
          ><FaIcon name="userPlus" /><span>我的预约</span><FaIcon name="arrow" /></RouterLink
        ><RouterLink to="/reportlist"
          ><FaIcon name="note" /><span>我的体检报告</span><FaIcon name="arrow" /></RouterLink
        ><RouterLink to="/persons"
          ><FaIcon name="users" /><span>家人健康档案</span><FaIcon name="arrow" /></RouterLink
        ><button @click="switchTab('profile')">
          <FaIcon name="edit" /><span>账号资料</span><FaIcon name="arrow" /></button
        ><button @click="explain('我的服务')">
          <FaIcon name="service" /><span>我的服务</span><FaIcon name="arrow" /></button
        ><button @click="explain('我的医生')">
          <FaIcon name="bed" /><span>我的医生</span><FaIcon name="arrow" /></button
        ><button @click="explain('问诊订单')">
          <FaIcon name="note" /><span>问诊订单</span><FaIcon name="arrow" /></button
        ><button @click="explain('商城订单')">
          <FaIcon name="cart" /><span>商城订单</span><FaIcon name="arrow" /></button
        ><button @click="logout = true">
          <FaIcon name="logout" /><span>退出登录</span><FaIcon name="arrow" />
        </button></nav
    ></template>
    <section v-else class="account-panel">
      <div class="account-heading">
        <button class="text-button" @click="switchTab('menu')">
          <FaIcon name="back" />返回个人中心
        </button>
      </div>
      <div class="tabs">
        <button :class="{ active: tab === 'profile' }" @click="switchTab('profile')">
          个人资料</button
        ><button :class="{ active: tab === 'password' }" @click="switchTab('password')">
          修改密码
        </button>
      </div>
      <AsyncState :loading="loading" :error="error" @retry="load"
        ><form v-if="tab === 'profile'" class="form account-form" @submit.prevent="saveProfile">
          <label>手机账号<input :value="profile.userId" disabled /></label
          ><label>真实姓名<input v-model.trim="profile.realName" required maxlength="20" /></label
          ><label
            >性别<select v-model.number="profile.sex">
              <option :value="1">男</option>
              <option :value="0">女</option>
            </select></label
          ><label
            >出生日期<input
              v-model="profile.birthday"
              type="date"
              :max="localDate()"
              required /></label
          ><label
            >身份证号码<input
              v-model.trim="profile.identityCard"
              maxlength="18"
              placeholder="留空表示保留原证件"
            /><small class="field-hint"
              >当前：{{ profile.identityCardMasked }}；有未归档预约时不可更换证件。</small
            ></label
          >
          <p v-if="formError" class="inline-error" role="alert">{{ formError }}</p>
          <button class="btn full" :disabled="busy">{{ busy ? '保存中…' : '保存个人资料' }}</button>
        </form>
        <form v-else class="form account-form" @submit.prevent="changePassword">
          <label
            >当前密码<input
              v-model="password.oldPassword"
              type="password"
              autocomplete="current-password"
              required /></label
          ><label
            >新密码<input
              v-model="password.newPassword"
              type="password"
              autocomplete="new-password"
              minlength="8"
              maxlength="72"
              placeholder="至少 8 位密码"
              required /></label
          ><label
            >确认新密码<input
              v-model="password.confirm"
              type="password"
              autocomplete="new-password"
              minlength="8"
              required
          /></label>
          <p v-if="formError" class="inline-error" role="alert">{{ formError }}</p>
          <button class="btn full" :disabled="busy">{{ busy ? '更新中…' : '更新登录密码' }}</button>
        </form></AsyncState
      >
    </section>
  </div>
  <ConfirmDialog
    :open="logout"
    title="退出当前账号？"
    text="退出后将清除本设备的预约草稿，订单与报告仍保存在账号中。"
    confirm-text="退出登录"
    :busy="busy"
    @close="logout = false"
    @confirm="signout"
  />
</template>
