<script setup>
defineProps({
  step: { type: Number, default: 0 },
  busy: Boolean,
  summary: { type: Array, default: () => [] },
});
defineEmits(['back']);
const labels = ['体检人', '医院', '套餐', '日期', '确认'];
</script>
<template>
  <nav class="booking-progress" aria-label="预约步骤">
    <div class="progress-caption">
      <span>预约流程</span><small>第 {{ step + 1 }} 步 / 共 5 步</small>
    </div>
    <ol>
      <li
        v-for="(label, index) in labels"
        :key="label"
        :class="{ completed: index < step, current: index === step }"
      >
        <button
          type="button"
          :disabled="busy || index >= step"
          :aria-current="index === step ? 'step' : undefined"
          :aria-label="
            index < step
              ? `返回${label}步骤`
              : `${label}，${index === step ? '当前步骤' : '尚未进行'}`
          "
          @click="$emit('back', index)"
        >
          <span class="progress-number" aria-hidden="true">{{
            index < step ? '✓' : index + 1
          }}</span
          ><span>{{ label }}</span>
        </button>
      </li>
    </ol>
    <p v-if="summary.length" class="progress-summary">{{ summary.join(' · ') }}</p>
  </nav>
</template>
<style scoped>
.booking-progress {
  padding: 13px 5% 12px;
  background: #fff;
  border-bottom: 1px solid #e8efef;
}
.progress-caption {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: calc(12px * var(--reading-scale, 1));
  color: #52777d;
}
.progress-caption > span {
  font-weight: 600;
}
.progress-caption small {
  font-size: calc(11px * var(--reading-scale, 1));
  color: #8b9b9d;
}
.booking-progress ol {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  list-style: none;
  margin: 0;
  padding: 0;
}
.booking-progress li {
  position: relative;
  min-width: 0;
}
.booking-progress li:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 11px;
  left: calc(50% + 17px);
  width: calc(100% - 34px);
  height: 1px;
  background: #e0e9e9;
}
.booking-progress li.completed::after {
  background: #9fc9c9;
}
.booking-progress button {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  padding: 0;
  color: #94a3a5;
  font-size: calc(11px * var(--reading-scale, 1));
  line-height: 1.5;
  border: 0;
  background: transparent;
}
.booking-progress button:disabled {
  opacity: 1;
  cursor: default;
}
.progress-number {
  width: 23px;
  height: 23px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f1f5f5;
  border: 1px solid #dfe8e8;
  font-size: calc(11px * var(--reading-scale, 1));
  line-height: 1;
}
.completed button {
  color: #37828b;
}
.completed .progress-number {
  background: #edf8f6;
  border-color: #b7d9d6;
}
.current button {
  color: #137e92;
  font-weight: 600;
}
.current .progress-number {
  background: #137e92;
  border-color: #137e92;
  color: #fff;
  box-shadow: 0 0 0 3px #edf6f7;
}
.progress-summary {
  margin: 10px 0 0;
  font-size: calc(10px * var(--reading-scale, 1));
  color: #819295;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
</style>
