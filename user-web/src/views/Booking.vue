<script setup>
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api, post } from '../api';
import {
  state,
  draft,
  setDraft,
  clearDraft,
  notify,
  sexText,
  dateText,
  money,
  localDate,
} from '../state';
import Icon from '../components/Icon.vue';
import AsyncState from '../components/AsyncState.vue';
const route = useRoute(),
  router = useRouter();
// 预约流程：体检人 → 医院 → 套餐 → 日期 → 确认，草稿在各路由之间共享。
const step = computed(() => route.meta.step),
  paths = ['/appointment', '/hospital', '/setmeal', '/selectdate', '/confirmorder'],
  bookingFor = ref(route.query.family ? 'family' : '');
const persons = ref([]),
  hospitals = ref([]),
  meals = ref([]),
  hospital = ref(null),
  meal = ref(null),
  calendar = ref([]),
  loading = ref(false),
  error = ref(''),
  submitting = ref(false),
  expanded = ref(null),
  details = ref({}),
  agreed = ref(false);
const month = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
const person = computed(() => persons.value.find((p) => p.personId === Number(draft.personId)));
const shownPersons = computed(() =>
  persons.value.filter((p) => (bookingFor.value === 'self' ? p.isSelf : !p.isSelf)),
);
const selectedHospital = computed(
  () => hospital.value || hospitals.value.find((h) => h.hpId === Number(draft.hpId)),
);
const monthLabel = computed(() => `${month.value.getFullYear()}年${month.value.getMonth() + 1}月`);
// 前端补齐月历格子；能否预约、剩余数量均以医院排班接口为准，缺失日期默认禁用。
const days = computed(() => {
  const y = month.value.getFullYear(),
    m = month.value.getMonth(),
    first = new Date(y, m, 1).getDay(),
    count = new Date(y, m + 1, 0).getDate(),
    data = new Map(calendar.value.map((d) => [d.date, d]));
  return [
    ...Array(first).fill(null),
    ...Array.from({ length: count }, (_, i) => {
      const date = localDate(new Date(y, m, i + 1));
      return { day: i + 1, date, ...(data.get(date) || { available: false, remaining: 0 }) };
    }),
  ];
});
const prevDisabled = computed(
  () =>
    month.value.getFullYear() * 12 + month.value.getMonth() <=
    new Date().getFullYear() * 12 + new Date().getMonth(),
);
const nextDisabled = computed(
  () => month.value > new Date(new Date().getFullYear(), new Date().getMonth() + 1, 1),
);
let loadId = 0;
async function load() {
  // 快速切换步骤时丢弃旧请求返回的数据，并拦截缺少前置选择的直接跳转。
  const id = ++loadId;
  loading.value = true;
  error.value = '';
  agreed.value = false;
  if (
    (step.value > 0 && !draft.personId) ||
    (step.value > 1 && !draft.hpId) ||
    (step.value > 2 && !draft.smId) ||
    (step.value > 3 && !draft.orderDate)
  ) {
    notify('请先完成前面的预约步骤');
    router.replace('/appointment');
    loading.value = false;
    return;
  }
  try {
    const list = await api.persons();
    if (id !== loadId) return;
    persons.value = list;
    if (step.value > 0 && !person.value) {
      notify('体检人档案已变更，请重新选择');
      clearDraft();
      router.replace('/appointment');
      return;
    }
    if (step.value === 1) hospitals.value = await api.hospitals();
    if (step.value >= 2) {
      const [h, m] = await Promise.all([
        api.hospital(draft.hpId),
        api.meals({ hpId: draft.hpId, type: person.value.sex }),
      ]);
      if (id !== loadId) return;
      hospital.value = h;
      meals.value = m;
    }
    if (step.value >= 3) {
      meal.value = await api.meal({ smId: draft.smId, hpId: draft.hpId });
      if (id !== loadId) return;
    }
    if (step.value === 3) {
      if (draft.orderDate) {
        const d = new Date(draft.orderDate + 'T00:00:00');
        month.value = new Date(d.getFullYear(), d.getMonth(), 1);
      }
      await loadCalendar();
    }
    // 同一份草稿重试沿用 requestId，由后端幂等处理，避免网络重试生成多笔预约。
    if (step.value === 4 && !draft.requestId) setDraft({ requestId: crypto.randomUUID() });
  } catch (e) {
    if (id === loadId) error.value = e.message;
  } finally {
    if (id === loadId) loading.value = false;
  }
}
async function loadCalendar() {
  calendar.value = await api.calendar({
    hpId: draft.hpId,
    year: month.value.getFullYear(),
    month: month.value.getMonth() + 1,
  });
}
async function moveMonth(delta) {
  if (loading.value) return;
  month.value = new Date(month.value.getFullYear(), month.value.getMonth() + delta, 1);
  loading.value = true;
  try {
    await loadCalendar();
  } catch (e) {
    notify(e.message);
  } finally {
    loading.value = false;
  }
}
function choosePerson(p) {
  if (draft.personId !== p.personId) {
    clearDraft();
    setDraft({ personId: p.personId });
  }
  router.push(paths[1]);
}
function chooseHospital(h) {
  // 上游选择变化时清除后续选择及提交编号，防止沿用其他医院的套餐或日期。
  if (draft.hpId !== h.hpId)
    setDraft({ hpId: h.hpId, smId: null, orderDate: null, requestId: null });
  router.push(paths[2]);
}
function chooseMeal(m) {
  if (draft.smId !== m.smId) setDraft({ smId: m.smId, orderDate: null, requestId: null });
  router.push(paths[3]);
}
async function expand(m) {
  expanded.value = expanded.value === m.smId ? null : m.smId;
  if (expanded.value && !details.value[m.smId]) {
    try {
      details.value[m.smId] = await api.meal({ hpId: draft.hpId, smId: m.smId });
    } catch (e) {
      notify(e.message);
    }
  }
}
function chooseDate(d) {
  if (!d.available) return;
  setDraft({ orderDate: d.date, requestId: null });
}
async function submit() {
  if (!agreed.value) return;
  submitting.value = true;
  try {
    // 不提交前端显示金额；套餐价格、名额占用及支付记录由后端同一事务确认。
    const order = await post('/orders/saveOrders', {
      personId: draft.personId,
      hpId: draft.hpId,
      smId: draft.smId,
      orderDate: draft.orderDate,
      requestId: draft.requestId,
    });
    clearDraft();
    notify('预约成功，祝您体检顺利', 'success');
    router.replace(`/appointmentsuccess/${order.orderId}`);
  } catch (e) {
    notify(e.message);
  } finally {
    submitting.value = false;
  }
}
watch(() => route.path, load, { immediate: true });
</script>
<template>
  <div class="prototype-booking" :class="`booking-step-${step}`">
    <AsyncState :loading="loading" :error="error" @retry="load">
      <section v-if="step === 0" class="appointment-start">
        <img class="appointment-picture" :src="'./img/yuyue.png'" alt="唯有爱与健康不能等待" />
        <div class="appointment-types">
          <button
            class="appointment-type"
            :class="{ chosen: bookingFor === 'self' }"
            aria-label="本人预约"
            @click="bookingFor = 'self'"
          >
            <img :src="'./img/benrenyuyue.png'" alt="本人预约" />
          </button>
          <button
            class="appointment-type"
            :class="{ chosen: bookingFor === 'family' }"
            aria-label="家属预约"
            @click="bookingFor = 'family'"
          >
            <img :src="'./img/jiashuyuyue.png'" alt="家属预约" />
          </button>
        </div>
        <div v-if="bookingFor" class="person-choices">
          <div class="person-choice-heading">
            <span>请选择体检人</span><RouterLink to="/persons?add=1">添加家属档案 ›</RouterLink>
          </div>
          <button
            v-for="p in shownPersons"
            :key="p.personId"
            class="person-select"
            @click="choosePerson(p)"
          >
            <span
              ><strong>{{ p.realName }}</strong
              ><small
                >{{ p.isSelf ? '本人' : p.relationship }} · {{ sexText(p.sex) }} ·
                {{ p.identityCardMasked }}</small
              ></span
            ><Icon name="arrow" :size="18" />
          </button>
          <p v-if="!shownPersons.length" class="small-note">请先添加家属档案，再为家人预约体检。</p>
          <p class="small-note">每位体检人只能有一条未归档的有效预约。</p>
        </div>
      </section>

      <section v-if="step === 1" class="hospital-list">
        <article v-for="(h, i) in hospitals" :key="h.hpId" class="hospital-card">
          <button
            class="hospital-title"
            :aria-label="`选择医院 ${h.name}`"
            @click="chooseHospital(h)"
          >
            <h3>{{ h.name }}</h3>
            <Icon name="arrow" :size="19" />
          </button>
          <div class="hospital-info">
            <img
              :src="
                h.picture?.startsWith('/')
                  ? '.' + h.picture
                  : h.picture || `./img/hospita${(i % 2) + 1}.png`
              "
              :alt="h.name + '环境'"
            />
            <table>
              <tbody>
                <tr>
                  <td>营业时间</td>
                  <td>{{ h.businessHours }}</td>
                </tr>
                <tr>
                  <td>采血截止</td>
                  <td>{{ h.deadline || '请咨询医院' }}</td>
                </tr>
                <tr>
                  <td>电话</td>
                  <td>{{ h.telephone }}</td>
                </tr>
                <tr>
                  <td>地址</td>
                  <td>{{ h.address }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="hospital-contact">
            <a :href="`tel:${h.telephone}`"><Icon name="phone" :size="14" />联系我们</a
            ><a
              :href="`https://uri.amap.com/search?keyword=${encodeURIComponent(h.address || h.name)}`"
              target="_blank"
              rel="noopener noreferrer"
              ><Icon name="location" :size="14" />查找位置</a
            >
          </div>
        </article>
        <p v-if="!hospitals.length" class="small-note">暂无可预约的体检机构，请稍后再试。</p>
      </section>

      <section v-if="step === 2" class="meal-list">
        <article v-for="m in meals" :key="m.smId" class="meal-card">
          <div class="meal-card-main">
            <button class="meal-select" :aria-label="`选择套餐 ${m.name}`" @click="chooseMeal(m)">
              <strong>{{
                m.type === 2 ? '通用套餐' : m.type === 1 ? '男士套餐' : '女士套餐'
              }}</strong
              ><span>{{ m.name }}</span
              ><small>¥{{ money(m.price) }} / 人</small>
            </button>
            <button class="meal-expand" :aria-expanded="expanded === m.smId" @click="expand(m)">
              详情 <span>{{ expanded === m.smId ? '⌃' : '⌄' }}</span>
            </button>
          </div>
          <div v-if="expanded === m.smId" class="meal-details">
            <p v-if="!details[m.smId]" class="small-note">正在加载检查项目…</p>
            <table v-else>
              <thead>
                <tr>
                  <th>检查项目</th>
                  <th>检查内容</th>
                  <th>检查意义</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in details[m.smId]?.items || []" :key="item.ciId">
                  <td>{{ item.ciName }}</td>
                  <td>
                    {{ item.ciContent
                    }}<small>{{ item.details?.map((d) => d.name).join('、') }}</small>
                  </td>
                  <td>{{ item.meaning || '了解身体健康状况' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>
        <p v-if="!meals.length" class="small-note">该机构暂无适合的套餐，请返回选择其他机构。</p>
        <p v-else class="small-note meal-note">
          已按{{ person?.realName }}的性别筛选，点击套餐名称继续预约。
        </p>
      </section>

      <section v-if="step === 3" class="calendar-panel">
        <div class="calendar-heading">
          <button :disabled="prevDisabled" aria-label="上个月" @click="moveMonth(-1)">‹</button>
          <h2>{{ monthLabel }}</h2>
          <button :disabled="nextDisabled" aria-label="下个月" @click="moveMonth(1)">›</button>
        </div>
        <div class="calendar-week">
          <span v-for="weekday in ['日', '一', '二', '三', '四', '五', '六']" :key="weekday">{{
            weekday
          }}</span>
        </div>
        <div class="calendar-grid">
          <template v-for="(day, index) in days" :key="index"
            ><span v-if="!day" class="calendar-blank"></span
            ><button
              v-else
              class="calendar-day"
              :class="{ selected: draft.orderDate === day.date, today: day.date === localDate() }"
              :disabled="!day.available"
              :aria-label="`${day.date} ${day.available ? '剩余' + day.remaining + '个名额' : '不可预约'}`"
              @click="chooseDate(day)"
            >
              <strong>{{ day.day }}</strong
              ><small>{{
                day.available
                  ? '余' + day.remaining
                  : day.date < localDate()
                    ? ''
                    : day.capacity === 0
                      ? '休息'
                      : '已满'
              }}</small>
            </button></template
          >
        </div>
        <p class="selected-date">
          {{
            draft.orderDate
              ? '您选择的日期：' + dateText(draft.orderDate)
              : '请点击有剩余名额的日期'
          }}
        </p>
        <p class="small-note calendar-note">休息日、已满及未开放的日期不可预约。</p>
        <div class="booking-bottom calendar-bottom">
          <span></span
          ><button
            :disabled="
              !draft.orderDate || !calendar.some((d) => d.date === draft.orderDate && d.available)
            "
            @click="router.push(paths[4])"
          >
            下一步
          </button>
        </div>
      </section>

      <section v-if="step === 4" class="confirm-main">
        <div class="confirm-section">
          <h3>体检人信息</h3>
          <dl class="detail-list">
            <div>
              <dt>姓名：</dt>
              <dd>{{ person?.realName }}</dd>
            </div>
            <div>
              <dt>证件号码：</dt>
              <dd>{{ person?.identityCardMasked }}</dd>
            </div>
            <div>
              <dt>出生日期：</dt>
              <dd>{{ dateText(person?.birthday) }}</dd>
            </div>
            <div>
              <dt>手机号码：</dt>
              <dd>{{ state.user?.userId }}</dd>
            </div>
          </dl>
        </div>
        <div class="confirm-section">
          <h3>体检日期</h3>
          <p class="section-value">{{ dateText(draft.orderDate) }}</p>
        </div>
        <div class="confirm-section">
          <h3>体检机构</h3>
          <p class="section-value">{{ selectedHospital?.name }}</p>
          <dl class="detail-list">
            <div>
              <dt>营业时间：</dt>
              <dd>{{ selectedHospital?.businessHours }}</dd>
            </div>
            <div>
              <dt>采血截止：</dt>
              <dd>{{ selectedHospital?.deadline || '请咨询医院' }}</dd>
            </div>
            <div>
              <dt>机构电话：</dt>
              <dd>{{ selectedHospital?.telephone }}</dd>
            </div>
            <div>
              <dt>机构地址：</dt>
              <dd>{{ selectedHospital?.address }}</dd>
            </div>
          </dl>
        </div>
        <div class="confirm-section">
          <h3>套餐类型</h3>
          <p class="section-value">{{ meal?.name }}</p>
        </div>
        <label class="confirmation-agreement"
          ><input v-model="agreed" type="checkbox" /><span
            >我已核对预约信息，同意本次支付（课程练习，不产生真实扣款）。</span
          ></label
        >
        <div class="booking-bottom payment-bottom">
          <span
            >实付款：<strong>¥{{ money(meal?.price) }}</strong></span
          ><button :disabled="!agreed || submitting" @click="submit">
            {{ submitting ? '提交中…' : '确认支付' }}
          </button>
        </div>
      </section>
      <div v-if="step === 1 || step === 2 || step === 4" class="booking-back">
        <button :disabled="submitting" @click="router.push(paths[step - 1])">‹ 返回上一步</button>
      </div>
    </AsyncState>
  </div>
</template>
<style scoped>
.prototype-booking {
  color: #555;
  min-height: calc(100dvh - 124px);
}
.appointment-picture {
  display: block;
  width: 100%;
  height: auto;
}
.appointment-types {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  margin: 38px 3.6% 0;
}
.appointment-type {
  width: 48%;
  padding: 0;
  border-radius: 6px;
  line-height: 0;
  outline-offset: 4px;
}
.appointment-type img {
  width: 100%;
  height: auto;
  display: block;
  border-radius: 6px;
}
.appointment-type.chosen {
  outline: 2px solid #6bb9b6;
}
.person-choices {
  margin: 28px 6% 16px;
}
.person-choice-heading {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #888;
  margin-bottom: 6px;
}
.person-choice-heading a {
  color: #137e92;
}
.person-select {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 13px 0;
  text-align: left;
  border-bottom: 1px solid #e9e9e9;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}
.person-select strong {
  font-size: 15px;
  font-weight: 500;
  display: block;
}
.person-select small {
  display: block;
  font-size: 11px;
  color: #999;
  margin-top: 3px;
}
.small-note {
  font-size: 11px;
  line-height: 1.7;
  color: #999;
  margin: 13px 0;
}
.hospital-list {
  padding: 14px 3.6%;
}
.hospital-card {
  padding: 15px 14px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  margin-bottom: 14px;
  box-shadow: 1px 2px 4px #00000015;
}
.hospital-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 0;
  text-align: left;
}
.hospital-title h3 {
  font-size: 16px;
  line-height: 1.4;
  font-weight: 600;
  padding-left: 9px;
  border-left: 3px solid #157999;
  margin: 0;
}
.hospital-info {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 13px;
}
.hospital-info > img {
  width: 24%;
  aspect-ratio: 1;
  object-fit: cover;
}
.hospital-info table {
  flex: 1;
  width: 72%;
  font-size: 12px;
  line-height: 1.6;
  border-collapse: collapse;
}
.hospital-info td {
  vertical-align: top;
  padding: 0 0 3px;
}
.hospital-info td:first-child {
  width: 61px;
  white-space: nowrap;
  padding-right: 5px;
  color: #777;
}
.hospital-contact {
  display: flex;
  justify-content: flex-end;
  gap: 9px;
  margin-top: 9px;
}
.hospital-contact a {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 30px;
  padding: 2px 10px;
  font-size: 12px;
  border: 1px solid #73b2c2;
  border-radius: 5px;
  color: #41899d;
}
.meal-list {
  padding: 36px 3.6% 14px;
}
.meal-card {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  box-shadow: 1px 2px 4px #00000012;
  margin-bottom: 15px;
  overflow: hidden;
}
.meal-card-main {
  display: flex;
  min-height: 87px;
}
.meal-select {
  flex: 1;
  text-align: left;
  padding: 13px 12px 13px 11%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  color: #555;
}
.meal-select strong {
  font-size: 16px;
  font-weight: 600;
}
.meal-select span {
  font-size: 13px;
  line-height: 1.5;
}
.meal-select small {
  font-size: 11px;
  color: #999;
  margin-top: 2px;
}
.meal-expand {
  width: 78px;
  flex-shrink: 0;
  border-left: 1px solid #eee;
  font-size: 13px;
  color: #777;
  padding: 0 9px;
}
.meal-expand span {
  margin-left: 5px;
  font-size: 16px;
}
.meal-details table {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
  font-size: 12px;
}
.meal-details th {
  font-weight: 400;
  background: #eee;
  text-align: center;
  padding: 9px 3px;
}
.meal-details td {
  vertical-align: top;
  border: 1px solid #eee;
  padding: 9px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.meal-details td small {
  display: block;
  color: #999;
  font-size: 10px;
  margin-top: 4px;
}
.meal-note {
  text-align: center;
}
.calendar-panel {
  background: #fff;
  padding: 20px 4% 18px;
  min-height: calc(100dvh - 184px);
  border-radius: 0;
  border: 0;
  box-shadow: none;
}
.calendar-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 0 0 10px;
  padding: 0 4px;
}
.calendar-heading h2 {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}
.calendar-heading button {
  font-size: 27px;
  line-height: 34px;
  width: 34px;
  padding: 0;
  color: #555;
}
.calendar-week,
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 0;
}
.calendar-week {
  margin: 0 0 14px;
  text-align: center;
  font-size: 13px;
  font-weight: 600;
}
.calendar-week span {
  padding: 6px 0;
}
.calendar-day,
.calendar-blank {
  height: 53px;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex-direction: column;
  padding: 3px 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: #444;
}
.calendar-day strong {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  width: 26px;
  height: 26px;
  line-height: 26px;
  border-radius: 50%;
  font-weight: 500;
}
.calendar-day small {
  font-size: 10px;
  line-height: 14px;
  color: #999;
  white-space: nowrap;
}
.calendar-day:disabled {
  opacity: 1;
  color: #c6c6c6;
}
.calendar-day:disabled small {
  color: #c5c5c5;
}
.calendar-day.selected {
  background: transparent;
}
.calendar-day.selected strong {
  background: #ef9942;
  color: #fff;
}
.calendar-day.today:not(.selected) strong {
  color: #137e92;
}
.selected-date {
  font-size: 12px;
  text-align: center;
  color: #777;
  margin: 24px 0 4px;
}
.calendar-note {
  text-align: center;
  margin-top: 0;
}
.booking-bottom {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 60px;
  z-index: 16;
  width: min(100%, 480px);
  height: 49px;
  background: #fff;
  display: flex;
  border-top: 1px solid #f0f0f0;
}
.booking-bottom > span {
  flex: 1;
  display: flex;
  align-items: center;
  padding-left: 6%;
  font-size: 15px;
}
.booking-bottom > button {
  min-width: 34%;
  padding: 0 12px;
  background: #117c94;
  color: #fff;
  font-size: 16px;
  border-radius: 0;
}
.booking-bottom button:disabled {
  opacity: 0.5;
}
.payment-bottom strong {
  font-size: 18px;
  color: #f77b2d;
  font-weight: 400;
}
.payment-bottom > button {
  font-size: 14px;
  min-width: 40%;
}
.confirm-main {
  padding: 2px 7% 15px;
}
.confirm-section h3 {
  display: flex;
  align-items: center;
  min-height: 49px;
  border-bottom: 1px solid #eee;
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 9px;
  padding: 0;
  gap: 10px;
}
.confirm-section h3:before {
  content: '';
  display: block;
  width: 2px;
  height: 14px;
  background: #127a90;
}
.confirm-section .detail-list {
  font-size: 12px;
  color: #555;
  display: block;
  padding: 0;
  margin: 0 0 5px;
}
.confirm-section .detail-list > div {
  display: grid;
  grid-template-columns: 85px minmax(0, 1fr);
  gap: 0;
  min-height: 31px;
  padding: 3px 0;
  border: 0;
  align-items: start;
}
.confirm-section dt,
.confirm-section dd {
  margin: 0;
  color: #555;
  line-height: 1.8;
  font-weight: 400;
  overflow-wrap: anywhere;
}
.section-value {
  font-size: 13px;
  margin: 10px 0 13px;
  line-height: 1.8;
}
.confirmation-agreement {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  font-size: 11px;
  line-height: 1.8;
  color: #888;
  margin: 23px 0 5px;
}
.confirmation-agreement input {
  margin-top: 4px;
  accent-color: #137e92;
}
.booking-back {
  padding: 6px 7% 15px;
}
.booking-back button {
  font-size: 12px;
  color: #137e92;
  padding: 5px 0;
}
.booking-step-3,
.booking-step-4 {
  padding-bottom: 52px;
}
@media (max-width: 360px) {
  .booking-bottom {
    bottom: 55px;
  }
  .hospital-title h3 {
    font-size: 14px;
  }
  .hospital-info table {
    font-size: 11px;
  }
  .hospital-info td:first-child {
    width: 55px;
  }
}
</style>
