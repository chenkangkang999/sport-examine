<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { post, loadSession } from '../api';
import { notify, state, localDate } from '../state';
import FaIcon from '../components/FaIcon.vue';
import Icon from '../components/Icon.vue';
const route = useRoute(),
  router = useRouter(),
  register = computed(() => route.path === '/register');
const form = reactive({
    userId: '',
    password: '',
    confirm: '',
    realName: '',
    sex: 1,
    birthday: '',
    identityCard: '',
  }),
  busy = ref(false),
  error = ref(''),
  showPassword = ref(false);
watch(register, () => {
  showPassword.value = false;
});
async function submit() {
  error.value = '';
  if (register.value && form.password !== form.confirm) {
    error.value = '两次输入的密码不一致';
    return;
  }
  if (!/^1\d{10}$/.test(form.userId)) {
    error.value = '请输入 11 位手机号码';
    return;
  }
  busy.value = true;
  try {
    await post(register.value ? '/users/saveUsers' : '/users/getUsersByUserIdByPass', form);
    await loadSession(true);
    if (!state.user) throw new Error('登录状态未建立，请重试');
    notify(register.value ? '注册成功' : '登录成功', 'success');
    router.replace(
      typeof route.query.next === 'string' && route.query.next.startsWith('/')
        ? route.query.next
        : '/index',
    );
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}
function forgot() {
  notify(
    '本课程项目暂未接入短信找回。可在登录后通过“个人中心 → 账号资料 → 修改密码”修改密码。',
    'info',
  );
}
</script>
<template>
  <div v-if="!register" class="prototype-login">
    <h1>体检预约-登录</h1>
    <section class="login-box">
      <form @submit.prevent="submit">
        <label class="login-input"
          ><FaIcon name="userOutline" /><input
            v-model.trim="form.userId"
            name="phone"
            type="tel"
            maxlength="11"
            autocomplete="tel"
            required
            placeholder="请输入手机号"
            aria-label="手机号码"
        /></label>
        <div class="login-input login-password">
          <FaIcon name="lock" /><input
            v-model="form.password"
            id="login-password"
            name="password"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="current-password"
            minlength="8"
            maxlength="72"
            required
            placeholder="请输入登录密码"
            aria-label="登录密码"
          /><button
            class="login-password-toggle"
            type="button"
            :aria-label="showPassword ? '隐藏密码' : '显示密码'"
            :aria-pressed="showPassword"
            aria-controls="login-password"
            :title="showPassword ? '隐藏密码' : '显示密码'"
            @click="showPassword = !showPassword"
          >
            <Icon :name="showPassword ? 'eyeOff' : 'eye'" :size="18" />
          </button>
        </div>
        <div class="login-reg">
          <RouterLink to="/register">注册</RouterLink
          ><button type="button" @click="forgot">忘记密码</button>
        </div>
        <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
        <button class="login-button" :disabled="busy">{{ busy ? '登录中…' : '登 录' }}</button>
      </form>
    </section>
    <footer class="login-service">
      <div>
        <span></span>
        <p>有疑问请联系客服</p>
        <span></span>
      </div>
      <p>0373-9999</p>
    </footer>
  </div>
  <div v-else class="prototype-register">
    <h1>欢迎注册熙心健康</h1>
    <form @submit.prevent="submit">
      <div class="register-rows">
        <label
          ><span>手机号码</span
          ><input
            v-model.trim="form.userId"
            name="phone"
            type="tel"
            maxlength="11"
            autocomplete="tel"
            required
            placeholder="请输入手机号码" /></label
        ><label
          ><span>真实姓名</span
          ><input
            v-model.trim="form.realName"
            name="realName"
            required
            maxlength="20"
            placeholder="真实姓名便于查看体检报告" /></label
        ><label
          ><span>生日</span
          ><input v-model="form.birthday" type="date" :max="localDate()" required aria-label="生日"
        /></label>
        <fieldset class="register-gender">
          <legend>性别</legend>
          <label><input v-model.number="form.sex" type="radio" name="gender" :value="1" />男</label
          ><label
            ><input v-model.number="form.sex" type="radio" name="gender" :value="0" />女</label
          >
        </fieldset>
        <label
          ><span>身份证号</span
          ><input
            v-model.trim="form.identityCard"
            maxlength="18"
            required
            placeholder="请输入身份证号" /></label
        ><label
          ><span>密码</span
          ><input
            v-model="form.password"
            type="password"
            autocomplete="new-password"
            minlength="8"
            maxlength="72"
            required
            placeholder="请输入密码" /></label
        ><label
          ><span>确认密码</span
          ><input
            v-model="form.confirm"
            type="password"
            autocomplete="new-password"
            minlength="8"
            required
            placeholder="请再次输入密码"
        /></label>
      </div>
      <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
      <button class="btn register-complete" :disabled="busy">
        {{ busy ? '正在处理…' : '完成' }}
      </button>
    </form>
  </div>
</template>
