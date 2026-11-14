<script setup>
import { computed } from 'vue';
import { dateText, orderState, money, localDate } from '../state';
import Icon from './Icon.vue';
const props = defineProps({ order: Object });
defineEmits(['cancel']);
const canCancel = computed(
  () =>
    props.order.state === 1 && !props.order.reportStarted && props.order.orderDate > localDate(),
);
</script>
<template>
  <article class="panel order-card">
    <div class="order-card-top">
      <span class="muted"
        >预约编号 <b>#{{ order.orderId }}</b></span
      ><span
        class="pill"
        :class="order.state === 3 ? 'neutral' : order.state === 2 ? 'blue-pill' : ''"
        >{{ orderState(order.state) }}</span
      >
    </div>
    <div class="order-card-main">
      <div class="order-date">
        <strong>{{ String(order.orderDate).slice(8, 10) }}</strong
        ><span>{{ String(order.orderDate).slice(0, 7).replace('-', '.') }}</span>
      </div>
      <div class="order-info">
        <h2>{{ order.setmealName }}</h2>
        <p><Icon name="hospital" :size="16" />{{ order.hospitalName }}</p>
        <p>
          <Icon name="user" :size="16" />{{ order.personName }} <span class="dot-separator">·</span>
          {{ dateText(order.orderDate) }}
        </p>
      </div>
      <div class="order-card-right">
        <strong class="order-amount">¥{{ money(order.amount) }}</strong>
      </div>
    </div>
    <div class="order-card-bottom">
      <small class="muted">{{
        order.state === 2
          ? '体检报告已归档，可查看完整结果。'
          : order.state === 3
            ? '预约已取消，名额已释放。'
            : order.reportStarted
              ? '医生正在处理检查结果，请耐心等待报告归档。'
              : '请按预约日期前往医院，并携带有效身份证件。'
      }}</small>
      <div class="actions">
        <button v-if="canCancel" class="text-button muted" @click="$emit('cancel', order)">
          取消预约</button
        ><RouterLink :to="`/orders/${order.orderId}`" class="btn secondary small-btn"
          >查看预约</RouterLink
        ><RouterLink v-if="order.state === 2" :to="`/report/${order.orderId}`" class="btn small-btn"
          >查看报告 <Icon name="arrow" :size="15"
        /></RouterLink>
      </div>
    </div>
  </article>
</template>
