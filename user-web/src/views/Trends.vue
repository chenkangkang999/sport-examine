<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { api, get } from '../api';
import { dateText } from '../state';
import AsyncState from '../components/AsyncState.vue';
import Icon from '../components/Icon.vue';
import { numericValue, numericReference, formatReference } from '../utils/reportMetrics';

const route = useRoute();
const persons = ref([]),
  personId = ref(''),
  response = ref(null),
  metricKey = ref('');
const loading = ref(true),
  error = ref(''),
  selectedPoint = ref(0),
  chartHost = ref(null),
  plotWidth = ref(400);
let requestId = 0,
  observer;
const series = computed(() => (Array.isArray(response.value?.series) ? response.value.series : []));
const metric = computed(() => series.value.find((item) => item.key === metricKey.value));
const points = computed(() =>
  (metric.value?.points || []).filter((point) => numericValue(point.value) != null),
);
const personOptions = computed(() => {
  const list = [...persons.value];
  if (
    response.value?.personId &&
    !list.some((person) => Number(person.personId) === Number(response.value.personId))
  )
    list.push({
      personId: response.value.personId,
      realName: response.value.personName,
      relationship: '历史档案',
    });
  return list;
});
const current = computed(() => points.value[selectedPoint.value] || points.value.at(-1));
const referenceSummary = computed(() => {
  if (!points.value.length) return '';
  const signatures = points.value.map((point) =>
    JSON.stringify(numericReference({ ...point, type: metric.value.type })),
  );
  if (new Set(signatures).size > 1) return '各次参考范围有变化，请结合下方每次记录查看。';
  const text = formatReference({ ...points.value[0], type: metric.value.type });
  return text
    ? `参考${Number(metric.value.type) === 2 ? '值' : '范围'}：${text} ${metric.value.unit || ''}`
    : '此指标未设置数值参考范围。';
});
const chart = computed(() => {
  if (points.value.length < 2) return null;
  const width = plotWidth.value,
    left = 49,
    right = width - 18,
    top = 24,
    bottom = 194;
  const values = points.value.map((point) => Number(point.value));
  const references = points.value.map((point) =>
    numericReference({ ...point, type: metric.value.type }),
  );
  const bounds = references.flatMap((ref) =>
    !ref
      ? []
      : ref.type === 'target'
        ? [ref.target]
        : [ref.lower, ref.upper].filter((value) => value != null),
  );
  let minimum = Math.min(...values, ...bounds),
    maximum = Math.max(...values, ...bounds);
  const padding = (maximum - minimum || Math.max(Math.abs(maximum) * 0.2, 1)) * 0.15;
  minimum -= padding;
  maximum += padding;
  const y = (value) => bottom - ((value - minimum) / (maximum - minimum)) * (bottom - top);
  const coords = values.map((value, index) => ({
    x: left + (index / (values.length - 1)) * (right - left),
    y: y(value),
  }));
  const indices = [...new Set([0, Math.floor((values.length - 1) / 2), values.length - 1])];
  // 参考值有变动时不铺设统一色带，逐次参考值保留在下方记录表中。
  const shared =
    new Set(references.map((ref) => JSON.stringify(ref))).size === 1 ? references[0] : null;
  const band =
    shared?.type === 'range'
      ? {
          top: shared.upper == null ? top : y(shared.upper),
          bottom: shared.lower == null ? bottom : y(shared.lower),
        }
      : null;
  return {
    width,
    left,
    right,
    top,
    bottom,
    coords,
    path: coords.map((p) => `${p.x},${p.y}`).join(' '),
    ticks: Array.from({ length: 5 }, (_, i) => ({
      value: minimum + (i / 4) * (maximum - minimum),
      y: y(minimum + (i / 4) * (maximum - minimum)),
    })),
    indices,
    band,
    target: shared?.type === 'target' ? y(shared.target) : null,
  };
});
function axisNumber(value) {
  return value !== 0 && (Math.abs(value) >= 100000 || Math.abs(value) < 0.01)
    ? value.toExponential(1)
    : Number(value.toFixed(3)).toString();
}
function chooseMetric() {
  selectedPoint.value = Math.max(0, points.value.length - 1);
}
function pickMetric() {
  const detailId = Number(route.query.detail),
    orderId = Number(route.query.orderId);
  const requested = series.value.find(
    (item) =>
      Number(item.sourceDetailId) === detailId &&
      (route.query.unit == null || String(item.unit || '') === String(route.query.unit || '')) &&
      (!orderId || item.points?.some((point) => Number(point.orderId) === orderId)),
  );
  metricKey.value = requested?.key || series.value[0]?.key || '';
  chooseMetric();
}
async function load() {
  if (!personId.value) return;
  const request = ++requestId;
  loading.value = true;
  error.value = '';
  response.value = null;
  try {
    const result = await get('/reports/trends', { personId: personId.value });
    // 切换体检人后，忽略先前请求，避免旧档案的曲线覆盖当前选择。
    if (request !== requestId) return;
    response.value = result;
    pickMetric();
  } catch (failure) {
    if (request === requestId) error.value = failure.message;
  } finally {
    if (request === requestId) loading.value = false;
  }
}
async function initialise() {
  loading.value = true;
  error.value = '';
  try {
    persons.value = await api.persons();
    const requested = Number(route.query.personId);
    const next =
      Number.isInteger(requested) && requested > 0 ? requested : persons.value[0]?.personId;
    if (next) {
      personId.value = String(next);
      await load();
    } else {
      response.value = { series: [] };
      loading.value = false;
    }
  } catch (failure) {
    error.value = failure.message;
    loading.value = false;
  }
}
function personChanged() {
  load();
}
watch(metricKey, chooseMetric);
watch(
  chartHost,
  (host) => {
    observer?.disconnect();
    if (host) {
      observer = new ResizeObserver(([entry]) => {
        plotWidth.value = Math.max(260, Math.round(entry.contentRect.width));
      });
      observer.observe(host);
    }
  },
  { flush: 'post' },
);
onBeforeUnmount(() => {
  requestId++;
  observer?.disconnect();
});
onMounted(initialise);
</script>
<template>
  <section class="trends-page">
    <header class="trends-heading">
      <span class="trends-kicker"><Icon name="report" :size="15" />健康记录</span>
      <h1>历次指标趋势</h1>
      <p>按体检人查看已归档报告中的同类数值变化。</p>
    </header>
    <div class="trends-filters">
      <label
        >体检人<select
          v-model="personId"
          :disabled="!personOptions.length"
          @change="personChanged"
          aria-label="选择趋势体检人"
        >
          <option
            v-for="person in personOptions"
            :key="person.personId"
            :value="String(person.personId)"
          >
            {{ person.realName
            }}{{
              person.isSelf ? '（本人）' : person.relationship ? `（${person.relationship}）` : ''
            }}
          </option>
        </select></label
      >
      <label v-if="series.length"
        >检查指标<select v-model="metricKey" aria-label="选择趋势指标">
          <option v-for="item in series" :key="item.key" :value="item.key">
            {{ item.name }}{{ item.unit ? `（${item.unit}）` : '' }}
          </option>
        </select></label
      >
    </div>
    <AsyncState
      :loading="loading"
      :error="error"
      :empty="!series.length"
      message="暂无可展示的数值记录"
      @retry="personOptions.length ? load() : initialise()"
      ><template #hint
        >医生归档报告后，数值类检查记录会在这里汇总。文字描述项目不绘制趋势。</template
      ><template #action
        ><RouterLink to="/reportlist" class="btn secondary">查看体检报告</RouterLink></template
      >
      <template v-if="metric">
        <section class="trend-chart-card">
          <div class="trend-metric-heading">
            <div>
              <h2>{{ metric.name }}</h2>
              <span>{{ response?.personName }} · {{ points.length }} 次记录</span>
            </div>
            <p>
              <strong>{{ current?.value ?? '—' }}</strong
              ><small>{{ metric.unit || '' }}</small>
            </p>
          </div>
          <p class="trend-reference-caption">{{ referenceSummary }}</p>
          <div v-if="points.length < 2" class="trend-single">
            <Icon name="clock" :size="27" />
            <h3>暂无可对比的历次数据</h3>
            <p>目前仅有 {{ points.length }} 次数值记录，已有结果仍可在下方查看。</p>
          </div>
          <template v-else>
            <div ref="chartHost" class="trend-chart-host">
              <svg
                class="trend-chart"
                :viewBox="`0 0 ${chart.width} 244`"
                role="group"
                :aria-label="`${metric.name}历次变化图，按体检日期与记录顺序排列`"
              >
                <title>{{ metric.name }}历次变化</title>
                <desc>点击或聚焦数据点查看该次结果，下方同时提供完整数值表。</desc>
                <rect
                  v-if="chart.band"
                  :x="chart.left"
                  :y="chart.band.top"
                  :width="chart.right - chart.left"
                  :height="chart.band.bottom - chart.band.top"
                  class="trend-reference-band"
                />
                <g v-for="(tick, index) in chart.ticks" :key="index">
                  <line
                    :x1="chart.left"
                    :x2="chart.right"
                    :y1="tick.y"
                    :y2="tick.y"
                    class="trend-grid-line"
                  />
                  <text
                    :x="chart.left - 8"
                    :y="tick.y + 4"
                    text-anchor="end"
                    class="trend-axis-label"
                  >
                    {{ axisNumber(tick.value) }}
                  </text>
                </g>
                <line
                  v-if="chart.target != null"
                  :x1="chart.left"
                  :x2="chart.right"
                  :y1="chart.target"
                  :y2="chart.target"
                  class="trend-target-line"
                />
                <polyline :points="chart.path" class="trend-data-line" />
                <circle
                  v-for="(point, index) in chart.coords"
                  :key="`${points[index].orderId}-${index}`"
                  :cx="point.x"
                  :cy="point.y"
                  :r="index === selectedPoint ? 6 : 4.5"
                  class="trend-data-point"
                  :class="{
                    abnormal: Number(points[index].isError) === 1,
                    selected: index === selectedPoint,
                  }"
                  role="button"
                  tabindex="0"
                  :aria-label="`${dateText(points[index].orderDate)}，${points[index].value}${metric.unit || ''}，${Number(points[index].isError) === 1 ? '异常' : '未标记异常'}`"
                  @click="selectedPoint = index"
                  @focus="selectedPoint = index"
                  @keydown.enter.prevent="selectedPoint = index"
                  @keydown.space.prevent="selectedPoint = index"
                />
                <text
                  v-for="index in chart.indices"
                  :key="index"
                  :x="chart.coords[index].x"
                  y="220"
                  :text-anchor="
                    index === 0 ? 'start' : index === points.length - 1 ? 'end' : 'middle'
                  "
                  class="trend-axis-label"
                >
                  {{ dateText(points[index].orderDate) }}
                </text>
              </svg>
            </div>
            <div class="trend-legend">
              <span><i class="legend-line"></i>检查数值</span
              ><span v-if="chart.band"><i class="legend-range"></i>共同参考范围</span
              ><span v-if="chart.target != null">虚线为共同参考值</span
              ><span><i class="legend-abnormal"></i>已标记异常</span>
            </div>
            <p class="trend-order-note">数据点按体检日期与记录顺序排列，点击可查看结果。</p>
            <div v-if="current" class="trend-current-point">
              <span>{{ dateText(current.orderDate) }}</span
              ><strong>{{ current.value }} {{ metric.unit }}</strong
              ><span :class="{ 'point-error': Number(current.isError) === 1 }">{{
                Number(current.isError) === 1 ? '异常' : '未标记异常'
              }}</span>
            </div>
          </template>
        </section>
        <section class="trend-records">
          <h2>历次记录</h2>
          <p>每次报告的参考范围可能不同，请以对应记录为准。</p>
          <div class="trend-table-scroll" tabindex="0" aria-label="历次检查记录，可左右滚动">
            <table>
              <thead>
                <tr>
                  <th scope="col">体检日期</th>
                  <th scope="col">结果</th>
                  <th scope="col">参考范围</th>
                  <th scope="col">提示</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(point, index) in points" :key="`${point.orderId}-${index}`">
                  <td>
                    <RouterLink :to="`/report/${point.orderId}`"
                      >{{ dateText(point.orderDate) }}<small>查看报告 ›</small></RouterLink
                    >
                  </td>
                  <td>
                    {{ point.value }}<small>{{ metric.unit }}</small>
                  </td>
                  <td>{{ formatReference({ ...point, type: metric.type }) || '—' }}</td>
                  <td :class="{ 'point-error': Number(point.isError) === 1 }">
                    {{ Number(point.isError) === 1 ? '异常' : '—' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <p class="trends-note">图表仅呈现已归档检查记录，不根据变化曲线自动生成诊断。</p>
      </template>
    </AsyncState>
  </section>
</template>
<style scoped>
.trends-page {
  padding: 22px 16px 25px;
  color: #405359;
  min-height: calc(100dvh - 124px);
}
.trends-kicker {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  color: #348b88;
  font-size: calc(12px * var(--reading-scale, 1));
}
.trends-heading h1 {
  font-size: calc(23px * var(--reading-scale, 1));
  color: #204c55;
  margin: 8px 0;
}
.trends-heading p {
  font-size: calc(12px * var(--reading-scale, 1));
  color: #819295;
  line-height: 1.7;
}
.trends-filters {
  display: grid;
  gap: 12px;
  margin: 20px 0;
}
.trends-filters label {
  display: flex;
  flex-direction: column;
  gap: 7px;
  color: #607b80;
  font-size: calc(12px * var(--reading-scale, 1));
}
.trends-filters select {
  width: 100%;
  min-width: 0;
  min-height: 39px;
  border: 1px solid #dce8e5;
  border-radius: 7px;
  background: #fff;
  color: #405359;
  font: inherit;
  padding: 7px 10px;
}
.trend-chart-card,
.trend-records {
  border: 1px solid #e1ece9;
  border-radius: 10px;
  background: #fff;
  padding: 16px 13px;
}
.trend-metric-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}
.trend-metric-heading h2 {
  margin: 0 0 5px;
  font-size: calc(17px * var(--reading-scale, 1));
  color: #315f66;
  overflow-wrap: anywhere;
}
.trend-metric-heading span {
  color: #829397;
  font-size: calc(11px * var(--reading-scale, 1));
}
.trend-metric-heading p {
  text-align: right;
  flex-shrink: 0;
}
.trend-metric-heading strong {
  display: block;
  color: #157c8a;
  font-size: calc(23px * var(--reading-scale, 1));
  font-weight: 600;
  line-height: 1.3;
}
.trend-metric-heading small {
  font-size: calc(10px * var(--reading-scale, 1));
  color: #829397;
}
.trend-reference-caption {
  margin: 15px 0 9px;
  color: #829397;
  font-size: calc(11px * var(--reading-scale, 1));
  line-height: 1.7;
}
.trend-chart-host {
  width: 100%;
  min-width: 0;
}
.trend-chart {
  display: block;
  width: 100%;
  height: auto;
  overflow: visible;
}
.trend-reference-band {
  fill: #e7f3eb;
}
.trend-grid-line {
  stroke: #e6eeee;
  stroke-width: 1;
}
.trend-target-line {
  stroke: #6e9d94;
  stroke-width: 1.5;
  stroke-dasharray: 5 4;
}
.trend-axis-label {
  fill: #829397;
  font-size: calc(10px * var(--reading-scale, 1));
  font-family: inherit;
}
.trend-data-line {
  fill: none;
  stroke: #278d96;
  stroke-width: 2.3;
  stroke-linejoin: round;
  stroke-linecap: round;
}
.trend-data-point {
  fill: #278d96;
  stroke: #fff;
  stroke-width: 2;
  cursor: pointer;
}
.trend-data-point.abnormal {
  fill: #ba634e;
}
.trend-data-point.selected,
.trend-data-point:focus-visible {
  stroke: #294f57;
  stroke-width: 2;
  outline: none;
}
.trend-legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px 13px;
  font-size: calc(10px * var(--reading-scale, 1));
  color: #7a8e91;
}
.trend-legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.trend-legend i {
  display: inline-block;
}
.legend-line {
  width: 15px;
  height: 2px;
  background: #278d96;
}
.legend-range {
  width: 14px;
  height: 7px;
  background: #d9ecdf;
}
.legend-abnormal {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #ba634e;
}
.trend-order-note {
  margin: 10px 0 0;
  font-size: calc(10px * var(--reading-scale, 1));
  color: #8a999b;
  line-height: 1.7;
}
.trend-current-point {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 12px;
  padding: 10px 11px;
  margin-top: 10px;
  background: #f2f8f7;
  border-radius: 6px;
  font-size: calc(11px * var(--reading-scale, 1));
  color: #71878a;
}
.trend-current-point strong {
  color: #345d65;
  font-weight: 500;
}
.trend-single {
  padding: 24px 10px 19px;
  text-align: center;
  color: #80a6a7;
}
.trend-single h3 {
  font-size: calc(14px * var(--reading-scale, 1));
  color: #607b80;
  margin: 10px 0 7px;
  font-weight: 500;
}
.trend-single p {
  font-size: calc(11px * var(--reading-scale, 1));
  color: #8a999b;
  line-height: 1.8;
}
.trend-records {
  margin-top: 16px;
}
.trend-records h2 {
  font-size: calc(15px * var(--reading-scale, 1));
  color: #315f66;
  margin: 0;
}
.trend-records > p {
  font-size: calc(10px * var(--reading-scale, 1));
  color: #8a999b;
  line-height: 1.7;
  margin: 8px 0 12px;
}
.trend-table-scroll {
  overflow-x: auto;
}
.trend-table-scroll table {
  width: 100%;
  min-width: 330px;
  border-collapse: collapse;
  text-align: left;
  font-size: calc(11px * var(--reading-scale, 1));
  line-height: 1.7;
}
.trend-table-scroll th {
  background: #f3f8f7;
  padding: 9px 7px;
  color: #6a8387;
  font-weight: 500;
}
.trend-table-scroll td {
  border-bottom: 1px solid #edf1f0;
  padding: 11px 7px;
  vertical-align: top;
}
.trend-table-scroll td:first-child {
  white-space: nowrap;
}
.trend-table-scroll td a {
  color: #278d96;
}
.trend-table-scroll small {
  display: block;
  font-size: calc(10px * var(--reading-scale, 1));
  color: #8a999b;
}
.point-error {
  color: #b26650 !important;
}
.trends-note {
  margin: 15px 3px 0;
  font-size: calc(10px * var(--reading-scale, 1));
  color: #8a999b;
  line-height: 1.8;
}
@media (max-width: 350px) {
  .trends-page {
    padding-inline: 12px;
  }
  .trend-chart-card,
  .trend-records {
    padding-inline: 10px;
  }
}
</style>
