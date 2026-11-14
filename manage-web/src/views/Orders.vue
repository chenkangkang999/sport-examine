<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { get, session, sexText } from '../api';
const router = useRouter(),
  loading = ref(false),
  failure = ref(''),
  rows = ref([]),
  total = ref(0),
  meals = ref([]),
  hospitalList = ref([]);
// 查询表单直接对应后端分页参数；sex 空字符串表示不过滤，保留“全部”选项。
const filters = reactive({
  userId: '',
  realName: '',
  sex: '',
  smId: '',
  orderDate: '',
  state: 1,
  hpId: '',
  page: 1,
  pageSize: 10,
});
async function load() {
  loading.value = true;
  failure.value = '';
  try {
    const result = await get('/orders/listOrders', filters);
    rows.value = result.items;
    total.value = result.total;
  } catch (e) {
    failure.value = e.message;
  } finally {
    loading.value = false;
  }
}
function search() {
  // 更换筛选条件时回到第一页，避免沿用旧页码而误显示空列表。
  filters.page = 1;
  load();
}
function reset() {
  Object.assign(filters, {
    userId: '',
    realName: '',
    sex: '',
    smId: '',
    orderDate: '',
    state: 1,
    hpId: '',
    page: 1,
  });
  load();
}
onMounted(async () => {
  load();
  // 套餐和管理员医院选项独立加载，某组元数据失败不阻断另一组显示。
  const r = await Promise.allSettled([
    get('/setmeal/listSetmeal'),
    session.staff?.role === 'ADMIN' ? get('/admin/hospitals') : Promise.resolve([]),
  ]);
  if (r[0].status === 'fulfilled') meals.value = r[0].value;
  if (r[1].status === 'fulfilled') hospitalList.value = r[1].value;
});
</script>
<template>
  <div class="manual-orders-layout">
    <aside class="manual-query-sidebar">
      <h2>体检客户查询</h2>
      <el-form :model="filters" label-width="58px" size="small" @submit.prevent="search">
        <el-form-item label="手机号码"
          ><el-input
            v-model.trim="filters.userId"
            placeholder="手机号码"
            clearable
            @keyup.enter="search"
        /></el-form-item>
        <el-form-item label="姓名"
          ><el-input
            v-model.trim="filters.realName"
            placeholder="姓名"
            clearable
            @keyup.enter="search"
        /></el-form-item>
        <el-form-item label="性别"
          ><el-radio-group v-model="filters.sex" class="vertical-radios"
            ><el-radio :value="1">男</el-radio><el-radio :value="0">女</el-radio
            ><el-radio value="">全部</el-radio></el-radio-group
          ></el-form-item
        >
        <el-form-item label="套餐类型"
          ><el-select v-model="filters.smId" placeholder="套餐类型" clearable
            ><el-option
              v-for="meal in meals"
              :key="meal.smId"
              :label="meal.name"
              :value="meal.smId" /></el-select
        ></el-form-item>
        <el-form-item label="体检日期"
          ><el-date-picker
            v-model="filters.orderDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="体检日期"
            clearable
        /></el-form-item>
        <el-form-item v-if="session.staff?.role === 'ADMIN'" label="体检医院"
          ><el-select v-model="filters.hpId" clearable placeholder="全部医院"
            ><el-option
              v-for="hospital in hospitalList"
              :key="hospital.hpId"
              :label="hospital.name"
              :value="hospital.hpId" /></el-select
        ></el-form-item>
        <el-form-item label="是否归档"
          ><el-radio-group v-model="filters.state" class="vertical-radios"
            ><el-radio :value="1">未归档</el-radio><el-radio :value="2">已归档</el-radio
            ><el-radio :value="3">已取消</el-radio
            ><el-radio value="">全部</el-radio></el-radio-group
          ></el-form-item
        >
        <div class="manual-query-buttons">
          <el-button type="primary" native-type="submit" :loading="loading">查询</el-button
          ><el-button type="warning" @click="reset">重置</el-button>
        </div>
      </el-form>
    </aside>
    <section class="manual-orders-content">
      <el-alert
        v-if="failure"
        :title="failure"
        type="error"
        :closable="false"
        show-icon
        class="mb-12"
      />
      <el-table
        v-loading="loading"
        :data="rows"
        size="small"
        empty-text="暂无符合条件的预约，试试调整筛选条件"
        row-key="orderId"
        class="manual-order-table"
      >
        <el-table-column prop="orderId" label="预约编号" min-width="95" />
        <el-table-column prop="userId" label="手机号码" min-width="118" />
        <el-table-column prop="personName" label="真实姓名" min-width="96" />
        <el-table-column label="性别" width="50"
          ><template #default="{ row }">{{ sexText(row.personSex) }}</template></el-table-column
        >
        <el-table-column
          prop="setmealName"
          label="体检套餐类型"
          min-width="150"
          show-overflow-tooltip
        />
        <el-table-column
          prop="hospitalName"
          label="体检医院"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column prop="orderDate" label="体检日期" min-width="110" />
        <el-table-column label="操作" min-width="108" fixed="right"
          ><template #default="{ row }"
            ><el-button
              link
              type="primary"
              size="small"
              @click="router.push('/orders/' + row.orderId)"
              >{{ row.state === 1 ? '编辑体检报告' : '查看体检报告' }}</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <div class="manual-pagination">
        <el-pagination
          v-model:current-page="filters.page"
          :page-size="filters.pageSize"
          :total="total"
          :pager-count="5"
          background
          size="small"
          layout="prev, pager, next, total"
          @current-change="load"
        />
      </div>
    </section>
  </div>
</template>
