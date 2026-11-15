<script setup>
import { onBeforeUnmount, ref, watch } from 'vue';
import { get } from '../api';
import Icon from './Icon.vue';
const props = defineProps({ order: { type: Object, required: true } });
const guide = ref(null),
  error = ref(''),
  loading = ref(false);
let request = 0;
async function load() {
  const id = ++request;
  loading.value = true;
  error.value = '';
  guide.value = null;
  try {
    const value = await get('/orders/guidance', { orderId: props.order.orderId });
    if (id === request) guide.value = value;
  } catch (e) {
    if (id === request) error.value = e.message;
  } finally {
    if (id === request) loading.value = false;
  }
}
const lines = (text) =>
  String(text || '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
watch(() => props.order.orderId, load, { immediate: true });
onBeforeUnmount(() => request++);
</script>
<template>
  <section class="checkup-guide" aria-label="体检前指南">
    <h3><Icon name="report" :size="17" />体检前指南</h3>
    <p v-if="loading" class="guide-note" role="status">正在读取机构须知…</p>
    <p v-else-if="error" class="guide-note" role="alert">
      {{ error }} <button @click="load">重试</button>
    </p>
    <template v-else-if="guide">
      <h4>准备事项</h4>
      <ul>
        <li v-for="line in lines(guide.preparation)" :key="line">{{ line }}</li>
      </ul>
      <h4>到院流程</h4>
      <ol>
        <li v-for="line in lines(guide.arrivalSteps)" :key="line">{{ line }}</li>
      </ol>
      <p class="guide-note">具体检查准备以体检机构提供的信息为准。</p>
      <a
        v-if="order.hospitalTelephone"
        :href="`tel:${order.hospitalTelephone}`"
        class="guide-contact"
        ><Icon name="phone" :size="14" />联系机构：{{ order.hospitalTelephone }}</a
      >
    </template>
  </section>
</template>
<style scoped>
.checkup-guide {
  margin: 16px 0;
  padding: 14px;
  border: 1px solid #e1ebea;
  border-radius: 9px;
  background: #fff;
  text-align: left;
}
.checkup-guide h3 {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: calc(15px * var(--reading-scale, 1));
  color: #37757f;
  margin: 0 0 12px;
  font-weight: 600;
}
.checkup-guide h4 {
  font-size: calc(12px * var(--reading-scale, 1));
  color: #527278;
  margin: 10px 0 6px;
  font-weight: 600;
}
.checkup-guide ul,
.checkup-guide ol {
  padding-left: 18px;
  font-size: calc(12px * var(--reading-scale, 1));
  line-height: 1.9;
  color: #667b80;
}
.checkup-guide ul {
  list-style: disc;
}
.checkup-guide ol {
  list-style: decimal;
}
.checkup-guide li {
  padding-left: 2px;
  margin: 3px 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.guide-note {
  color: #8a9b9e;
  font-size: calc(11px * var(--reading-scale, 1));
  line-height: 1.8;
  margin-top: 10px;
}
.guide-note button,
.guide-contact {
  color: #137e92;
}
.guide-contact {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: calc(12px * var(--reading-scale, 1));
  margin-top: 10px;
}
</style>
