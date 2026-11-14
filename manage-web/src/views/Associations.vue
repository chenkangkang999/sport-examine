<script setup>
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { get, post, sexText, money } from '../api';
const hospitalList = ref([]),
  meals = ref([]),
  hpId = ref(''),
  selected = ref([]),
  saved = ref([]),
  loading = ref(false),
  saving = ref(false),
  failure = ref('');
const currentHospital = computed(() => hospitalList.value.find((h) => h.hpId === hpId.value));
const dirty = computed(
  () => JSON.stringify([...selected.value].sort()) !== JSON.stringify([...saved.value].sort()),
);
async function load() {
  if (!hpId.value) return;
  loading.value = true;
  failure.value = '';
  try {
    selected.value = await get('/admin/hospital-setmeals', { hpId: hpId.value });
    saved.value = [...selected.value];
  } catch (e) {
    failure.value = e.message;
  } finally {
    loading.value = false;
  }
}
async function save() {
  saving.value = true;
  try {
    await post('/admin/hospital-setmeals/save', { hpId: hpId.value, smIds: selected.value });
    saved.value = [...selected.value];
    ElMessage.success('医院套餐配置已保存');
  } catch {
  } finally {
    saving.value = false;
  }
}
onMounted(async () => {
  loading.value = true;
  try {
    [hospitalList.value, meals.value] = await Promise.all([
      get('/admin/hospitals'),
      get('/admin/setmeals'),
    ]);
    hpId.value = hospitalList.value[0]?.hpId || '';
    await load();
  } catch (e) {
    failure.value = e.message;
  } finally {
    loading.value = false;
  }
});
</script>
<template>
  <div class="page-heading">
    <div>
      <div class="eyebrow">HOSPITAL PACKAGE SETTINGS</div>
      <h1>医院套餐配置</h1>
      <p class="page-description">为每家体检医院配置可预约的套餐，保持服务信息准确。</p>
    </div>
  </div>
  <section class="panel">
    <div class="association-hospital">
      <div>
        <span class="field-label">选择体检医院</span
        ><el-select v-model="hpId" placeholder="请选择医院" @change="load"
          ><el-option
            v-for="h in hospitalList"
            :key="h.hpId"
            :label="h.name + (h.state === 1 ? '' : '（已停用）')"
            :value="h.hpId"
        /></el-select>
      </div>
      <p>{{ currentHospital?.address || '请选择要配置的医院' }}</p>
    </div>
    <el-alert
      v-if="failure"
      :title="failure"
      type="error"
      :closable="false"
      show-icon
      class="table-error"
    />
    <div class="panel-title">
      可提供的体检套餐<span>已选择 {{ selected.length }} 个套餐</span>
    </div>
    <el-checkbox-group v-model="selected" v-loading="loading" class="association-grid"
      ><div
        v-for="meal in meals"
        :key="meal.smId"
        class="package-card"
        :class="{ selected: selected.includes(meal.smId), inactive: meal.state !== 1 }"
      >
        <div class="package-card-top">
          <el-checkbox :value="meal.smId" :label="meal.name" /><el-tag
            v-if="meal.state !== 1"
            type="info"
            size="small"
            >已停用</el-tag
          >
        </div>
        <p>
          {{ Number(meal.type) === 2 ? '不限性别' : sexText(meal.type) + '士适用'
          }}<span>包含 {{ meal.ciIds?.length || 0 }} 个检查项目</span>
        </p>
        <strong>¥{{ money(meal.price) }}<small>/ 人</small></strong>
      </div></el-checkbox-group
    ><el-empty v-if="!loading && !meals.length" description="暂无套餐，请先创建体检套餐" />
    <div class="association-footer">
      <span>{{
        dirty ? '配置已修改，请保存后生效。' : '只有启用的医院及套餐才会展示给用户。'
      }}</span
      ><el-button type="primary" :loading="saving" :disabled="!hpId || loading" @click="save"
        >保存医院套餐配置</el-button
      >
    </div>
  </section>
</template>
