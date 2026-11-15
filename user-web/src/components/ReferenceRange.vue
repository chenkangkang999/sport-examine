<script setup>
import { computed } from 'vue';
import { numericReference, numericValue, formatReference } from '../utils/reportMetrics';

const props = defineProps({ detail: { type: Object, required: true } });
const graphic = computed(() => {
  const value = numericValue(props.detail.value);
  const reference = numericReference(props.detail);
  if (value == null || !reference) return null;
  const anchors =
    reference.type === 'target'
      ? [reference.target]
      : [reference.lower, reference.upper].filter((v) => v != null);
  const minimum = Math.min(value, ...anchors),
    maximum = Math.max(value, ...anchors);
  const span = maximum - minimum || Math.max(Math.abs(maximum) * 0.2, 1);
  const start = minimum - span * 0.2,
    end = maximum + span * 0.2;
  const position = (number) => ((number - start) / (end - start)) * 100;
  return {
    marker: position(value),
    target: reference.type === 'target' ? position(reference.target) : null,
    left:
      reference.type === 'range' ? (reference.lower == null ? 0 : position(reference.lower)) : 0,
    right:
      reference.type === 'range' ? (reference.upper == null ? 100 : position(reference.upper)) : 0,
    label: `数值 ${props.detail.value} ${props.detail.unit || ''}，参考${formatReference(props.detail)}，${Number(props.detail.isError) === 1 ? '已标记异常' : '未标记异常'}`,
  };
});
</script>
<template>
  <div v-if="graphic" class="metric-reference" :class="{ abnormal: Number(detail.isError) === 1 }">
    <div class="reference-track" role="img" :aria-label="graphic.label">
      <span
        v-if="graphic.target == null"
        class="reference-band"
        :style="{ left: graphic.left + '%', width: graphic.right - graphic.left + '%' }"
      ></span>
      <span v-else class="reference-target" :style="{ left: graphic.target + '%' }"></span>
      <span class="reference-marker" :style="{ left: graphic.marker + '%' }"></span>
    </div>
    <p>
      {{ graphic.target == null ? '浅色区为参考范围' : '虚线为参考值'
      }}<span>{{ Number(detail.isError) === 1 ? '异常' : '当前数值位置' }}</span>
    </p>
  </div>
</template>
<style scoped>
.metric-reference {
  margin-top: 9px;
  width: 100%;
}
.reference-track {
  position: relative;
  height: 8px;
  border-radius: 4px;
  background: #edf1f1;
}
.reference-band {
  position: absolute;
  inset-block: 0;
  background: #cce5dd;
  border-radius: 4px;
}
.reference-target {
  position: absolute;
  top: -4px;
  height: 16px;
  border-left: 2px dashed #6e9d94;
}
.reference-marker {
  position: absolute;
  top: -3px;
  height: 14px;
  width: 5px;
  border: 1px solid #fff;
  border-radius: 3px;
  transform: translateX(-50%);
  background: #157c8a;
  box-shadow: 0 0 0 1px #157c8a;
}
.abnormal .reference-marker {
  background: #ba634e;
  box-shadow: 0 0 0 1px #ba634e;
}
.metric-reference p {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 3px 8px;
  margin: 5px 0 0;
  font-size: calc(9px * var(--reading-scale, 1));
  line-height: 1.6;
  color: #869392;
}
.metric-reference p span {
  color: #617f80;
}
.abnormal p span {
  color: #a65a45;
}
@media print {
  .metric-reference {
    display: none;
  }
}
</style>
