<script setup>
import { onMounted, ref, watch } from 'vue';
import { api, post } from '../api';
import { notify, dateText, localDate, orderState } from '../state';
import Icon from '../components/Icon.vue';
import AsyncState from '../components/AsyncState.vue';
import ConfirmDialog from '../components/ConfirmDialog.vue';
const loading = ref(true),
  error = ref(''),
  orders = ref([]),
  persons = ref([]),
  status = ref(''),
  personId = ref(''),
  cancel = ref(null),
  busy = ref(false);
async function load() {
  loading.value = true;
  error.value = '';
  try {
    orders.value = await api.orders({
      state: status.value || undefined,
      personId: personId.value || undefined,
    });
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
async function confirmCancel() {
  busy.value = true;
  try {
    await post('/orders/removeOrders', { orderId: cancel.value.orderId });
    cancel.value = null;
    notify('预约已取消', 'success');
    await load();
  } catch (e) {
    notify(e.message);
  } finally {
    busy.value = false;
  }
}
watch([status, personId], load);
onMounted(async () => {
  await load();
  try {
    persons.value = await api.persons();
  } catch (e) {
    notify(e.message);
  }
});
</script>
<template>
  <section class="appointments-page">
    <div class="appointment-filters">
      <label
        >状态<select v-model="status" aria-label="预约状态">
          <option value="">全部预约</option>
          <option :value="1">未归档</option>
          <option :value="2">已归档</option>
          <option :value="3">已取消</option>
        </select></label
      ><label
        >体检人<select v-model="personId" aria-label="体检人">
          <option value="">全部家人</option>
          <option v-for="p in persons" :key="p.personId" :value="p.personId">
            {{ p.realName }}
          </option>
        </select></label
      >
    </div>
    <AsyncState
      :loading="loading"
      :error="error"
      :empty="!orders.length"
      message="暂无预约记录"
      @retry="load"
      ><template #hint>您可以为自己或家人预约体检。</template
      ><template #action
        ><RouterLink to="/appointment" class="btn">去预约体检</RouterLink></template
      >
      <ul class="appointment-list">
        <li v-for="order in orders" :key="order.orderId" class="order-card">
          <RouterLink :to="`/orders/${order.orderId}`" class="order-info"
            ><small>{{ dateText(order.orderDate) }}</small
            ><strong>{{ order.setmealName }}</strong
            ><span>{{ order.personName }} · {{ order.hospitalName }}</span></RouterLink
          >
          <div class="order-actions">
            <button
              v-if="order.state === 1 && !order.reportStarted && order.orderDate > localDate()"
              class="cancel-appointment"
              @click="cancel = order"
            >
              取消预约</button
            ><RouterLink
              v-else-if="order.state === 2"
              :to="`/report/${order.orderId}`"
              class="view-report"
              >查看报告</RouterLink
            ><span v-else class="order-status">{{ orderState(order.state) }}</span>
          </div>
        </li>
      </ul>
    </AsyncState>
    <RouterLink to="/appointment" class="new-appointment">继续预约 ›</RouterLink>
  </section>
  <ConfirmDialog
    :open="Boolean(cancel)"
    title="确认取消这次预约？"
    :text="`将取消${cancel?.personName || ''}的本次体检预约，释放已占名额，历史订单仍然保留。`"
    confirm-text="确认取消预约"
    :busy="busy"
    @close="cancel = null"
    @confirm="confirmCancel"
  />
</template>
<style scoped>
.appointments-page {
  padding: 0 7%;
  min-height: calc(100dvh - 124px);
  color: #555;
}
.appointment-filters {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 15px 0 5px;
  font-size: 11px;
  color: #999;
}
.appointment-filters label {
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
}
.appointment-filters select {
  font-size: 11px;
  max-width: 112px;
  border: 1px solid #e7e7e7;
  border-radius: 2px;
  padding: 3px 5px;
  background: #fff;
  color: #777;
  min-height: 28px;
}
.appointment-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.order-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 77px;
  border: 0;
  border-bottom: 1px solid #eee;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  padding: 12px 0;
  margin: 0;
}
.order-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.6;
  flex: 1;
}
.order-info > small {
  font-size: 12px;
  color: #777;
}
.order-info > strong {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  overflow-wrap: anywhere;
}
.order-info > span {
  font-size: 10px;
  color: #aaa;
  line-height: 1.5;
  margin-top: 2px;
}
.order-actions {
  flex-shrink: 0;
  font-size: 13px;
  max-width: 83px;
  text-align: right;
}
.cancel-appointment {
  padding: 7px 0;
  color: #e6a23c;
  font-size: 13px;
}
.view-report {
  color: #137e92;
}
.order-status {
  font-size: 11px;
  color: #999;
}
.new-appointment {
  display: block;
  text-align: center;
  margin: 25px 0;
  font-size: 12px;
  color: #137e92;
}
</style>
