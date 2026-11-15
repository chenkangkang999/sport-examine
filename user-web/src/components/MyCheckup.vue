<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { get } from '../api';
import { dateText, orderState, state } from '../state';
import Icon from './Icon.vue';
const info = ref(null),
  error = ref(''),
  loading = ref(true);
let request = 0;
async function load() {
  const id = ++request;
  const owner = state.user?.userId;
  loading.value = true;
  error.value = '';
  try {
    const data = await get('/dashboard');
    if (id !== request || state.user?.userId !== owner) return;
    info.value = data;
    state.unreadCount = Number(info.value.unreadCount || 0);
  } catch (e) {
    if (id === request) error.value = e.message;
  } finally {
    if (id === request) loading.value = false;
  }
}
onMounted(load);
onBeforeUnmount(() => request++);
</script>
<template>
  <section class="my-checkup" aria-label="我的体检概览">
    <header>
      <h2><Icon name="calendar" :size="18" />我的体检</h2>
      <RouterLink to="/appointmentlist">全部预约 ›</RouterLink>
    </header>
    <p v-if="loading" class="checkup-note" role="status">正在读取体检安排…</p>
    <div v-else-if="error" class="checkup-note" role="alert">
      {{ error }} <button @click="load">重新加载</button>
    </div>
    <template v-else-if="info">
      <div v-if="info.appointment" class="checkup-row">
        <div>
          <small
            >{{ Number(info.appointment.state) === 1 ? '当前预约' : '最近预约' }} ·
            {{ orderState(info.appointment.state) }}</small
          ><strong
            >{{ dateText(info.appointment.orderDate)
            }}<span>{{ info.appointment.personName }}</span></strong
          >
          <p>{{ info.appointment.hospitalName }}</p>
        </div>
        <RouterLink :to="`/orders/${info.appointment.orderId}`" class="checkup-link"
          >查看预约</RouterLink
        >
      </div>
      <div v-else class="checkup-row">
        <div>
          <strong>还没有体检预约</strong>
          <p>为自己或家人安排一次体检</p>
        </div>
        <RouterLink to="/appointment" class="checkup-link">去预约</RouterLink>
      </div>
      <div v-if="info.latestReport" class="checkup-row report-row">
        <div>
          <small>最新报告</small
          ><strong
            >{{ info.latestReport.personName
            }}<span>{{ dateText(info.latestReport.orderDate) }}</span></strong
          >
          <p>报告已归档，可查看完整结果</p>
        </div>
        <RouterLink :to="`/report/${info.latestReport.orderId}`" class="checkup-link"
          >查看报告</RouterLink
        >
      </div>
      <div v-else class="checkup-note">报告归档后，最新结果会显示在这里。</div>
      <footer>
        <RouterLink to="/trends">历次指标趋势 ›</RouterLink
        ><RouterLink to="/messages"
          >{{ state.unreadCount ? `${state.unreadCount} 条未读消息` : '消息中心' }} ›</RouterLink
        >
      </footer>
    </template>
  </section>
</template>
<style scoped>
.my-checkup {
  margin: 12px 14px;
  padding: 14px;
  border: 1px solid #dfebea;
  border-radius: 10px;
  background: #fff;
}
.my-checkup header,
.my-checkup h2,
.my-checkup footer,
.checkup-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.my-checkup h2 {
  justify-content: flex-start;
  font-size: calc(16px * var(--reading-scale, 1));
  color: #306773;
  font-weight: 600;
}
.my-checkup header a,
.my-checkup footer a {
  color: #548891;
  font-size: calc(11px * var(--reading-scale, 1));
}
.checkup-row {
  padding: 14px 0;
}
.checkup-row > div {
  flex: 1;
  min-width: 0;
}
.checkup-row small {
  display: block;
  color: #819296;
  font-size: calc(10px * var(--reading-scale, 1));
  line-height: 1.7;
}
.checkup-row strong {
  display: block;
  font-weight: 500;
  font-size: calc(15px * var(--reading-scale, 1));
  margin: 3px 0;
  overflow-wrap: anywhere;
}
.checkup-row strong span {
  font-size: calc(11px * var(--reading-scale, 1));
  margin-left: 8px;
  color: #657f83;
}
.checkup-row p,
.checkup-note {
  color: #839498;
  font-size: calc(11px * var(--reading-scale, 1));
  line-height: 1.8;
}
.checkup-link {
  font-size: calc(12px * var(--reading-scale, 1));
  color: #137e92;
  padding: 7px 9px;
  border: 1px solid #bed9db;
  border-radius: 6px;
  flex-shrink: 0;
}
.report-row,
.my-checkup footer {
  border-top: 1px solid #edf2f2;
}
.my-checkup footer {
  padding-top: 10px;
}
.checkup-note {
  padding: 12px 0;
}
.checkup-note button {
  color: #137e92;
  text-decoration: underline;
}
</style>
