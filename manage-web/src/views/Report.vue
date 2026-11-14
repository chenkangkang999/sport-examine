<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { get, post, stateText, sexText, reference, money } from '../api';
const route = useRoute(),
  router = useRouter(),
  loading = ref(false),
  failure = ref(''),
  saving = ref(''),
  report = ref(null),
  conclusionDialog = ref(false),
  conclusionFormRef = ref();
const conclusion = reactive({ orId: null, title: '', content: '' });
// 基线记录各检查分项上次保存的值，用于识别未保存改动；归档或取消后页面只读。
const baselines = ref({});
const readOnly = computed(() => report.value?.order.state !== 1);
const dirty = computed(() =>
  report.value?.items.some(
    (group) =>
      JSON.stringify(
        group.details.map((d) => [d.cidrId, d.value ?? '', Number(d.isError || 0)]),
      ) !== baselines.value[group.cirId],
  ),
);
const resultCount = computed(() => {
  const details = report.value?.items.flatMap((i) => i.details) || [];
  return {
    total: details.length,
    done: details.filter((d) => String(d.value ?? '').trim() !== '').length,
    abnormal: details.filter((d) => Number(d.isError) === 1).length,
  };
});
function snapshot(group) {
  return JSON.stringify(
    group.details.map((d) => [d.cidrId, d.value ?? '', Number(d.isError || 0)]),
  );
}
// 接收后端最新报告和版本；分项保存时保留其他分项尚未保存的本地填写内容。
function adopt(value, savedGroup = null) {
  if (report.value && savedGroup !== 'all')
    value.items.forEach((group) => {
      const old = report.value.items.find((i) => i.cirId === group.cirId);
      if (old && group.cirId !== savedGroup && snapshot(old) !== baselines.value[old.cirId])
        group.details = old.details;
    });
  report.value = value;
  value.items.forEach((group) => {
    if (!(group.cirId in baselines.value) || savedGroup === 'all' || group.cirId === savedGroup)
      baselines.value[group.cirId] = snapshot(group);
  });
}
async function load() {
  loading.value = true;
  failure.value = '';
  try {
    adopt(await get('/ciReport/listCiReport', { orderId: route.params.id }), 'all');
  } catch (e) {
    failure.value = e.message;
  } finally {
    loading.value = false;
  }
}
async function handleError(error) {
  // 别的医生或浏览器已先保存时返回 409，不能用旧版本静默覆盖新报告。
  if (error.response?.status !== 409) return;
  try {
    await ElMessageBox.confirm(
      `${error.message}。请重新加载最新报告后再编辑，尚未保存的内容会被清除。`,
      '报告版本已变化',
      { type: 'warning', confirmButtonText: '加载最新报告', cancelButtonText: '保留当前填写' },
    );
    await load();
  } catch {}
}
function numericValid(details) {
  return details.every(
    (d) =>
      !String(d.value ?? '').trim() ||
      ![1, 2].includes(Number(d.type)) ||
      Number.isFinite(Number(d.value)),
  );
}
async function saveGroup(group) {
  if (!numericValid(group.details)) {
    ElMessage.warning('数值类型的检查结果必须填写有效数字');
    return;
  }
  saving.value = String(group.cirId);
  try {
    // 每次写入携带 reportVersion；数值异常最终由服务器参考范围计算。
    const value = await post('/ciDetailedReport/updateCiDetailedReport', {
      orderId: report.value.order.orderId,
      version: report.value.order.reportVersion,
      items: group.details.map((d) => ({
        cidrId: d.cidrId,
        value: String(d.value ?? '').trim(),
        isError: Number(d.isError || 0),
      })),
    });
    adopt(value, group.cirId);
    ElMessage.success(`${group.ciName}已保存，数值异常已由服务器核对`);
  } catch (e) {
    await handleError(e);
  } finally {
    saving.value = '';
  }
}
function editConclusion(value) {
  Object.assign(conclusion, {
    orId: value?.orId || null,
    title: value?.title || '',
    content: value?.content || '',
  });
  conclusionDialog.value = true;
  document
    .getElementById('conclusion-form')
    ?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}
function clearConclusion() {
  Object.assign(conclusion, { orId: null, title: '', content: '' });
  conclusionFormRef.value?.clearValidate();
}
async function saveConclusion() {
  if (!(await conclusionFormRef.value.validate().catch(() => false))) return;
  saving.value = 'conclusion';
  try {
    adopt(
      await post('/overallResult/saveOverallResult', {
        orderId: report.value.order.orderId,
        version: report.value.order.reportVersion,
        ...conclusion,
      }),
    );
    conclusionDialog.value = false;
    clearConclusion();
    ElMessage.success('总检结论已保存');
  } catch (e) {
    await handleError(e);
  } finally {
    saving.value = '';
  }
}
async function removeConclusion(value) {
  try {
    await ElMessageBox.confirm(`删除总检结论“${value.title}”？`, '删除结论', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  saving.value = 'conclusion';
  try {
    adopt(
      await post('/overallResult/removeOverallResult', {
        orderId: report.value.order.orderId,
        version: report.value.order.reportVersion,
        orId: value.orId,
      }),
    );
    ElMessage.success('结论已删除');
  } catch (e) {
    await handleError(e);
  } finally {
    saving.value = '';
  }
}
async function archive() {
  // 前端先提示缺项和未保存内容，后端仍会在事务中核验结果、结论、状态与版本。
  if (dirty.value) {
    ElMessage.warning('请先保存所有已修改的检查项目，再归档报告');
    return;
  }
  if (
    report.value.items.some((group) =>
      group.details.some(
        (detail) => detail.required !== false && !String(detail.value ?? '').trim(),
      ),
    ) ||
    !report.value.conclusions.length
  ) {
    ElMessage.warning('请填写并保存全部必填检查结果及至少一条总检结论');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '归档后报告将同步至用户端，所有检查结果与总检结论将只读。请确认已完成核对。',
      '确认归档报告',
      { type: 'warning', confirmButtonText: '确认归档', cancelButtonText: '继续核对' },
    );
  } catch {
    return;
  }
  saving.value = 'archive';
  try {
    adopt(
      await post('/orders/updateOrdersState', {
        orderId: report.value.order.orderId,
        version: report.value.order.reportVersion,
      }),
      'all',
    );
    ElMessage.success('报告已归档，用户现在可以查看');
  } catch (e) {
    await handleError(e);
  } finally {
    saving.value = '';
  }
}
async function reload() {
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('重新加载将清除未保存内容，确认继续？', '重新加载', {
        type: 'warning',
        confirmButtonText: '重新加载',
        cancelButtonText: '取消',
      });
    } catch {
      return;
    }
  }
  await load();
}
// 离开页面前提醒分项中尚未保存的编辑，避免切换预约时丢失填写结果。
onBeforeRouteLeave(async () => {
  if (!dirty.value) return true;
  try {
    await ElMessageBox.confirm('当前有未保存的检查结果，离开将丢失这些修改。', '离开报告', {
      confirmButtonText: '离开',
      cancelButtonText: '继续编辑',
      type: 'warning',
    });
    return true;
  } catch {
    return false;
  }
});
onMounted(load);
</script>
<template>
  <div v-if="loading && !report" class="manual-loading"><el-skeleton :rows="8" animated /></div>
  <el-result v-else-if="failure && !report" icon="error" title="报告加载失败" :sub-title="failure"
    ><template #extra><el-button type="primary" @click="load">重试</el-button></template></el-result
  >
  <div v-if="report" class="manual-report-layout">
    <aside class="manual-query-sidebar manual-patient-sidebar">
      <h2>预约客户信息</h2>
      <table class="manual-patient-table">
        <tbody>
          <tr>
            <th>预约编号</th>
            <td>{{ report.order.orderId }}</td>
          </tr>
          <tr>
            <th>手机号码</th>
            <td>{{ report.order.userId }}</td>
          </tr>
          <tr>
            <th>真实姓名</th>
            <td>{{ report.order.personName }}</td>
          </tr>
          <tr>
            <th>性别</th>
            <td>{{ sexText(report.order.personSex) }}</td>
          </tr>
          <tr>
            <th>套餐类型</th>
            <td>{{ report.order.setmealName }}</td>
          </tr>
          <tr>
            <th>体检日期</th>
            <td>{{ report.order.orderDate }}</td>
          </tr>
        </tbody>
      </table>
      <el-button type="primary" size="small" @click="router.push('/orders')"
        >查询体检用户</el-button
      >
      <p class="manual-patient-extra">体检医院<br />{{ report.order.hospitalName }}</p>
      <p class="manual-patient-extra">证件号码<br />{{ report.order.personIdentityMasked }}</p>
    </aside>
    <section class="manual-report-content">
      <div class="manual-report-status">
        <span
          >{{ stateText(report.order.state) }} · {{ resultCount.done }}/{{
            resultCount.total
          }}
          项已填写<span v-if="dirty" class="unsaved-note">　有未保存修改</span></span
        >
        <div>
          <span>版本 {{ report.order.reportVersion }}</span
          ><el-button link type="primary" :loading="loading" @click="reload">重新加载</el-button>
        </div>
      </div>
      <el-alert
        v-if="report.order.state === 2"
        title="此报告已归档，内容为只读，用户端可查看。"
        type="success"
        show-icon
        :closable="false"
        class="mb-12"
      />
      <el-alert
        v-else-if="report.order.state === 3"
        title="此预约已取消，无法录入或归档报告。"
        type="info"
        show-icon
        :closable="false"
        class="mb-12"
      />
      <el-alert
        v-if="failure"
        :title="failure"
        type="error"
        show-icon
        :closable="false"
        class="mb-12"
      />
      <details v-for="group in report.items" :key="group.cirId" class="manual-check-group" open>
        <summary>
          {{ group.ciName
          }}<span v-if="snapshot(group) !== baselines[group.cirId]" class="unsaved-note"
            >（未保存）</span
          >
        </summary>
        <div class="manual-check-fields">
          <div
            v-for="detail in group.details"
            :key="detail.cidrId"
            class="manual-check-field"
            :class="{
              abnormal: Number(detail.isError) === 1,
              descriptive: Number(detail.type) >= 4,
            }"
          >
            <span v-if="Number(detail.isError) === 1" class="manual-abnormal-mark" title="异常结果"
              >异</span
            >
            <label :for="'result-' + detail.cidrId">{{ detail.name }}</label>
            <el-input
              :id="'result-' + detail.cidrId"
              v-model="detail.value"
              :disabled="readOnly || !!saving"
              :type="Number(detail.type) >= 4 ? 'textarea' : 'text'"
              :rows="2"
              :maxlength="500"
              :placeholder="detail.name"
              :aria-label="detail.name + '检查结果'"
              size="small"
            />
            <span v-if="detail.unit" class="manual-result-unit">{{ detail.unit }}</span>
            <span class="manual-normal-range">正常值范围: {{ reference(detail) }}</span>
            <el-checkbox
              v-if="Number(detail.type) >= 3"
              v-model="detail.isError"
              :true-value="1"
              :false-value="0"
              :disabled="readOnly || !!saving"
              size="small"
              :aria-label="detail.name + '异常标记'"
              >异常</el-checkbox
            >
          </div>
        </div>
        <div v-if="!readOnly" class="manual-group-save">
          <el-button
            type="primary"
            size="small"
            :loading="saving === String(group.cirId)"
            :disabled="!!saving"
            @click="saveGroup(group)"
            >{{ group.ciName }}项保存</el-button
          >
        </div>
      </details>
      <el-empty v-if="!report.items.length" description="暂无检查项目，请联系管理员核对套餐配置" />
      <section class="manual-conclusions">
        <div class="manual-conclusion-header">
          <span>总检结论</span
          ><el-button
            v-if="!readOnly"
            type="danger"
            size="small"
            :loading="saving === 'archive'"
            :disabled="!!saving && saving !== 'archive'"
            @click="archive"
            >体检套餐总检结果报告归档</el-button
          >
        </div>
        <el-table :data="report.conclusions" size="small" empty-text="暂无总检结论，请在下方填写">
          <el-table-column label="编号" width="50"
            ><template #default="{ $index }">{{ $index + 1 }}</template></el-table-column
          >
          <el-table-column prop="title" label="总检结论项标题" min-width="150" />
          <el-table-column prop="content" label="总检结论项内容" min-width="300" />
          <el-table-column v-if="!readOnly" label="操作" width="100"
            ><template #default="{ row }"
              ><el-button link type="primary" :disabled="!!saving" @click="editConclusion(row)"
                >更新</el-button
              ><el-button link type="primary" :disabled="!!saving" @click="removeConclusion(row)"
                >删除</el-button
              ></template
            ></el-table-column
          >
        </el-table>
        <el-form
          v-if="!readOnly"
          id="conclusion-form"
          ref="conclusionFormRef"
          :model="conclusion"
          label-width="115px"
          size="small"
          class="manual-conclusion-form"
          :rules="{
            title: [
              { required: true, whitespace: true, message: '请输入结论标题', trigger: 'blur' },
            ],
            content: [
              { required: true, whitespace: true, message: '请输入总检结论内容', trigger: 'blur' },
            ],
          }"
          @submit.prevent="saveConclusion"
        >
          <el-form-item label="总检结论项标题" prop="title"
            ><el-input v-model.trim="conclusion.title" maxlength="40" placeholder="总检结论项标题"
          /></el-form-item>
          <el-form-item label="总检结论项内容" prop="content"
            ><el-input
              v-model.trim="conclusion.content"
              type="textarea"
              :rows="2"
              maxlength="400"
              placeholder="总检结论项内容"
          /></el-form-item>
          <el-form-item
            ><el-button
              type="primary"
              native-type="submit"
              :loading="saving === 'conclusion'"
              :disabled="!!saving && saving !== 'conclusion'"
              >{{ conclusion.orId ? '更新' : '添加' }}</el-button
            ><el-button type="warning" @click="clearConclusion">清空</el-button></el-form-item
          >
        </el-form>
      </section>
      <div class="manual-report-footnote">
        数值结果保存后自动判断异常；描述项目由医生标记。归档前请核对所有结果与总检结论。
      </div>
    </section>
  </div>
</template>
