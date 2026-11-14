<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { state, notify } from './state';
import Icon from './components/Icon.vue';
import FaIcon from './components/FaIcon.vue';
const route = useRoute(),
  router = useRouter();
const login = computed(() => route.path === '/login'),
  register = computed(() => route.path === '/register'),
  home = computed(() => route.path === '/index');
const title = computed(
  () =>
    ({
      '/register': '注册',
      '/appointment': '用户体检预约',
      '/hospital': '请选择体检机构',
      '/setmeal': '选择体检套餐',
      '/selectdate': '选择体检日期',
      '/confirmorder': '确认你的订单',
      '/appointmentlist': '我的预约',
      '/reportlist': '健康档案',
      '/personal': '个人中心',
      '/persons': '家人健康档案',
    })[route.path] ||
    (route.path.startsWith('/appointmentsuccess')
      ? '预约成功'
      : route.path.startsWith('/report/')
        ? '体检报告'
        : route.path.startsWith('/orders/')
          ? '预约详情'
          : route.meta.title),
);
function back() {
  if (window.history.state?.back) router.back();
  else router.push(register.value ? '/login' : '/index');
}
function explain(name) {
  notify(`${name}是原型保留入口，本课程项目暂未接入该项外部服务。`, 'info');
}
</script>
<template>
  <div :class="['app-shell', { 'login-shell': login, 'register-shell': register }]">
    <header v-if="!login" :class="['prototype-header', { 'home-header': home }]">
      <template v-if="home"
        ><button class="home-hospital" @click="notify('在体检预约中可选择体检医院。', 'info')">
          <Icon class="cloud-hospital-mark" name="cloudHospital" :size="26" /><span>云医院</span>
          <FaIcon name="down" /></button
        ><button
          class="header-message"
          aria-label="消息"
          @click="notify('暂无新的系统消息。', 'info')"
        >
          <FaIcon name="envelope" /></button
      ></template>
      <template v-else
        ><button class="header-back" aria-label="返回" @click="back"><FaIcon name="back" /></button>
        <h1>{{ title }}</h1>
        <span class="header-spacer"></span
      ></template>
    </header>
    <main :class="login ? 'login-main' : 'main-content'"><RouterView /></main>
    <nav v-if="!login && !register" class="prototype-footer" aria-label="底部导航">
      <RouterLink to="/index" :class="{ active: home }"
        ><FaIcon name="home" /><span>云医院</span></RouterLink
      >
      <button @click="explain('商城')"><FaIcon name="cart" /><span>商城</span></button>
      <button @click="explain('发现')"><FaIcon name="compass" /><span>发现</span></button>
      <RouterLink to="/personal" :class="{ active: ['/personal', '/persons'].includes(route.path) }"
        ><FaIcon name="user" /><span>我</span></RouterLink
      >
    </nav>
  </div>
  <div class="toast-stack" role="status" aria-live="polite">
    <div v-for="notice in state.notices" :key="notice.id" :class="['toast', notice.kind]">
      <Icon :name="notice.kind === 'success' ? 'check' : 'warning'" :size="18" /><span>{{
        notice.message
      }}</span
      ><button
        class="icon-button"
        aria-label="关闭提示"
        @click="state.notices = state.notices.filter((n) => n.id !== notice.id)"
      >
        <Icon name="close" :size="16" />
      </button>
    </div>
  </div>
</template>
