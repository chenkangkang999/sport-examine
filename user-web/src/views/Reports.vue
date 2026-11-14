<script setup>
import { onMounted, ref, watch } from 'vue';
import { api } from '../api';
import { dateText, notify } from '../state';
import Icon from '../components/Icon.vue';
import AsyncState from '../components/AsyncState.vue';
const loading = ref(true),
  error = ref(''),
  reports = ref([]),
  persons = ref([]),
  personId = ref('');
async function load() {
  loading.value = true;
  error.value = '';
  try {
    reports.value = await api.reports({ personId: personId.value || undefined });
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
watch(personId, load);
onMounted(async () => {
  await load();
  try {
    persons.value = await api.persons();
  } catch (e) {
    notify(e.message);
  }
});
</script>
<template>
  <section class="health-records">
    <img class="report-banner" :src="'./img/report.png'" alt="免费解读体检报告" />
    <div class="report-filter">
      <label
        >体检人<select v-model="personId" aria-label="选择报告体检人">
          <option value="">全部家人</option>
          <option v-for="person in persons" :key="person.personId" :value="person.personId">
            {{ person.realName }}
          </option>
        </select></label
      ><small>已归档 {{ reports.length }} 份</small>
    </div>
    <AsyncState
      :loading="loading"
      :error="error"
      :empty="!reports.length"
      message="暂无体检报告"
      @retry="load"
      ><template #hint>体检完成并由医生归档后，即可在这里查看报告。</template
      ><template #action
        ><RouterLink to="/appointmentlist" class="btn secondary">查看我的预约</RouterLink></template
      >
      <div class="report-list">
        <RouterLink
          v-for="report in reports"
          :key="report.orderId"
          :to="`/report/${report.orderId}`"
          class="report-card"
          ><Icon name="report" :size="30" class="report-line-icon" />
          <div class="report-row-copy">
            <h2>{{ dateText(report.orderDate) }} 体检报告</h2>
            <p>{{ report.hospitalName }}</p>
            <small>{{ report.personName }} · {{ report.setmealName }}</small>
          </div>
          <Icon name="arrow" :size="22" class="report-arrow"
        /></RouterLink>
      </div>
    </AsyncState>
  </section>
</template>
<style scoped>
.health-records {
  min-height: calc(100dvh - 124px);
  color: #555;
}
.report-banner {
  width: 100%;
  height: auto;
  display: block;
}
.report-filter {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 10px 7% 0;
  font-size: 11px;
  color: #999;
  gap: 10px;
}
.report-filter label {
  display: flex;
  align-items: center;
  gap: 7px;
}
.report-filter select {
  font-size: 11px;
  color: #777;
  background: #fff;
  border: 1px solid #e7e7e7;
  padding: 3px 5px;
  min-height: 27px;
  border-radius: 2px;
  max-width: 120px;
}
.report-filter > small {
  font-size: 10px;
}
.report-list {
  display: block;
  margin: 0 7%;
  padding: 0;
}
.report-card {
  display: flex;
  align-items: center;
  gap: 11px;
  border: 0;
  border-bottom: 1px solid #eee;
  background: transparent;
  border-radius: 0;
  box-shadow: none;
  min-height: 81px;
  padding: 13px 0;
  margin: 0;
}
.report-line-icon {
  flex-shrink: 0;
  color: #6bb9b6;
}
.report-row-copy {
  min-width: 0;
  flex: 1;
}
.report-row-copy h2 {
  font-size: 14px;
  font-weight: 600;
  color: #555;
  line-height: 1.5;
  margin: 0;
}
.report-row-copy p {
  font-size: 11px;
  color: #999;
  font-weight: 500;
  line-height: 1.6;
  margin: 3px 0 0;
}
.report-row-copy small {
  font-size: 10px;
  color: #aaa;
  line-height: 1.5;
  display: block;
  margin-top: 1px;
}
.report-arrow {
  color: #aaa;
  flex-shrink: 0;
}
</style>
