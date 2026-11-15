<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { api } from '../api';
import { dateText, sexText } from '../state';
import Icon from '../components/Icon.vue';
import AsyncState from '../components/AsyncState.vue';
import ReportPrint from '../components/ReportPrint.vue';
import ReferenceRange from '../components/ReferenceRange.vue';
import { formatReference } from '../utils/reportMetrics';
const route = useRoute(),
  data = ref(null),
  loading = ref(true),
  error = ref(''),
  tab = ref('summary'),
  printing = ref(false);
const canPrint = computed(
  () => !loading.value && !error.value && Number(data.value?.order.state) === 2,
);
const abnormal = computed(
  () =>
    data.value?.items?.flatMap((i) =>
      (i.details || [])
        .filter((d) => Number(d.isError) === 1)
        .map((d) => ({ ...d, ciName: i.ciName })),
    ) || [],
);
const range = formatReference;
let loadId = 0;
async function load() {
  const request = ++loadId;
  loading.value = true;
  error.value = '';
  data.value = null;
  tab.value = 'summary';
  try {
    const report = await api.report(route.params.orderId);
    if (request === loadId) data.value = report;
  } catch (e) {
    if (request === loadId) error.value = e.message;
  } finally {
    if (request === loadId) loading.value = false;
  }
}
async function printReport() {
  if (!canPrint.value || printing.value) return;
  const request = loadId;
  printing.value = true;
  try {
    // 打印独立完整副本，不受屏幕上当前选择的报告标签影响。
    await nextTick();
    if (document.fonts?.ready) await document.fonts.ready;
    if (request === loadId && canPrint.value) window.print();
  } finally {
    printing.value = false;
  }
}
onBeforeUnmount(() => {
  loadId++;
});
watch(() => route.params.orderId, load, { immediate: true });
</script>
<template>
  <div class="report-document">
    <nav class="report-detail-tabs" aria-label="体检报告标签">
      <button
        :class="{ active: tab === 'summary' }"
        :aria-pressed="tab === 'summary'"
        @click="tab = 'summary'"
      >
        总检结论</button
      ><button
        :class="{ active: tab === 'details' }"
        :aria-pressed="tab === 'details'"
        @click="tab = 'details'"
      >
        报告详情
      </button>
    </nav>
    <AsyncState :loading="loading" :error="error" @retry="load"
      ><template v-if="data">
        <div class="report-print-toolbar">
          <button
            class="report-print-button"
            :disabled="!canPrint || printing"
            @click="printReport"
          >
            <Icon name="report" :size="16" />{{ printing ? '正在打开…' : '打印 / 保存 PDF' }}
          </button>
          <RouterLink
            class="report-trends-link"
            :to="{ path: '/trends', query: { personId: data.order.personId } }"
            >历次指标 ›</RouterLink
          >
          <small>打印包含完整报告。在打印窗口选择“另存为 PDF”即可保存。</small>
        </div>
        <p class="report-person-note">
          {{ dateText(data.order.orderDate) }} · {{ data.order.personName }} ·
          {{ sexText(data.order.personSex) }}
        </p>
        <div v-if="tab === 'summary'" class="report-summary">
          <section class="prototype-report-section">
            <h2>异常项</h2>
            <div v-if="!abnormal.length" class="normal-note">本次报告未标记异常项目。</div>
            <div v-for="detail in abnormal" :key="detail.cidrId" class="indications">
              <div class="indication-name">
                <span class="error-label">异</span><strong>{{ detail.name }}</strong>
              </div>
              <div class="indication-value">
                <p>
                  {{ detail.value }} <span>{{ detail.unit }}</span>
                </p>
                <small v-if="range(detail)">正常范围：{{ range(detail) }}</small>
                <ReferenceRange :detail="detail" />
                <RouterLink
                  v-if="detail.sourceDetailId && [1, 2, 3].includes(Number(detail.type))"
                  class="detail-trends-link"
                  :to="{
                    path: '/trends',
                    query: {
                      personId: data.order.personId,
                      detail: detail.sourceDetailId,
                      unit: detail.unit || '',
                      orderId: data.order.orderId,
                    },
                  }"
                  >查看历次变化 ›</RouterLink
                >
              </div>
            </div>
          </section>
          <section class="prototype-report-section">
            <h2>一、尊敬的顾客，您本次体检结论如下：</h2>
            <p
              v-for="(result, index) in data.conclusions"
              :key="result.orId"
              class="conclusion-title"
            >
              {{ index + 1 }}、{{ result.title }}
            </p>
            <p v-if="!data.conclusions?.length" class="normal-note">暂无总检结论。</p>
          </section>
          <section class="prototype-report-section">
            <h2>二、尊敬的顾客，您本次体检建议如下：</h2>
            <article
              v-for="(result, index) in data.conclusions"
              :key="result.orId"
              class="conclusion-content"
            >
              <h3>{{ index + 1 }}、{{ result.title }}</h3>
              <p>{{ result.content }}</p>
            </article>
          </section>
        </div>
        <div v-else class="report-items">
          <section v-for="item in data.items" :key="item.cirId" class="prototype-report-section">
            <h2>{{ item.ciName }}</h2>
            <div v-for="detail in item.details" :key="detail.cidrId" class="indications">
              <div class="indication-name">
                <span v-if="Number(detail.isError) === 1" class="error-label">异</span
                ><strong>{{ detail.name }}</strong>
              </div>
              <div class="indication-value">
                <p>
                  {{ detail.value ?? '—' }} <span>{{ detail.unit }}</span>
                </p>
                <small v-if="range(detail)">正常范围：{{ range(detail) }}</small>
                <ReferenceRange :detail="detail" />
                <RouterLink
                  v-if="detail.sourceDetailId && [1, 2, 3].includes(Number(detail.type))"
                  class="detail-trends-link"
                  :to="{
                    path: '/trends',
                    query: {
                      personId: data.order.personId,
                      detail: detail.sourceDetailId,
                      unit: detail.unit || '',
                      orderId: data.order.orderId,
                    },
                  }"
                  >查看历次变化 ›</RouterLink
                >
              </div>
            </div>
          </section>
        </div>
        <p class="report-hospital-note">
          {{ data.order.hospitalName }}<br />{{ data.order.personIdentityMasked }}
        </p>
      </template></AsyncState
    >
  </div>
  <ReportPrint v-if="canPrint" :report="data" :format-range="range" />
</template>
<style scoped>
.report-trends-link {
  color: #137e92;
  font-size: calc(12px * var(--reading-scale, 1));
  padding: 7px 4px;
}
.detail-trends-link {
  display: inline-block;
  color: #378b94;
  font-size: calc(10px * var(--reading-scale, 1));
  margin-top: 7px;
}
.report-print-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 3.6% 0;
}
.report-print-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 36px;
  padding: 7px 12px;
  border: 1px solid #b8d8dd;
  border-radius: 6px;
  background: #eff8f9;
  color: #137e92;
  font-size: calc(12px * var(--reading-scale, 1));
}
.report-print-toolbar small {
  font-size: calc(11px * var(--reading-scale, 1));
  line-height: 1.7;
  color: #7b8e93;
}
.report-document {
  padding-top: 52px;
  min-height: calc(100dvh - 124px);
  color: #555;
}
.report-detail-tabs {
  position: fixed;
  top: 60px;
  left: 50%;
  transform: translateX(-50%);
  width: min(100%, 480px);
  height: 52px;
  z-index: 15;
  display: flex;
  background: #f9f9f9;
  border-bottom: 1px solid #eee;
}
.report-detail-tabs > button {
  flex: 1;
  font-size: calc(15px * var(--reading-scale, 1));
  font-weight: 600;
  color: #555;
  border-bottom: 2px solid transparent;
  margin: 0;
  padding: 0;
  background: transparent;
  border-radius: 0;
}
.report-detail-tabs > button.active {
  border-bottom-color: #137e92;
  color: #137e92;
}
.report-person-note {
  font-size: calc(10px * var(--reading-scale, 1));
  color: #999;
  text-align: center;
  line-height: 1.7;
  margin: 8px 4% 0;
}
.prototype-report-section {
  margin: 11px 3.6% 0;
  border-radius: 11px;
  overflow: hidden;
  background: #fff;
  padding: 0;
  border: 0;
  box-shadow: none;
}
.prototype-report-section > h2 {
  background: #7bafd7;
  color: #fff;
  font-size: calc(14px * var(--reading-scale, 1));
  font-weight: 400;
  line-height: 1.7;
  padding: 8px 14px;
  min-height: 37px;
  margin: 0;
}
.indications {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 12px;
  min-height: 59px;
  border-bottom: 1px solid #eee;
  font-size: calc(12px * var(--reading-scale, 1));
  line-height: 1.65;
}
.indications:last-child {
  border-bottom: 0;
}
.indication-name {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  color: #333;
}
.indication-name strong {
  font-weight: 600;
  font-size: calc(12px * var(--reading-scale, 1));
  overflow-wrap: anywhere;
}
.error-label {
  background: #ba634e;
  color: #fff;
  font-size: calc(11px * var(--reading-scale, 1));
  line-height: 17px;
  min-width: 17px;
  height: 17px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 2px;
  flex-shrink: 0;
}
.indication-value {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}
.indication-value p {
  margin: 0;
  color: #555;
  font-size: calc(12px * var(--reading-scale, 1));
  white-space: pre-wrap;
}
.indication-value p > span {
  font-size: calc(11px * var(--reading-scale, 1));
  color: #777;
}
.indication-value small {
  font-size: calc(10px * var(--reading-scale, 1));
  color: #999;
  line-height: 1.6;
  display: block;
}
.conclusion-title {
  font-size: calc(13px * var(--reading-scale, 1));
  line-height: 1.8;
  padding: 12px;
  border-bottom: 1px solid #eee;
  margin: 0;
}
.conclusion-title:last-child {
  border-bottom: 0;
}
.conclusion-content {
  padding: 13px 12px;
  border-bottom: 1px solid #eee;
}
.conclusion-content:last-child {
  border-bottom: 0;
}
.conclusion-content h3 {
  font-size: calc(14px * var(--reading-scale, 1));
  font-weight: 600;
  line-height: 1.8;
  margin: 0 0 7px;
  color: #555;
}
.conclusion-content p {
  font-size: calc(12px * var(--reading-scale, 1));
  line-height: 1.9;
  text-indent: 2em;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  margin: 0;
  color: #666;
}
.normal-note {
  padding: 15px 12px;
  font-size: calc(12px * var(--reading-scale, 1));
  color: #999;
  margin: 0;
}
.report-hospital-note {
  text-align: center;
  font-size: calc(10px * var(--reading-scale, 1));
  line-height: 1.8;
  color: #aaa;
  margin: 18px 6% 15px;
}
@media (max-width: 360px) {
  .report-detail-tabs {
    top: 55px;
  }
}
</style>
