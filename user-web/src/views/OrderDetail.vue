<script setup>
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { api, post } from '../api';
import { dateText, orderState, money, localDate, notify, sexText } from '../state';
import Icon from '../components/Icon.vue';
import AsyncState from '../components/AsyncState.vue';
import ConfirmDialog from '../components/ConfirmDialog.vue';
import CheckupGuide from '../components/CheckupGuide.vue';
const route = useRoute(),
  order = ref(null),
  loading = ref(true),
  error = ref(''),
  cancel = ref(false),
  busy = ref(false);
const canCancel = computed(
  () =>
    order.value?.state === 1 && !order.value.reportStarted && order.value.orderDate > localDate(),
);
async function load() {
  loading.value = true;
  error.value = '';
  try {
    order.value = await api.order(route.params.orderId);
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
async function confirmCancel() {
  busy.value = true;
  try {
    await post('/orders/removeOrders', { orderId: order.value.orderId });
    cancel.value = false;
    notify('预约已取消', 'success');
    await load();
  } catch (e) {
    notify(e.message);
  } finally {
    busy.value = false;
  }
}
watch(() => route.params.orderId, load, { immediate: true });
</script>
<template>
  <AsyncState :loading="loading" :error="error" @retry="load">
    <template v-if="order">
      <section v-if="route.meta.success && order.state === 1" class="appointment-success">
        <div class="success-main">
          <div class="success-color">
            <span class="success-check"><Icon name="check" :size="39" /></span>
          </div>
          <h1>恭喜预约成功！</h1>
          <p>请体检用户携带本人身份证到店认证</p>
        </div>
        <RouterLink :to="`/orders/${order.orderId}`" class="success-order">查看订单</RouterLink>
        <RouterLink to="/appointment?family=1" class="success-continue">继续为家人预约</RouterLink>
        <div class="success-info">
          <p>您的信息已经发送至体检机构</p>
          <p>{{ order.hospitalName }}</p>
          <p>{{ dateText(order.orderDate) }} · {{ order.personName }}</p>
          <p>
            客服电话 <a :href="`tel:${order.hospitalTelephone}`">{{ order.hospitalTelephone }}</a>
          </p>
        </div>
        <div class="success-guide"><CheckupGuide :order="order" /></div>
      </section>
      <section v-else class="order-detail">
        <p class="order-number">
          预约编号 {{ order.orderId }}<span>{{ orderState(order.state) }}</span>
        </p>
        <div class="detail-section">
          <h3>体检人信息</h3>
          <dl class="detail-list">
            <div>
              <dt>姓名：</dt>
              <dd>{{ order.personName }}</dd>
            </div>
            <div>
              <dt>证件号码：</dt>
              <dd>{{ order.personIdentityMasked }}</dd>
            </div>
            <div>
              <dt>出生日期：</dt>
              <dd>{{ dateText(order.personBirthday) }}</dd>
            </div>
            <div>
              <dt>性别：</dt>
              <dd>{{ sexText(order.personSex) }}</dd>
            </div>
            <div>
              <dt>手机号码：</dt>
              <dd>{{ order.userId }}</dd>
            </div>
          </dl>
        </div>
        <div class="detail-section">
          <h3>体检日期</h3>
          <p>{{ dateText(order.orderDate) }}</p>
        </div>
        <div class="detail-section">
          <h3>体检机构</h3>
          <p>{{ order.hospitalName }}</p>
          <dl class="detail-list">
            <div>
              <dt>营业时间：</dt>
              <dd>{{ order.businessHours }}</dd>
            </div>
            <div>
              <dt>采血截止：</dt>
              <dd>{{ order.deadline || '请咨询医院' }}</dd>
            </div>
            <div>
              <dt>机构电话：</dt>
              <dd>
                <a :href="`tel:${order.hospitalTelephone}`">{{ order.hospitalTelephone }}</a>
              </dd>
            </div>
            <div>
              <dt>机构地址：</dt>
              <dd>{{ order.hospitalAddress }}</dd>
            </div>
          </dl>
        </div>
        <div class="detail-section">
          <h3>套餐类型</h3>
          <p>{{ order.setmealName }}</p>
        </div>
        <div class="detail-section">
          <h3>订单金额</h3>
          <p class="order-amount">¥{{ money(order.amount) }}</p>
        </div>
        <CheckupGuide :order="order" />
        <div class="detail-actions">
          <RouterLink v-if="order.state === 2" :to="`/report/${order.orderId}`" class="btn"
            >查看体检报告</RouterLink
          ><RouterLink to="/appointmentlist" class="btn secondary">查看全部预约</RouterLink
          ><button v-if="canCancel" class="cancel-detail" @click="cancel = true">
            取消本次预约
          </button>
        </div>
        <p v-if="order.state === 1 && !canCancel" class="detail-note">
          预约当日或已开始录入报告的订单不可取消。
        </p>
        <p class="detail-note">
          请按预约日期携带有效身份证件到院，体检准备事项可致电体检机构咨询。
        </p>
      </section>
    </template>
  </AsyncState>
  <ConfirmDialog
    :open="cancel"
    title="确认取消本次预约？"
    text="取消后将释放已占名额，历史订单仍然保留。"
    confirm-text="确认取消预约"
    :busy="busy"
    @close="cancel = false"
    @confirm="confirmCancel"
  />
</template>
<style scoped>
.success-guide {
  margin: 18px 7% 24px;
}
.appointment-success {
  min-height: calc(100dvh - 124px);
  color: #555;
}
.success-main {
  background: #fff;
  text-align: center;
  border-bottom: 1px solid #eee;
  padding-bottom: 29px;
}
.success-color {
  height: 140px;
  position: relative;
  background: linear-gradient(110deg, #01c7a4, #02a6c9);
}
.success-check {
  position: absolute;
  left: 50%;
  bottom: -31px;
  transform: translateX(-50%);
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #fff;
  color: #02a6c9;
}
.success-main h1 {
  font-size: calc(20px * var(--reading-scale, 1));
  font-weight: 400;
  line-height: 1.6;
  color: #02a6c9;
  margin: 45px 0 10px;
}
.success-main > p {
  font-size: calc(12px * var(--reading-scale, 1));
  color: #777;
  margin: 0;
}
.success-order {
  display: block;
  background: #fff;
  text-align: center;
  line-height: 54px;
  font-size: calc(14px * var(--reading-scale, 1));
  color: #02a6c9;
}
.success-continue {
  display: block;
  width: 88%;
  margin: 23px auto 0;
  min-height: 48px;
  line-height: 48px;
  text-align: center;
  color: #fff;
  background: linear-gradient(110deg, #01c7b0, #02a6c9);
  font-size: calc(14px * var(--reading-scale, 1));
  border-radius: 5px;
}
.success-info {
  text-align: center;
  margin: 63px 7% 22px;
  font-size: calc(11px * var(--reading-scale, 1));
  color: #999;
  line-height: 1.8;
}
.success-info p {
  margin: 3px 0;
}
.success-info a {
  font-size: calc(12px * var(--reading-scale, 1));
  color: #777;
  margin-left: 7px;
}
.order-detail {
  padding: 9px 7% 20px;
  color: #555;
}
.order-number {
  display: flex;
  justify-content: space-between;
  gap: 9px;
  font-size: calc(11px * var(--reading-scale, 1));
  color: #999;
  margin: 7px 0 0;
}
.order-number span {
  color: #137e92;
}
.detail-section h3 {
  min-height: 49px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid #eee;
  font-size: calc(15px * var(--reading-scale, 1));
  font-weight: 600;
  margin: 0 0 9px;
}
.detail-section h3:before {
  content: '';
  display: block;
  width: 2px;
  height: 14px;
  background: #127a90;
}
.detail-section > .detail-list {
  display: block;
  padding: 0;
  margin: 0 0 5px;
  font-size: calc(12px * var(--reading-scale, 1));
}
.detail-section .detail-list > div {
  display: grid;
  grid-template-columns: 85px minmax(0, 1fr);
  gap: 0;
  min-height: 30px;
  padding: 3px 0;
  border: 0;
}
.detail-section dt,
.detail-section dd {
  margin: 0;
  color: #555;
  line-height: 1.8;
  overflow-wrap: anywhere;
  font-weight: 400;
}
.detail-section > p {
  font-size: calc(13px * var(--reading-scale, 1));
  line-height: 1.8;
  margin: 11px 0 14px;
}
.detail-section .order-amount {
  font-size: calc(18px * var(--reading-scale, 1));
  color: #f77b2d;
}
.order-amount small {
  font-size: calc(11px * var(--reading-scale, 1));
  color: #aaa;
  margin-left: 12px;
}
.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-top: 22px;
}
.detail-actions .btn {
  font-size: calc(13px * var(--reading-scale, 1));
  min-height: 38px;
  padding: 7px 12px;
}
.cancel-detail {
  font-size: calc(12px * var(--reading-scale, 1));
  color: #e6a23c;
  padding: 7px 0;
}
.detail-note {
  font-size: calc(11px * var(--reading-scale, 1));
  color: #999;
  line-height: 1.8;
  margin: 17px 0 4px;
}
</style>
