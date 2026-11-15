<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { api } from '../api';
import { money } from '../state';
import Icon from './Icon.vue';
const props = defineProps({
  open: Boolean,
  hpId: [Number, String],
  meals: { type: Array, default: () => [] },
});
const emit = defineEmits(['close', 'choose']);
const dialog = ref(null),
  closeButton = ref(null),
  loading = ref(false),
  records = ref({}),
  errors = ref({});
let request = 0,
  previousFocus,
  previousOverflow = '',
  scrollLocked = false;
const columns = computed(() =>
  props.meals.map((meal) => ({
    original: meal,
    detail: records.value[meal.smId],
    error: errors.value[meal.smId],
  })),
);
const allReady = computed(
  () =>
    !loading.value && columns.value.length >= 2 && columns.value.every((column) => column.detail),
);
const rows = computed(() => {
  const groups = new Map();
  for (const column of columns.value)
    for (const item of column.detail?.items || [])
      groups.set(String(item.ciId), { key: String(item.ciId), name: item.ciName });
  return [...groups.values()].map((row) => {
    const items = columns.value.map((column) =>
      column.detail?.items?.find((item) => String(item.ciId) === row.key),
    );
    const signatures = items.map((item) =>
      item
        ? (item.details || [])
            .map((detail) => `${detail.cdId}:${detail.name}`)
            .sort()
            .join('|') || 'included'
        : 'absent',
    );
    return { ...row, items, different: allReady.value && new Set(signatures).size > 1 };
  });
});
const differenceCount = computed(() => rows.value.filter((row) => row.different).length);
const gender = (type) =>
  Number(type) === 2 ? '不限性别' : Number(type) === 1 ? '男士适用' : '女士适用';
async function load() {
  const current = ++request;
  if (!props.open || !props.hpId || props.meals.length < 2) return;
  loading.value = true;
  records.value = {};
  errors.value = {};
  const hospital = props.hpId;
  // 对比使用当前医院的详情接口，价格和检查内容不在前端硬编码；失败的列不参与差异结论。
  const results = await Promise.all(
    props.meals.map(async (meal) => {
      try {
        return { id: meal.smId, value: await api.meal({ hpId: hospital, smId: meal.smId }) };
      } catch (error) {
        return { id: meal.smId, error: error.message };
      }
    }),
  );
  if (current !== request || !props.open) return;
  results.forEach((result) => {
    if (result.value) records.value[result.id] = result.value;
    else errors.value[result.id] = result.error;
  });
  loading.value = false;
  // 重试按钮在成功后会被预约按钮替换，避免焦点随旧按钮移除而留在页面背景。
  await nextTick();
  if (current === request && props.open && document.activeElement === document.body)
    closeButton.value?.focus({ preventScroll: true });
}
function releaseScroll() {
  document.removeEventListener('keydown', onKeydown);
  if (scrollLocked) document.body.style.overflow = previousOverflow;
  scrollLocked = false;
}
function close() {
  emit('close');
}
function choose(column) {
  if (!column.detail || loading.value) return;
  emit('close');
  emit('choose', column.detail);
}
function onKeydown(event) {
  if (!props.open || !dialog.value) return;
  if (event.key === 'Escape') {
    event.preventDefault();
    close();
    return;
  }
  if (event.key !== 'Tab') return;
  const focusable = [
    ...dialog.value.querySelectorAll('button:not(:disabled), [tabindex="0"]'),
  ].filter((element) => element.getClientRects().length);
  const first = focusable[0],
    last = focusable[focusable.length - 1];
  if (
    event.shiftKey &&
    (document.activeElement === first || !dialog.value.contains(document.activeElement))
  ) {
    event.preventDefault();
    last?.focus();
  } else if (
    !event.shiftKey &&
    (document.activeElement === last || !dialog.value.contains(document.activeElement))
  ) {
    event.preventDefault();
    first?.focus();
  }
}
watch(() => [props.open, props.hpId, props.meals.map((meal) => meal.smId).join(',')], load, {
  immediate: true,
});
watch(
  () => props.open,
  async (open) => {
    if (open) {
      previousFocus = document.activeElement;
      previousOverflow = document.body.style.overflow;
      document.body.style.overflow = 'hidden';
      scrollLocked = true;
      // 在 document 监听，以便异步内容替换导致焦点暂时落到 body 时 Escape 仍有效。
      document.addEventListener('keydown', onKeydown);
      await nextTick();
      if (props.open) closeButton.value?.focus();
    } else {
      releaseScroll();
      if (previousFocus?.isConnected) previousFocus.focus();
    }
  },
  { flush: 'post', immediate: true },
);
onBeforeUnmount(() => {
  request++;
  releaseScroll();
});
</script>
<template>
  <Teleport to="body">
    <div v-if="open" class="comparison-backdrop" @click.self="close">
      <section
        ref="dialog"
        class="comparison-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="comparison-title"
      >
        <header class="comparison-header">
          <div>
            <h2 id="comparison-title">体检套餐对比</h2>
            <p>查看价格、适用人群和检查内容后再选择</p>
          </div>
          <button
            ref="closeButton"
            type="button"
            class="comparison-close"
            aria-label="关闭套餐对比"
            @click="close"
          >
            <Icon name="close" :size="21" />
          </button>
        </header>
        <p class="comparison-hint" role="status">
          <template v-if="loading">正在读取所选套餐的最新检查内容…</template>
          <template v-else-if="!allReady"
            >部分套餐加载失败，可在对应列重试；也可关闭对比继续预约。</template
          >
          <template v-else-if="differenceCount"
            >有 {{ differenceCount }} 组检查内容不同，下表已标出差异。</template
          >
          <template v-else>所选套餐检查项目一致，可结合价格和适用人群进行选择。</template>
        </p>
        <div
          class="comparison-scroll"
          tabindex="0"
          aria-label="套餐对比表，可左右滚动"
          :aria-busy="loading"
        >
          <table :style="{ minWidth: `${124 + columns.length * 180}px` }">
            <thead>
              <tr>
                <th scope="col">对比内容</th>
                <th v-for="column in columns" :key="column.original.smId" scope="col">
                  {{ column.detail?.name || column.original.name }}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <th scope="row">套餐价格</th>
                <td v-for="column in columns" :key="column.original.smId">
                  <strong v-if="column.detail" class="comparison-price"
                    >¥{{ money(column.detail.price) }}<small> / 人</small></strong
                  ><span v-else>{{ loading ? '加载中…' : '待加载' }}</span>
                </td>
              </tr>
              <tr>
                <th scope="row">适用人群</th>
                <td v-for="column in columns" :key="column.original.smId">
                  {{ column.detail ? gender(column.detail.type) : '—' }}
                </td>
              </tr>
              <tr>
                <th scope="row">检查项目</th>
                <td v-for="column in columns" :key="column.original.smId">
                  {{ column.detail ? (column.detail.items?.length || 0) + ' 组检查项目' : '—' }}
                </td>
              </tr>
              <tr
                v-for="row in rows"
                :key="row.key"
                :class="{ 'comparison-difference': row.different }"
              >
                <th scope="row">
                  {{ row.name }}<small v-if="row.different" class="difference-label">有差异</small>
                </th>
                <td v-for="(column, index) in columns" :key="column.original.smId">
                  <template v-if="column.detail"
                    ><template v-if="row.items[index]"
                      ><span class="included-label">✓ 包含</span>
                      <p class="comparison-item-copy">{{ row.items[index].ciContent }}</p>
                      <ul v-if="row.items[index].details?.length">
                        <li v-for="detail in row.items[index].details" :key="detail.cdId">
                          {{ detail.name }}
                        </li>
                      </ul></template
                    ><span v-else class="not-included">不包含</span></template
                  >
                  <span v-else>待加载</span>
                </td>
              </tr>
              <tr class="comparison-choice">
                <th scope="row">继续预约</th>
                <td v-for="column in columns" :key="column.original.smId">
                  <p v-if="column.error" class="comparison-error" role="alert">
                    {{ column.error }}
                  </p>
                  <button
                    v-if="column.error"
                    type="button"
                    class="comparison-retry"
                    :disabled="loading"
                    @click="load"
                  >
                    重新加载</button
                  ><button
                    v-else
                    type="button"
                    class="comparison-choose"
                    :disabled="!column.detail || loading"
                    :aria-label="`预约此套餐 ${column.detail?.name || column.original.name}`"
                    @click="choose(column)"
                  >
                    预约此套餐
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <footer class="comparison-footer">
          <span>手机可左右滑动查看完整对比</span
          ><button type="button" @click="close">返回套餐列表</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>
<style scoped>
.comparison-backdrop {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: #18363e70;
}
.comparison-dialog {
  width: min(100%, 920px);
  max-height: calc(100dvh - 40px);
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #dce9e9;
  border-radius: 14px;
  box-shadow: 0 18px 70px #173c4930;
  overflow: hidden;
  color: #425b60;
  font-size: calc(13px * var(--reading-scale, 1));
  line-height: 1.7;
}
.comparison-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 15px;
  padding: 19px 22px 15px;
  background: #f2f9f8;
}
.comparison-header h2 {
  margin: 0 0 3px;
  font-size: calc(18px * var(--reading-scale, 1));
  font-weight: 600;
  color: #256f7c;
}
.comparison-header p {
  margin: 0;
  color: #7a9599;
  font-size: calc(12px * var(--reading-scale, 1));
}
.comparison-close {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 50%;
  color: #527f86;
}
.comparison-close:hover {
  background: #e3eeee;
}
.comparison-hint {
  padding: 11px 22px;
  margin: 0;
  color: #789195;
  font-size: calc(12px * var(--reading-scale, 1));
  border-bottom: 1px solid #e6eeee;
}
.comparison-scroll {
  overflow: auto;
  min-height: 0;
  overscroll-behavior: contain;
}
.comparison-scroll table {
  width: 100%;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
  text-align: left;
  font-size: calc(13px * var(--reading-scale, 1));
  line-height: 1.7;
}
.comparison-scroll th,
.comparison-scroll td {
  vertical-align: top;
  padding: 13px 16px;
  border-right: 1px solid #e6eeee;
  border-bottom: 1px solid #e6eeee;
  overflow-wrap: anywhere;
  background: #fff;
}
.comparison-scroll th:first-child {
  position: sticky;
  left: 0;
  width: 124px;
  z-index: 1;
  background: #f8fbfb;
  box-shadow: 1px 0 #e6eeee;
}
.comparison-scroll thead th {
  position: sticky;
  top: 0;
  background: #eef6f5;
  z-index: 2;
  font-size: calc(14px * var(--reading-scale, 1));
  font-weight: 600;
  color: #2b7780;
}
.comparison-scroll thead th:first-child {
  z-index: 3;
  background: #eef6f5;
}
.comparison-scroll tbody th {
  font-weight: 500;
}
.comparison-scroll tr > :last-child {
  border-right: 0;
}
.comparison-price {
  color: #e6843d;
  font-size: calc(18px * var(--reading-scale, 1));
  font-weight: 500;
  white-space: nowrap;
}
.comparison-price small {
  color: #8f9fa0;
  font-size: calc(11px * var(--reading-scale, 1));
  font-weight: 400;
}
.comparison-scroll .comparison-difference > * {
  background: #fbfaf0;
}
.difference-label {
  display: block;
  font-size: calc(10px * var(--reading-scale, 1));
  font-weight: 400;
  color: #aa8444;
}
.included-label {
  color: #338b7d;
  font-size: calc(12px * var(--reading-scale, 1));
}
.comparison-item-copy {
  color: #84989b;
  font-size: calc(11px * var(--reading-scale, 1));
  margin: 3px 0 7px;
}
.comparison-scroll ul {
  margin: 0;
  padding: 0;
  list-style: none;
}
.comparison-scroll li {
  margin-top: 3px;
  font-size: calc(12px * var(--reading-scale, 1));
}
.not-included {
  color: #a3afb0;
}
.comparison-error {
  margin: 0 0 8px;
  font-size: calc(12px * var(--reading-scale, 1));
  color: #b27660;
}
.comparison-choose,
.comparison-retry {
  min-height: 34px;
  padding: 6px 12px;
  border-radius: 6px;
  border: 1px solid #137e92;
  background: #137e92;
  color: #fff;
  font-size: calc(12px * var(--reading-scale, 1));
}
.comparison-retry {
  color: #137e92;
  background: #fff;
}
.comparison-footer {
  display: flex;
  flex-shrink: 0;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 12px 22px;
  background: #fff;
}
.comparison-footer span {
  font-size: calc(11px * var(--reading-scale, 1));
  color: #8ca1a3;
}
.comparison-footer button {
  color: #137e92;
  font-size: calc(12px * var(--reading-scale, 1));
  min-height: 32px;
}
@media (max-width: 600px) {
  .comparison-backdrop {
    padding: 12px;
  }
  .comparison-dialog {
    max-height: calc(100dvh - 24px);
    border-radius: 12px;
  }
  .comparison-header {
    padding: 15px;
  }
  .comparison-header h2 {
    font-size: calc(16px * var(--reading-scale, 1));
  }
  .comparison-header p {
    font-size: calc(11px * var(--reading-scale, 1));
  }
  .comparison-hint {
    padding: 10px 15px;
    font-size: calc(11px * var(--reading-scale, 1));
  }
  .comparison-scroll th,
  .comparison-scroll td {
    padding: 11px 12px;
  }
  .comparison-footer {
    padding: 10px 15px;
  }
  .comparison-footer span {
    font-size: calc(10px * var(--reading-scale, 1));
  }
}
</style>
