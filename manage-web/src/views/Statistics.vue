<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { dateString, get, money } from '../api';

function currentMonth() {
  const now = new Date();
  return [
    dateString(new Date(now.getFullYear(), now.getMonth(), 1)),
    dateString(new Date(now.getFullYear(), now.getMonth() + 1, 0)),
  ];
}
const filters = reactive({ dates: currentMonth(), hpId: '' });
const hospitals = ref([]),
  data = ref(null),
  loading = ref(false),
  failure = ref(''),
  hospitalFailure = ref(''),
  dailyPage = ref(1);
const dayPageSize = 15;
let request = 0;
const totals = computed(() => data.value?.totals || {});
const daily = computed(() => data.value?.daily || []);
const pagedDays = computed(() =>
  daily.value.slice((dailyPage.value - 1) * dayPageSize, dailyPage.value * dayPageSize),
);
const selectedHospital = computed(() =>
  data.value?.hpId
    ? hospitals.value.find((h) => Number(h.hpId) === Number(data.value.hpId))?.name ||
      `医院 #${data.value.hpId}`
    : '全部医院',
);
function percent(value) {
  return `${Math.max(0, Math.min(100, Number(value) || 0)).toFixed(1)}%`;
}
const cards = computed(() => [
  {
    title: '预约总数',
    value: totals.value.appointments ?? 0,
    unit: '单',
    description: '含未归档、已归档和已取消',
  },
  {
    title: '未归档',
    value: totals.value.active ?? 0,
    unit: '单',
    description: '有效预约中待完成的报告',
  },
  {
    title: '已归档',
    value: totals.value.archived ?? 0,
    unit: '份',
    description: '已发布给用户的报告',
  },
  {
    title: '已取消',
    value: totals.value.cancelled ?? 0,
    unit: '单',
    description: '已释放占用名额',
  },
  {
    title: '名额使用率',
    value: percent(totals.value.utilization),
    description: `${totals.value.used ?? 0} / ${totals.value.capacity ?? 0} 个名额`,
  },
  {
    title: '报告归档率',
    value: percent(totals.value.archiveRate),
    description: '已归档 ÷ 有效预约，不含取消',
  },
]);
// 同一日期轴展示预约与归档数量，精确数量保留在下方表格中。
const chart = computed(() => {
  const rows = daily.value,
    max = Math.max(1, ...rows.map((d) => Number(d.appointments) || 0));
  const x = (i) => 46 + (rows.length > 1 ? (i / (rows.length - 1)) * 808 : 404);
  const y = (v) => 184 - ((Number(v) || 0) / max) * 144;
  const path = (key) => rows.map((d, i) => `${x(i)},${y(d[key])}`).join(' ');
  const indices = [...new Set([0, Math.floor((rows.length - 1) / 2), rows.length - 1])].filter(
    (i) => i >= 0 && rows[i],
  );
  return {
    max,
    appointments: path('appointments'),
    archived: path('archived'),
    ticks: indices.map((i) => ({ x: x(i), date: rows[i].date })),
    single:
      rows.length === 1
        ? { x: x(0), appointmentY: y(rows[0].appointments), archivedY: y(rows[0].archived) }
        : null,
  };
});
async function loadHospitals() {
  hospitalFailure.value = '';
  try {
    hospitals.value = await get('/admin/hospitals', {}, { quiet: true });
  } catch (e) {
    hospitalFailure.value = e.message;
  }
}
async function load() {
  const dates = filters.dates;
  if (!dates || dates.length !== 2) {
    failure.value = '请选择完整的体检日期区间';
    return;
  }
  const days =
    (Date.parse(`${dates[1]}T00:00:00Z`) - Date.parse(`${dates[0]}T00:00:00Z`)) / 86400000 + 1;
  if (!Number.isFinite(days) || days < 1 || days > 366) {
    failure.value = '日期区间应为 1–366 天';
    return;
  }
  const id = ++request;
  loading.value = true;
  failure.value = '';
  try {
    const result = await get(
      '/admin/statistics',
      { from: dates[0], to: dates[1], hpId: filters.hpId || undefined },
      { quiet: true },
    );
    if (id !== request) return;
    data.value = result;
    dailyPage.value = 1;
  } catch (e) {
    if (id === request) {
      data.value = null;
      failure.value = e.message;
    }
  } finally {
    if (id === request) loading.value = false;
  }
}
function reset() {
  filters.dates = currentMonth();
  filters.hpId = '';
  load();
}
onMounted(() => {
  loadHospitals();
  load();
});
onBeforeUnmount(() => {
  request++;
});
</script>

<template>
  <div class="page-heading">
    <div>
      <h1>数据统计</h1>
      <p class="page-description">按体检日期查看预约、名额使用和报告归档情况。</p>
    </div>
  </div>
  <section class="panel statistics-filters">
    <el-form :model="filters" label-position="top" @submit.prevent="load">
      <el-form-item label="体检日期区间（最多 366 天）">
        <el-date-picker
          v-model="filters.dates"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :clearable="false"
        />
      </el-form-item>
      <el-form-item label="体检医院"
        ><el-select v-model="filters.hpId" clearable placeholder="全部医院"
          ><el-option
            v-for="hospital in hospitals"
            :key="hospital.hpId"
            :label="hospital.name"
            :value="hospital.hpId" /></el-select
      ></el-form-item>
      <div class="statistics-filter-actions">
        <el-button type="primary" native-type="submit" :loading="loading">查询统计</el-button
        ><el-button :disabled="loading" @click="reset">本月全部医院</el-button>
      </div>
    </el-form>
    <p v-if="hospitalFailure" class="statistics-error" role="alert">
      医院选项加载失败：{{ hospitalFailure }}
      <el-button link type="primary" @click="loadHospitals">重试</el-button>
    </p>
  </section>
  <el-alert
    v-if="failure"
    :title="failure"
    type="error"
    :closable="false"
    show-icon
    class="statistics-load-error"
  />
  <div v-loading="loading" class="statistics-results" :aria-busy="loading">
    <template v-if="data">
      <p class="statistics-scope">
        {{ selectedHospital }} · {{ data.from }} 至 {{ data.to }} <span>当前展示已查询的数据</span>
      </p>
      <section class="statistics-cards" aria-label="统计概览">
        <article v-for="card in cards" :key="card.title">
          <h2>{{ card.title }}</h2>
          <strong
            >{{ card.value }}<small v-if="card.unit">{{ card.unit }}</small></strong
          >
          <p>{{ card.description }}</p>
        </article>
      </section>
      <section class="panel statistics-panel">
        <div class="panel-title">每日预约与归档 <span>按预约的体检日期归属统计</span></div>
        <div v-if="daily.length" class="statistics-chart">
          <div class="statistics-legend">
            <span class="appointment">预约总数</span><span class="archived">已归档报告</span>
          </div>
          <svg
            viewBox="0 0 900 230"
            role="img"
            :aria-label="`${data.from}至${data.to}每日预约与已归档报告折线图，下表提供详细数量`"
          >
            <line x1="46" x2="854" y1="184" y2="184" stroke="#cbd5df" />
            <line x1="46" x2="854" y1="40" y2="40" stroke="#edf1f5" />
            <line x1="46" x2="854" y1="112" y2="112" stroke="#edf1f5" />
            <text x="36" y="188" text-anchor="end">0</text>
            <text x="36" y="44" text-anchor="end">{{ chart.max }}</text>
            <polyline
              :points="chart.appointments"
              fill="none"
              stroke="#3984c5"
              stroke-width="2.5"
              stroke-linejoin="round"
            />
            <polyline
              :points="chart.archived"
              fill="none"
              stroke="#43a58b"
              stroke-width="2.5"
              stroke-linejoin="round"
            />
            <template v-if="chart.single">
              <circle :cx="chart.single.x" :cy="chart.single.appointmentY" r="4" fill="#3984c5" />
              <circle :cx="chart.single.x" :cy="chart.single.archivedY" r="3" fill="#43a58b" />
            </template>
            <text
              v-for="tick in chart.ticks"
              :key="tick.date"
              :x="tick.x"
              y="213"
              :text-anchor="tick === chart.ticks[0] ? 'start' : tick === chart.ticks.at(-1) ? 'end' : 'middle'"
            >
              {{ tick.date }}
            </text>
          </svg>
        </div>
        <el-empty v-else description="当前区间暂无日期统计" :image-size="70" />
        <el-table :data="pagedDays" stripe empty-text="暂无每日统计" class="statistics-daily-table">
          <el-table-column prop="date" label="体检日期" min-width="115" /><el-table-column
            prop="appointments"
            label="预约总数"
            min-width="85"
          /><el-table-column prop="active" label="未归档" min-width="80" /><el-table-column
            prop="archived"
            label="已归档"
            min-width="80"
          /><el-table-column prop="cancelled" label="已取消" min-width="80" />
          <el-table-column label="使用名额 / 容量" min-width="125"
            ><template #default="{ row }"
              >{{ row.used }} / {{ row.capacity }}</template
            ></el-table-column
          >
          <el-table-column label="名额使用率" min-width="150"
            ><template #default="{ row }"
              ><div class="statistics-utilization">
                <el-progress
                  :percentage="Math.max(0, Math.min(100, Number(row.utilization) || 0))"
                  :show-text="false"
                  :stroke-width="7"
                /><span>{{ percent(row.utilization) }}</span>
              </div></template
            ></el-table-column
          >
        </el-table>
        <div class="statistics-pagination">
          <el-pagination
            v-model:current-page="dailyPage"
            :total="daily.length"
            :page-size="dayPageSize"
            :pager-count="5"
            size="small"
            background
            layout="prev, pager, next, total"
          />
        </div>
      </section>
      <div class="statistics-detail-grid">
        <section class="panel statistics-panel">
          <div class="panel-title">热门套餐 <span>仅统计有效预约</span></div>
          <el-table :data="data.popularMeals || []" stripe empty-text="当前区间暂无有效预约"
            ><el-table-column prop="name" label="套餐名称" min-width="160" /><el-table-column
              prop="appointments"
              label="预约数量"
              width="95"
            /><el-table-column label="预约金额" min-width="115"
              ><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column
            ></el-table
          >
          <p class="statistics-note">按订单套餐快照汇总，金额为课程项目的预约记录金额。</p>
        </section>
        <section class="panel statistics-panel">
          <div class="panel-title">机构归档情况</div>
          <el-table :data="data.hospitalSummary || []" stripe empty-text="当前区间暂无医院预约"
            ><el-table-column prop="name" label="体检医院" min-width="160" /><el-table-column
              prop="appointments"
              label="总数"
              width="65" /><el-table-column
              prop="active"
              label="未归档"
              width="75" /><el-table-column
              prop="archived"
              label="已归档"
              width="75" /><el-table-column prop="cancelled" label="已取消" width="75"
          /></el-table>
        </section>
      </div>
      <p class="statistics-note">
        统计口径：预约数量按体检日期统计；名额按所选区间的排班容量和实际占用量统计；归档率为已归档数
        ÷（未归档数＋已归档数）。无名额或有效预约时，相应比率显示为 0。
      </p>
    </template>
    <el-empty v-else-if="!loading && !failure" description="请选择日期和医院后查询" />
  </div>
</template>

<style scoped>
.statistics-filters {
  padding: 16px;
}
.statistics-filters .el-form {
  display: grid;
  grid-template-columns: minmax(280px, 1.4fr) minmax(170px, 1fr) auto;
  gap: 16px;
  align-items: end;
}
.statistics-filters .el-form-item {
  margin-bottom: 0;
  min-width: 0;
}
.statistics-filters :deep(.el-date-editor) {
  width: 100%;
}
.statistics-filter-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.statistics-filter-actions .el-button + .el-button {
  margin-left: 0;
}
.statistics-error {
  margin: 10px 0 0;
  font-size: 12px;
  color: #b44a46;
}
.statistics-load-error {
  margin: 16px 18px;
  width: auto;
}
.statistics-results {
  margin: 0 18px 24px;
  min-height: 160px;
}
.statistics-scope {
  font-size: 12px;
  color: #4c6279;
  line-height: 1.8;
  margin: 15px 0;
}
.statistics-scope span {
  font-size: 11px;
  color: #909399;
  margin-left: 10px;
}
.statistics-cards {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}
.statistics-cards article {
  border: 1px solid #dbe4ed;
  border-radius: 6px;
  background: #fff;
  padding: 15px;
  min-width: 0;
}
.statistics-cards h2 {
  font-size: 12px;
  color: #566e85;
  font-weight: 400;
  margin: 0;
}
.statistics-cards strong {
  display: block;
  color: #256ca8;
  font-size: 25px;
  line-height: 1.4;
  margin: 8px 0 5px;
  overflow-wrap: anywhere;
}
.statistics-cards small {
  font-size: 11px;
  font-weight: 400;
  margin-left: 5px;
  color: #909399;
}
.statistics-cards p {
  font-size: 10px;
  color: #8b98a5;
  line-height: 1.7;
  margin: 0;
}
.statistics-panel {
  margin: 16px 0 0;
}
.statistics-chart {
  padding: 16px 20px 0;
}
.statistics-chart svg {
  width: 100%;
  max-height: 300px;
  display: block;
}
.statistics-chart text {
  font-family: inherit;
  font-size: 12px;
  fill: #8190a0;
}
.statistics-legend {
  display: flex;
  justify-content: flex-end;
  gap: 18px;
  font-size: 11px;
  color: #7c8c9c;
}
.statistics-legend span::before {
  content: '';
  display: inline-block;
  width: 18px;
  height: 3px;
  background: #3984c5;
  margin: 0 6px 3px 0;
}
.statistics-legend .archived::before {
  background: #43a58b;
}
.statistics-utilization {
  display: flex;
  gap: 8px;
  align-items: center;
}
.statistics-utilization .el-progress {
  flex: 1;
  min-width: 25px;
}
.statistics-utilization span {
  min-width: 40px;
  color: #7c8c9c;
  font-size: 11px;
}
.statistics-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 14px;
  overflow-x: auto;
}
.statistics-detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
}
.statistics-detail-grid .panel-title {
  flex-wrap: wrap;
}
.statistics-note {
  margin: 14px 0 0;
  color: #8b98a5;
  font-size: 11px;
  line-height: 1.8;
}
.statistics-panel .statistics-note {
  padding: 0 16px 14px;
}
@media (max-width: 1250px) {
  .statistics-cards {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .statistics-filters .el-form {
    grid-template-columns: minmax(250px, 1.4fr) minmax(160px, 1fr);
  }
  .statistics-filter-actions {
    grid-column: 1 / -1;
  }
  .statistics-detail-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
@media (max-width: 760px) {
  .statistics-results {
    margin-left: 12px;
    margin-right: 12px;
  }
  .statistics-load-error {
    margin: 12px;
  }
  .statistics-filters .el-form {
    grid-template-columns: minmax(0, 1fr);
    gap: 12px;
  }
  .statistics-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }
  .statistics-cards article {
    padding: 12px;
  }
  .statistics-cards strong {
    font-size: 23px;
  }
  .statistics-chart {
    padding: 14px 4px 0;
  }
  .statistics-chart svg {
    min-height: 150px;
  }
  .statistics-chart text {
    font-size: 25px;
  }
  .statistics-scope span {
    display: block;
    margin-left: 0;
  }
}
</style>
