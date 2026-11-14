<script setup>
import Icon from './Icon.vue';
defineProps({
  open: Boolean,
  title: String,
  text: String,
  busy: Boolean,
  confirmText: { default: '确认操作' },
});
defineEmits(['close', 'confirm']);
</script>
<template>
  <Teleport to="body"
    ><div v-if="open" class="modal-backdrop" @click.self="!busy && $emit('close')">
      <section class="modal small" role="dialog" aria-modal="true" :aria-label="title">
        <button
          class="icon-button modal-close"
          aria-label="关闭"
          :disabled="busy"
          @click="$emit('close')"
        >
          <Icon name="close" />
        </button>
        <div class="dialog-symbol"><Icon name="warning" :size="28" /></div>
        <h2>{{ title }}</h2>
        <p class="muted dialog-text">{{ text }}</p>
        <div class="actions">
          <button class="btn secondary" :disabled="busy" @click="$emit('close')">再想一想</button
          ><button class="btn" :disabled="busy" @click="$emit('confirm')">
            {{ busy ? '处理中…' : confirmText }}
          </button>
        </div>
      </section>
    </div></Teleport
  >
</template>
