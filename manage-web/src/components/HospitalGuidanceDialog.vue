<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { get, post } from '../api';
const props = defineProps({ open: Boolean, hospital: Object });
const emit = defineEmits(['close', 'saved']);
const form = reactive({ preparation: '', arrivalSteps: '' });
const loading = ref(false),
  saving = ref(false),
  loaded = ref(false),
  error = ref('');
const title = computed(() => `${props.hospital?.name || '医院'} · 体检指南`);
const readOnly = computed(() => Number(props.hospital?.state) !== 1);
let request = 0;
onBeforeUnmount(() => {
  request++;
});
async function load() {
  const id = ++request;
  if (!props.open || !props.hospital?.hpId) return;
  loading.value = true;
  loaded.value = false;
  error.value = '';
  Object.assign(form, { preparation: '', arrivalSteps: '' });
  try {
    const result = await get(
      '/admin/hospital-guidance',
      { hpId: props.hospital.hpId },
      { quiet: true },
    );
    if (id !== request || !props.open) return;
    Object.assign(form, {
      preparation: result.preparation || '',
      arrivalSteps: result.arrivalSteps || '',
    });
    loaded.value = true;
  } catch (e) {
    if (id === request) error.value = e.message;
  } finally {
    if (id === request) loading.value = false;
  }
}
async function save() {
  if (!loaded.value || saving.value || readOnly.value) return;
  if (form.preparation.length > 2000 || form.arrivalSteps.length > 2000) {
    error.value = '每项内容最多 2000 字';
    return;
  }
  saving.value = true;
  error.value = '';
  try {
    await post('/admin/hospital-guidance/save', { hpId: props.hospital.hpId, ...form });
    ElMessage.success('体检指南已保存，用户可在预约成功及订单详情中查看');
    emit('saved');
    emit('close');
  } catch (e) {
    error.value = e.message;
  } finally {
    saving.value = false;
  }
}
watch(
  () => [props.open, props.hospital?.hpId],
  () => {
    if (props.open) load();
    else {
      request++;
      loading.value = false;
    }
  },
  { immediate: true },
);
</script>
<template>
  <el-dialog
    :model-value="open"
    :title="title"
    width="680px"
    class="hospital-guidance-dialog"
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    @update:model-value="(value) => !value && $emit('close')"
  >
    <p class="guidance-description">
      供用户在到院前查看。请由机构维护准确的准备事项与到院流程；没有填写时显示通用提醒。
    </p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="mb-12" />
    <el-alert
      v-if="readOnly"
      title="该医院已停用，体检指南仅供查看。"
      type="info"
      :closable="false"
      show-icon
      class="mb-12"
    />
    <el-skeleton v-if="loading" :rows="5" animated />
    <el-button v-else-if="!loaded" @click="load">重新加载指南</el-button>
    <el-form v-else label-position="top" :disabled="readOnly" @submit.prevent="save">
      <el-form-item label="体检准备事项">
        <el-input
          v-model="form.preparation"
          type="textarea"
          :rows="5"
          maxlength="2000"
          show-word-limit
          placeholder="填写本机构提供的预约凭证、证件及检查准备要求；如需个别医嘱，请提醒用户咨询医生。"
          :disabled="saving || readOnly"
        />
      </el-form-item>
      <el-form-item label="到院流程">
        <el-input
          v-model="form.arrivalSteps"
          type="textarea"
          :rows="5"
          maxlength="2000"
          show-word-limit
          placeholder="可逐行填写：到院位置、登记方式、领取指引、检查及报告查询流程。"
          :disabled="saving || readOnly"
        />
      </el-form-item>
      <p class="guidance-description">
        此处只维护指南，不改变医院基本资料、预约名额或既有订单快照。
      </p>
    </el-form>
    <template #footer
      ><el-button :disabled="saving" @click="$emit('close')">{{
        readOnly ? '关闭' : '取消'
      }}</el-button
      ><el-button
        v-if="!readOnly"
        type="primary"
        :loading="saving"
        :disabled="!loaded || loading"
        @click="save"
        >保存体检指南</el-button
      ></template
    >
  </el-dialog>
</template>
<style scoped>
.guidance-description {
  color: #7c8c9a;
  font-size: 12px;
  line-height: 1.8;
  margin: 0 0 16px;
}
</style>
