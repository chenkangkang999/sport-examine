<script setup>
import { onMounted, ref, reactive } from 'vue';
import { useRoute } from 'vue-router';
import { api, post } from '../api';
import { notify, dateText, sexText, localDate } from '../state';
import Icon from '../components/Icon.vue';
import AsyncState from '../components/AsyncState.vue';
import ConfirmDialog from '../components/ConfirmDialog.vue';
const route = useRoute(),
  persons = ref([]),
  loading = ref(true),
  error = ref(''),
  editing = ref(false),
  busy = ref(false),
  formError = ref(''),
  remove = ref(null),
  form = reactive({});
async function load() {
  loading.value = true;
  error.value = '';
  try {
    persons.value = await api.persons();
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
function open(person) {
  Object.keys(form).forEach((k) => delete form[k]);
  Object.assign(
    form,
    person
      ? { ...person, identityCard: '' }
      : { realName: '', sex: 1, birthday: '', identityCard: '', relationship: '家人' },
  );
  formError.value = '';
  editing.value = true;
}
async function save() {
  busy.value = true;
  formError.value = '';
  try {
    const payload = { ...form };
    if (!payload.identityCard) delete payload.identityCard;
    await post('/persons/save', payload);
    editing.value = false;
    notify('健康档案已保存', 'success');
    await load();
  } catch (e) {
    formError.value = e.message;
  } finally {
    busy.value = false;
  }
}
async function destroy() {
  busy.value = true;
  try {
    await post('/persons/remove', { personId: remove.value.personId });
    remove.value = null;
    notify('档案已移除，历史订单仍会保留', 'success');
    await load();
  } catch (e) {
    notify(e.message);
  } finally {
    busy.value = false;
  }
}
onMounted(async () => {
  await load();
  if (route.query.add) open();
});
</script>
<template>
  <div class="prototype-persons">
    <div class="persons-toolbar">
      <p>选择档案可为本人或家人预约体检</p>
      <button class="text-button" aria-label="添加家人" @click="open()">＋ 添加家人</button>
    </div>
    <AsyncState
      :loading="loading"
      :error="error"
      :empty="!persons.length"
      message="还没有健康档案"
      @retry="load"
      ><template #action><button class="btn" @click="open()">添加家人档案</button></template>
      <div class="persons-list">
        <article v-for="person in persons" :key="person.personId" class="person-list-row">
          <div class="person-list-name">
            <strong>{{ person.realName }}</strong
            ><span>{{ person.isSelf ? '本人' : person.relationship || '家人' }}</span
            ><small>{{ sexText(person.sex) }}</small>
          </div>
          <p>生日：{{ dateText(person.birthday) }}</p>
          <p>身份证号：{{ person.identityCardMasked || '已登记' }}</p>
          <div class="person-list-actions">
            <button class="text-button" @click="open(person)">编辑档案</button
            ><button v-if="!person.isSelf" class="text-button danger-text" @click="remove = person">
              移除</button
            ><RouterLink
              :to="{ path: '/appointment', query: { personId: person.personId } }"
              class="text-button"
              >预约体检</RouterLink
            >
          </div>
        </article>
      </div></AsyncState
    >
  </div>
  <Teleport to="body"
    ><div v-if="editing" class="modal-backdrop" @click.self="!busy && (editing = false)">
      <section class="modal" role="dialog" aria-modal="true" aria-label="健康档案">
        <button
          class="icon-button modal-close"
          :disabled="busy"
          aria-label="关闭"
          @click="editing = false"
        >
          <Icon name="close" />
        </button>
        <h2>{{ form.personId ? '编辑健康档案' : '添加家人档案' }}</h2>
        <form class="form" @submit.prevent="save">
          <label
            >姓名<input
              v-model.trim="form.realName"
              required
              maxlength="20"
              placeholder="请输入体检人姓名"
          /></label>
          <div class="form-grid">
            <label
              >性别<select v-model.number="form.sex">
                <option :value="1">男</option>
                <option :value="0">女</option>
              </select></label
            ><label
              >与本人关系<select v-model="form.relationship" :disabled="Boolean(form.isSelf)">
                <option
                  v-for="name in ['本人', '父亲', '母亲', '配偶', '子女', '家人', '其他']"
                  :key="name"
                >
                  {{ name }}
                </option>
              </select></label
            >
          </div>
          <label
            >出生日期<input
              v-model="form.birthday"
              type="date"
              :max="localDate()"
              required /></label
          ><label
            >身份证号码<input
              v-model.trim="form.identityCard"
              maxlength="18"
              :required="!form.personId"
              :placeholder="form.personId ? '留空表示保留原证件号码' : '请输入 18 位身份证号码'"
            /><small v-if="form.personId" class="field-hint"
              >当前：{{ form.identityCardMasked }}，有未归档预约时不可更换证件。</small
            ></label
          >
          <p v-if="formError" class="inline-error" role="alert">{{ formError }}</p>
          <div class="actions">
            <button type="button" class="btn secondary" :disabled="busy" @click="editing = false">
              取消</button
            ><button class="btn" :disabled="busy">{{ busy ? '保存中…' : '保存档案' }}</button>
          </div>
        </form>
      </section>
    </div></Teleport
  >
  <ConfirmDialog
    :open="Boolean(remove)"
    title="移除这份家人档案？"
    text="历史订单与报告会保留。存在未归档预约的档案不能移除。"
    confirm-text="确认移除"
    :busy="busy"
    @close="remove = null"
    @confirm="destroy"
  />
</template>
