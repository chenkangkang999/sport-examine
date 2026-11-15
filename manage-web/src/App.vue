<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessageBox, ElMessage } from 'element-plus';
import { session, logout } from './api';
const route = useRoute(),
  router = useRouter();
const admin = computed(() => session.staff?.role === 'ADMIN');
const links = [
  { to: '/orders', label: '体检客户查询' },
  { to: '/statistics', label: '数据统计' },
  { to: '/catalog/hospitals', label: '医院信息' },
  { to: '/catalog/setmeals', label: '体检套餐' },
  { to: '/catalog/checkitems', label: '检查项目' },
  { to: '/associations', label: '医院套餐' },
  { to: '/schedules', label: '预约名额' },
  { to: '/catalog/doctors', label: '医生账号' },
];
async function signOut() {
  try {
    await ElMessageBox.confirm('退出当前管理端账号？用户端登录不受影响。', '退出登录', {
      type: 'info',
      confirmButtonText: '退出',
      cancelButtonText: '取消',
    });
    await logout();
    router.replace('/login');
    ElMessage.success('已退出管理端');
  } catch {}
}
</script>
<template>
  <RouterView v-if="route.meta.public" />
  <div v-else class="manual-workspace">
    <header class="manual-header">
      <RouterLink to="/orders" class="manual-brand">体检报告管理系统</RouterLink>
      <div class="manual-account">
        <span>{{ admin ? '管理员' : '医生' }}: {{ session.staff?.realName }}</span
        ><button @click="signOut">退出</button>
      </div>
    </header>
    <nav v-if="admin" class="manual-admin-menu" aria-label="管理员功能">
      <RouterLink v-for="link in links" :key="link.to" :to="link.to">{{ link.label }}</RouterLink>
    </nav>
    <main class="manual-main"><RouterView :key="route.path" /></main>
  </div>
</template>
