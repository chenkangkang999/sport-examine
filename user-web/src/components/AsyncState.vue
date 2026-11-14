<script setup>
import Icon from './Icon.vue';
defineProps({
  loading: Boolean,
  error: String,
  empty: Boolean,
  message: { default: '暂时还没有记录' },
});
defineEmits(['retry']);
</script>
<template>
  <div v-if="loading" class="async-state" role="status">
    <span class="spinner"></span>
    <p>正在为您加载…</p>
  </div>
  <div v-else-if="error" class="async-state">
    <Icon name="warning" :size="36" />
    <h3>暂时无法加载</h3>
    <p>{{ error }}</p>
    <button class="btn secondary" @click="$emit('retry')">重新加载</button>
  </div>
  <div v-else-if="empty" class="async-state">
    <Icon name="leaf" :size="42" />
    <h3>{{ message }}</h3>
    <p><slot name="hint">您的健康记录会在这里妥善保存。</slot></p>
    <slot name="action" />
  </div>
  <slot v-else />
</template>
