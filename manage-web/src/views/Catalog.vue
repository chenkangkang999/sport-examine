<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { get, post, sexText, money } from '../api';
const route = useRoute(),
  resource = computed(() => route.params.resource),
  rows = ref([]),
  relatedHospitals = ref([]),
  relatedChecks = ref([]),
  loading = ref(false),
  saving = ref(false),
  failure = ref(''),
  dialog = ref(false),
  formRef = ref(),
  query = ref('');
const definitions = {
  hospitals: {
    title: '医院信息',
    english: 'HOSPITAL DIRECTORY',
    description: '维护体检机构信息，为用户提供准确的就诊指引。',
    id: 'hpId',
    name: 'name',
    singular: '医院',
  },
  setmeals: {
    title: '体检套餐',
    english: 'CHECKUP PACKAGES',
    description: '组合检查项目与服务价格，让预约选择更清晰。',
    id: 'smId',
    name: 'name',
    singular: '套餐',
  },
  checkitems: {
    title: '检查项目',
    english: 'EXAMINATION CATALOG',
    description: '维护检查明细、参考范围与结果验证方式。',
    id: 'ciId',
    name: 'ciName',
    singular: '检查项目',
  },
  doctors: {
    title: '医生与账号',
    english: 'STAFF & ACCESS',
    description: '统一维护医生工号、所属医院与工作台权限。',
    id: 'docId',
    name: 'realName',
    singular: '账号',
  },
};
const config = computed(() => definitions[resource.value]);
const form = reactive({});
const visibleRows = computed(() =>
  rows.value.filter(
    (row) =>
      !query.value ||
      `${row[config.value.name]} ${row.docCode || ''}`
        .toLowerCase()
        .includes(query.value.toLowerCase()),
  ),
);
const rules = computed(() => {
  const result = {
    [config.value.name]: [
      {
        required: true,
        whitespace: true,
        message: `请输入${resource.value === 'doctors' ? '姓名' : config.value.singular + '名称'}`,
        trigger: 'blur',
      },
    ],
  };
  if (resource.value === 'doctors') {
    result.docCode = [{ required: true, message: '请输入工号', trigger: 'blur' }];
    if (!form.docId)
      result.password = [{ required: true, min: 8, message: '新账号密码至少8位', trigger: 'blur' }];
    if (form.role === 'DOCTOR')
      result.hpId = [{ required: true, message: '请选择所属医院', trigger: 'change' }];
  }
  if (resource.value === 'checkitems')
    result.ciContent = [{ required: true, message: '请输入检查内容', trigger: 'blur' }];
  return result;
});
async function load() {
  loading.value = true;
  failure.value = '';
  try {
    rows.value = await get(`/admin/${resource.value}`);
  } catch (e) {
    failure.value = e.message;
  } finally {
    loading.value = false;
  }
}
async function loadRelated() {
  const results = await Promise.allSettled([get('/admin/hospitals'), get('/admin/checkitems')]);
  if (results[0].status === 'fulfilled') relatedHospitals.value = results[0].value;
  if (results[1].status === 'fulfilled') relatedChecks.value = results[1].value;
}
function defaultDetail() {
  return {
    name: '',
    unit: '',
    minrange: null,
    maxrange: null,
    normalValue: null,
    normalValueString: '',
    type: 1,
    required: true,
  };
}
function openEdit(row) {
  Object.keys(form).forEach((key) => delete form[key]);
  const defaults =
    resource.value === 'hospitals'
      ? {
          name: '',
          picture: '',
          telephone: '',
          address: '',
          businessHours: '周一至周五 07:30-15:30',
          deadline: '采血截止 10:30',
          state: 1,
        }
      : resource.value === 'setmeals'
        ? { name: '', type: 2, price: 0, state: 1, ciIds: [] }
        : resource.value === 'checkitems'
          ? {
              ciName: '',
              ciContent: '',
              meaning: '',
              remarks: '',
              state: 1,
              details: [defaultDetail()],
            }
          : {
              docCode: '',
              realName: '',
              password: '',
              sex: 1,
              deptno: 1,
              hpId: '',
              role: 'DOCTOR',
              state: 1,
            };
  Object.assign(form, defaults, row ? JSON.parse(JSON.stringify(row)) : {});
  if (resource.value === 'doctors') form.password = '';
  if (resource.value === 'checkitems') form.details = form.details || [];
  dialog.value = true;
}
async function save() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  if (resource.value === 'setmeals' && !form.ciIds.length) {
    ElMessage.warning('请至少选择一个检查项目');
    return;
  }
  if (
    resource.value === 'checkitems' &&
    (!form.details.length || form.details.some((d) => !d.name?.trim()))
  ) {
    ElMessage.warning('请至少添加一条明细，并填写每条明细名称');
    return;
  }
  if (
    resource.value === 'checkitems' &&
    form.details.some(
      (d) =>
        d.type === 1 &&
        ((d.minrange == null && d.maxrange == null) ||
          (d.minrange != null && d.maxrange != null && Number(d.minrange) > Number(d.maxrange))),
    )
  ) {
    ElMessage.warning('范围验证项目至少需填写一个边界，下限不能大于上限');
    return;
  }
  if (
    resource.value === 'checkitems' &&
    form.details.some(
      (d) => d.type === 2 && (d.normalValue == null || !Number.isFinite(Number(d.normalValue))),
    )
  ) {
    ElMessage.warning('等值验证项目需填写有效的正常值');
    return;
  }
  saving.value = true;
  try {
    await post(`/admin/${resource.value}/save`, form);
    dialog.value = false;
    ElMessage.success('资料已保存，历史订单与报告快照不受影响');
    await load();
    await loadRelated();
  } catch {
  } finally {
    saving.value = false;
  }
}
async function disable(row) {
  try {
    await ElMessageBox.confirm(
      `停用“${row[config.value.name]}”？相关历史订单与报告将保留。`,
      '停用资料',
      { type: 'warning', confirmButtonText: '确认停用', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  try {
    await post(`/admin/${resource.value}/disable`, { id: row[config.value.id] });
    ElMessage.success('已停用');
    await load();
  } catch {}
}
function hospitalName(hpId) {
  return relatedHospitals.value.find((h) => h.hpId === hpId)?.name || '—';
}
onMounted(() => {
  load();
  loadRelated();
});
</script>
<template>
  <div class="page-heading">
    <div>
      <div class="eyebrow">{{ config.english }}</div>
      <h1>{{ config.title }}</h1>
      <p class="page-description">{{ config.description }}</p>
    </div>
    <el-button type="primary" @click="openEdit()">＋ 新增{{ config.singular }}</el-button>
  </div>
  <section class="panel">
    <div class="catalog-toolbar">
      <div>
        <strong>{{ config.title }}目录</strong
        ><el-tag effect="plain" size="small">{{ rows.length }} 条资料</el-tag>
      </div>
      <div>
        <el-input
          v-model="query"
          clearable
          :placeholder="resource === 'doctors' ? '搜索姓名或工号' : '搜索名称'"
          class="catalog-search"
        /><el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>
    <el-alert
      v-if="failure"
      :title="failure"
      type="error"
      :closable="false"
      show-icon
      class="table-error"
    />
    <el-table v-loading="loading" :data="visibleRows" stripe empty-text="暂无资料，请点击右上角新增"
      ><el-table-column :prop="config.id" label="编号" width="80" /><el-table-column
        :prop="config.name"
        :label="resource === 'doctors' ? '姓名' : '名称'"
        min-width="180"
        ><template #default="{ row }"
          ><strong>{{ row[config.name] }}</strong>
          <div v-if="resource === 'doctors'" class="cell-sub">{{ row.docCode }}</div></template
        ></el-table-column
      >
      <template v-if="resource === 'hospitals'"
        ><el-table-column prop="telephone" label="联系电话" min-width="135" /><el-table-column
          prop="address"
          label="医院地址"
          min-width="220"
          show-overflow-tooltip /><el-table-column
          prop="businessHours"
          label="营业时间"
          min-width="190"
      /></template>
      <template v-if="resource === 'setmeals'"
        ><el-table-column label="适用性别" width="100"
          ><template #default="{ row }">{{ sexText(row.type) }}</template></el-table-column
        ><el-table-column label="套餐价格" width="130"
          ><template #default="{ row }"
            ><span class="price">¥{{ money(row.price) }}</span></template
          ></el-table-column
        ><el-table-column label="检查项目" min-width="220"
          ><template #default="{ row }"
            ><span class="muted">{{
              (row.ciIds || [])
                .map((id) => relatedChecks.find((c) => c.ciId === id)?.ciName || '#' + id)
                .join('、') || '未配置'
            }}</span></template
          ></el-table-column
        ></template
      >
      <template v-if="resource === 'checkitems'"
        ><el-table-column
          prop="ciContent"
          label="检查内容"
          min-width="210"
          show-overflow-tooltip
        /><el-table-column label="结果明细" min-width="170"
          ><template #default="{ row }"
            >{{ row.details?.length || 0 }} 项<span class="cell-sub">{{
              row.details?.map((d) => d.name).join('、')
            }}</span></template
          ></el-table-column
        ></template
      >
      <template v-if="resource === 'doctors'"
        ><el-table-column label="角色" min-width="120"
          ><template #default="{ row }"
            ><el-tag :type="row.role === 'ADMIN' ? 'warning' : 'info'">{{
              row.role === 'ADMIN' ? '管理员' : '体检医生'
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="所属医院" min-width="200"
          ><template #default="{ row }">{{
            row.role === 'ADMIN' ? '全部医院' : hospitalName(row.hpId)
          }}</template></el-table-column
        ><el-table-column label="科室" width="100"
          ><template #default="{ row }">{{
            { 1: '检验科', 2: '内科', 3: '外科' }[row.deptno] || '—'
          }}</template></el-table-column
        ></template
      >
      <el-table-column label="状态" width="100"
        ><template #default="{ row }"
          ><el-tag :type="row.state === 1 ? 'success' : 'info'" effect="light">{{
            row.state === 1 ? '启用' : '已停用'
          }}</el-tag></template
        ></el-table-column
      ><el-table-column label="操作" min-width="135" fixed="right"
        ><template #default="{ row }"
          ><el-button type="primary" link @click="openEdit(row)">编辑</el-button
          ><el-button v-if="row.state === 1" type="danger" link @click="disable(row)"
            >停用</el-button
          ></template
        ></el-table-column
      ></el-table
    >
    <div class="catalog-footnote">资料调整仅影响后续预约；已创建订单与历史报告保留原始快照。</div>
  </section>
  <el-dialog
    v-model="dialog"
    :title="`${form[config.id] ? '编辑' : '新增'}${config.singular}`"
    :width="resource === 'checkitems' ? '1000px' : '640px'"
    :close-on-click-modal="false"
    destroy-on-close
    ><el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="editor-form">
      <template v-if="resource === 'hospitals'"
        ><el-form-item label="医院名称" prop="name"
          ><el-input v-model.trim="form.name" maxlength="80"
        /></el-form-item>
        <div class="form-two-columns">
          <el-form-item label="联系电话"
            ><el-input v-model.trim="form.telephone" maxlength="30" /></el-form-item
          ><el-form-item label="营业时间"
            ><el-input v-model.trim="form.businessHours"
          /></el-form-item>
        </div>
        <el-form-item label="医院地址"
          ><el-input v-model.trim="form.address" maxlength="200" /></el-form-item
        ><el-form-item label="采血截止及到院提醒"
          ><el-input v-model.trim="form.deadline" /></el-form-item
        ><el-form-item label="图片路径（可选）"
          ><el-input v-model.trim="form.picture" placeholder="如 /img/hospita1.png" /></el-form-item
      ></template>
      <template v-if="resource === 'setmeals'"
        ><el-form-item label="套餐名称" prop="name"
          ><el-input v-model.trim="form.name" maxlength="80"
        /></el-form-item>
        <div class="form-two-columns">
          <el-form-item label="适用性别"
            ><el-select v-model="form.type"
              ><el-option label="男士" :value="1" /><el-option label="女士" :value="0" /><el-option
                label="不限"
                :value="2" /></el-select></el-form-item
          ><el-form-item label="套餐价格（元）"
            ><el-input-number
              v-model="form.price"
              :min="0.01"
              :max="999999"
              :precision="2"
              :step="10"
          /></el-form-item>
        </div>
        <el-form-item label="包含检查项目"
          ><el-select v-model="form.ciIds" multiple filterable placeholder="选择一个或多个检查项目"
            ><el-option
              v-for="item in relatedChecks"
              :key="item.ciId"
              :label="item.ciName + (item.state === 1 ? '' : '（已停用）')"
              :value="item.ciId" /></el-select></el-form-item
        ><el-alert
          title="套餐创建后，还需在“医院套餐配置”中关联医院，用户才能预约。"
          type="info"
          :closable="false"
          show-icon
          class="mb-20"
      /></template>
      <template v-if="resource === 'doctors'"
        ><div class="form-two-columns">
          <el-form-item label="工号 / 登录账号" prop="docCode"
            ><el-input
              v-model.trim="form.docCode"
              maxlength="20"
              autocomplete="off" /></el-form-item
          ><el-form-item label="姓名" prop="realName"
            ><el-input v-model.trim="form.realName" maxlength="20"
          /></el-form-item>
        </div>
        <el-form-item
          :label="form.docId ? '登录密码（留空保持原密码）' : '初始登录密码'"
          prop="password"
          ><el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="至少8位，建议同时包含字母与数字"
        /></el-form-item>
        <div class="form-two-columns">
          <el-form-item label="角色"
            ><el-select v-model="form.role"
              ><el-option label="体检医生" value="DOCTOR" /><el-option
                label="系统管理员"
                value="ADMIN" /></el-select></el-form-item
          ><el-form-item label="性别"
            ><el-radio-group v-model="form.sex"
              ><el-radio :value="1">男</el-radio><el-radio :value="0">女</el-radio></el-radio-group
            ></el-form-item
          >
        </div>
        <div class="form-two-columns">
          <el-form-item v-if="form.role === 'DOCTOR'" label="所属医院" prop="hpId"
            ><el-select v-model="form.hpId" placeholder="请选择医院"
              ><el-option
                v-for="h in relatedHospitals"
                :key="h.hpId"
                :label="h.name"
                :value="h.hpId" /></el-select></el-form-item
          ><el-form-item label="科室"
            ><el-select v-model="form.deptno"
              ><el-option label="检验科" :value="1" /><el-option
                label="内科"
                :value="2" /><el-option label="外科" :value="3" /></el-select
          ></el-form-item></div
      ></template>
      <template v-if="resource === 'checkitems'"
        ><div class="form-two-columns">
          <el-form-item label="检查项目名称" prop="ciName"
            ><el-input v-model.trim="form.ciName" maxlength="50" /></el-form-item
          ><el-form-item label="检查内容" prop="ciContent"
            ><el-input v-model.trim="form.ciContent" maxlength="100"
          /></el-form-item>
        </div>
        <div class="form-two-columns">
          <el-form-item label="检查意义"
            ><el-input v-model.trim="form.meaning" maxlength="300" /></el-form-item
          ><el-form-item label="备注"
            ><el-input v-model.trim="form.remarks" maxlength="300"
          /></el-form-item>
        </div>
        <div class="detail-editor-title">
          <strong>结果明细配置</strong
          ><el-button type="primary" plain size="small" @click="form.details.push(defaultDetail())"
            >＋ 添加明细</el-button
          >
        </div>
        <div v-for="(detail, index) in form.details" :key="index" class="detail-editor">
          <div class="detail-editor-row">
            <div class="detail-editor-number">{{ index + 1 }}</div>
            <el-input
              v-model.trim="detail.name"
              placeholder="明细名称"
              aria-label="明细名称"
            /><el-select v-model="detail.type" aria-label="验证类型"
              ><el-option label="数值范围" :value="1" /><el-option
                label="数值等值"
                :value="2" /><el-option label="无需验证" :value="3" /><el-option
                label="描述型"
                :value="4" /><el-option label="其他" :value="5" /></el-select
            ><el-input
              v-model.trim="detail.unit"
              placeholder="单位（可空）"
              aria-label="单位"
            /><el-checkbox v-model="detail.required">必填</el-checkbox
            ><el-button type="danger" link @click="form.details.splice(index, 1)">移除</el-button>
          </div>
          <div class="detail-ranges">
            <template v-if="detail.type === 1"
              ><span>正常范围</span
              ><el-input-number
                v-model="detail.minrange"
                :controls="false"
                placeholder="最小值" /><span>至</span
              ><el-input-number
                v-model="detail.maxrange"
                :controls="false"
                placeholder="最大值" /></template
            ><template v-else-if="detail.type === 2"
              ><span>正常值</span
              ><el-input-number
                v-model="detail.normalValue"
                :controls="false"
                placeholder="数值" /></template
            ><template v-else
              ><span>参考描述</span
              ><el-input v-model.trim="detail.normalValueString" placeholder="如：未见异常"
            /></template>
          </div></div
      ></template>
      <el-form-item label="启用状态"
        ><el-radio-group v-model="form.state"
          ><el-radio :value="1">启用</el-radio><el-radio :value="2">停用</el-radio></el-radio-group
        ></el-form-item
      > </el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save">保存资料</el-button></template
    ></el-dialog
  >
</template>
