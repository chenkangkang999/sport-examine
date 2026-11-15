<script setup>
import { state, setReadingSize } from '../state';
</script>
<template>
  <button
    type="button"
    class="reading-size-button"
    :aria-label="state.readingSize === 'large' ? '切换为标准字号' : '切换为大字模式'"
    :aria-pressed="state.readingSize === 'large'"
    :title="state.readingSize === 'large' ? '恢复标准字号' : '放大阅读字号'"
    @click="setReadingSize(state.readingSize === 'large' ? 'standard' : 'large')"
  >
    <span aria-hidden="true">A<span>a</span></span>
  </button>
</template>
<style scoped>
.reading-size-button {
  width: 32px;
  min-height: 32px;
  border: 1px solid #d9e7e8;
  border-radius: 7px;
  color: #277f88;
  background: #fff;
  font-family: Arial, sans-serif;
  line-height: 1;
  font-size: 17px;
  flex-shrink: 0;
}
.reading-size-button[aria-pressed='true'] {
  background: #eaf5f4;
  border-color: #80b6b8;
}
.reading-size-button > span > span {
  font-size: 12px;
}
</style>
