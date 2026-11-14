<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { get, post, dateString } from '../api';
const hospitalList = ref([]),
  rows = ref([]),
  loading = ref(false),
  saving = ref(false),
  failure = ref('');
const end = new Date();
end.setDate(end.getDate() + 30);
const form = reactive({
  hpId: '',
  dates: [dateString(), dateString(end)],
  capacity: 200,
  weekdays: [1, 2, 3, 4, 5],
});
const weekdays = [
  { label: '周一', value: 1 },
  { label: '周二', value: 2 },
  { label: '周三', value: 3 },
  { label: '周四', value: 4 },
  { label: '周五', value: 5 },
  { label: '周六', value: 6 },
  { label: '周日', value: 7 },
];
async function load() {
  if (!form.hpId || !form.dates?.length) return;
  loading.value = true;
  failure.value = '';
  try {
    rows.value = await get('/admin/schedules', {
      hpId: form.hpId,
      from: form.dates[0],
      to: form.dates[1],
    });
  } catch (e) {
    failure.value = e.message;
  } finally {
    loading.value = false;
  }
}
async function save() {
  if (!form.hpId || form.dates?.length !== 2) {
    ElMessage.warning('请先选择医院与日期范围');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `将选定范围内勾选的星期设置为每日 ${form.capacity} 人，未勾选的星期关闭预约。已有预约占用不会被清除。`,
      '批量更新名额',
      { type: 'warning', confirmButtonText: '确认设置', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  saving.value = true;
  try {
    await post('/admin/schedules/save', {
      hpId: form.hpId,
      from: form.dates[0],
      to: form.dates[1],
      capacity: form.capacity,
      weekdays: form.weekdays,
    });
    ElMessage.success('预约名额已更新');
    await load();
  } catch {
  } finally {
    saving.value = false;
  }
}
onMounted(async () => {
  try {
    hospitalList.value = await get('/admin/hospitals');
    form.hpId = hospitalList.value[0]?.hpId || '';
    await load();
  } catch (e) {
    failure.value = e.message;
  }
});
</script>
<template>
  <div class="page-heading">
    <div>
      <div class="eyebrow">APPOINTMENT CAPACITY</div>
      <h1>预约名额管理</h1>
      <p class="page-description">按医院与日期安排接待名额，预约和取消后实时同步。</p>
    </div>
  </div>
  <section class="panel schedule-config">
    <div class="panel-title">排班与名额配置<span>支持连续日期批量设置</span></div>
    <el-form :model="form" label-position="top"
      ><div class="schedule-main-fields">
        <el-form-item label="体检医院"
          ><el-select v-model="form.hpId" placeholder="选择医院" @change="load"
            ><el-option
              v-for="h in hospitalList"
              :key="h.hpId"
              :label="h.name"
              :value="h.hpId" /></el-select></el-form-item
        ><el-form-item label="日期范围"
          ><el-date-picker
            v-model="form.dates"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            :clearable="false"
            @change="load" /></el-form-item
        ><el-form-item label="每日容量（人）"
          ><el-input-number v-model="form.capacity" :min="0" :max="10000" :precision="0"
        /></el-form-item>
      </div>
      <el-form-item label="开放预约的星期"
        ><el-checkbox-group v-model="form.weekdays"
          ><el-checkbox v-for="day in weekdays" :key="day.value" :value="day.value">{{
            day.label
          }}</el-checkbox></el-checkbox-group
        ></el-form-item
      >
      <div class="schedule-config-bottom">
        <span>未勾选的星期将设置为 0 人；容量不能低于已预约数量。</span
        ><el-button type="primary" :loading="saving" @click="save">批量更新名额</el-button>
      </div></el-form
    >
  </section>
  <section class="panel">
    <div class="panel-title">
      每日名额明细<span>共 {{ rows.length }} 天</span
      ><el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-alert
      v-if="failure"
      :title="failure"
      type="error"
      :closable="false"
      show-icon
      class="table-error"
    /><el-table
      v-loading="loading"
      :data="rows"
      stripe
      empty-text="暂无排班，请选择医院和日期后批量生成"
      ><el-table-column prop="date" label="体检日期" min-width="160"
        ><template #default="{ row }"
          ><strong>{{ row.date }}</strong
          ><span class="weekday-cell">{{
            new Date(row.date + 'T00:00:00').toLocaleDateString('zh-CN', { weekday: 'short' })
          }}</span></template
        ></el-table-column
      ><el-table-column prop="capacity" label="总容量" min-width="120" /><el-table-column
        prop="used"
        label="已预约"
        min-width="120"
      /><el-table-column label="剩余名额" min-width="120"
        ><template #default="{ row }"
          ><strong :class="row.remaining > 0 ? 'text-primary' : 'muted'">{{
            row.remaining
          }}</strong></template
        ></el-table-column
      ><el-table-column label="占用情况" min-width="200"
        ><template #default="{ row }"
          ><el-progress
            :percentage="
              row.capacity ? Math.min(100, Math.round((row.used / row.capacity) * 100)) : 0
            "
            :stroke-width="6" /></template></el-table-column
      ><el-table-column label="开放状态" min-width="130"
        ><template #default="{ row }"
          ><el-tag :type="row.capacity === 0 ? 'info' : row.available ? 'success' : 'warning'">{{
            row.capacity === 0 ? '未开放' : row.available ? '可预约' : '不可预约'
          }}</el-tag></template
        ></el-table-column
      ></el-table
    >
  </section>
</template>
