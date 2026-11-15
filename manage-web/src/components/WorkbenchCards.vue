<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { get } from '../api';
const props = defineProps({ hpId: [String, Number], active: String });
defineEmits(['select']);
const data = ref(null),
  loading = ref(false),
  error = ref('');
let request = 0;
const cards = computed(() => [
  { key: 'today', title: '今日体检', value: data.value?.todayCount, hint: '今天的有效预约' },
  { key: 'unstarted', title: '待录入', value: data.value?.pendingEntry, hint: '尚未保存检查结果' },
  {
    key: 'started',
    title: '录入中 / 待归档',
    value: data.value?.pendingArchive,
    hint: '已开始录入，需核对完整性',
  },
  { key: 'archived', title: '已归档', value: data.value?.archivedCount, hint: '已发布的体检报告' },
]);
async function load() {
  const id = ++request;
  loading.value = true;
  error.value = '';
  try {
    const result = await get('/workbench', props.hpId ? { hpId: props.hpId } : {}, { quiet: true });
    if (id === request) data.value = result;
  } catch (e) {
    if (id === request) {
      data.value = null;
      error.value = e.message;
    }
  } finally {
    if (id === request) loading.value = false;
  }
}
watch(() => props.hpId, load, { immediate: true });
onBeforeUnmount(() => {
  request++;
});
</script>
<template>
  <section class="workbench-overview" aria-label="医生待办工作区" :aria-busy="loading">
    <div class="workbench-overview-heading">
      <span
        >工作概览 <small v-if="data?.todayDate">{{ data.todayDate }}</small></span
      >
      <button type="button" :disabled="loading" @click="load">
        {{ loading ? '更新中…' : '刷新概览' }}
      </button>
    </div>
    <p v-if="error" class="workbench-error" role="alert">
      {{ error }} <button type="button" @click="load">重试</button>
    </p>
    <div class="workbench-cards">
      <button
        v-for="card in cards"
        :key="card.key"
        type="button"
        :class="{ active: active === card.key }"
        :aria-pressed="active === card.key"
        :disabled="loading || !data"
        @click="$emit('select', card.key, data.todayDate)"
      >
        <span>{{ card.title }}</span
        ><strong>{{ loading || !data ? '—' : (card.value ?? 0) }}</strong
        ><small>{{ card.hint }}</small>
      </button>
    </div>
    <p class="workbench-scope">
      按当前医院范围统计，点击卡片筛选预约；数量不随姓名、套餐等条件变化。
    </p>
  </section>
</template>
<style scoped>
.workbench-overview {
  margin-bottom: 16px;
  min-width: 0;
}
.workbench-overview-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 9px;
  font-size: 13px;
  color: #4b6078;
}
.workbench-overview-heading small {
  margin-left: 6px;
  font-size: 11px;
  color: #909399;
}
.workbench-overview-heading button,
.workbench-error button {
  padding: 3px 0;
  border: 0;
  background: none;
  color: #2374c3;
  font-size: 12px;
}
.workbench-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
.workbench-cards > button {
  min-width: 0;
  text-align: left;
  border: 1px solid #dbe4ed;
  border-radius: 6px;
  background: #fff;
  padding: 12px;
  color: #48627e;
}
.workbench-cards > button.active {
  border-color: #409eff;
  background: #f1f7ff;
  box-shadow: inset 0 2px #409eff;
}
.workbench-cards > button:disabled {
  cursor: default;
}
.workbench-cards span,
.workbench-cards strong,
.workbench-cards small {
  display: block;
}
.workbench-cards span {
  font-size: 12px;
}
.workbench-cards strong {
  margin: 6px 0 4px;
  font-size: 25px;
  line-height: 1.25;
  color: #256ca8;
  font-weight: 600;
}
.workbench-cards small,
.workbench-scope {
  color: #8b98a5;
  font-size: 10px;
  line-height: 1.6;
}
.workbench-scope {
  margin: 7px 0 0;
}
.workbench-error {
  margin: 0 0 8px;
  font-size: 12px;
  color: #b44a46;
}
@media (max-width: 1000px) {
  .workbench-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 430px) {
  .workbench-cards {
    gap: 7px;
  }
  .workbench-cards > button {
    padding: 10px;
  }
}
</style>
