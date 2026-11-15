<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { findDoctor } from '../data/doctors';
import Icon from '../components/Icon.vue';
const route = useRoute(),
  doctor = computed(() => findDoctor(route.params.id));
</script>
<template>
  <article v-if="doctor" class="doctor-detail">
    <header class="doctor-profile">
      <img :src="doctor.image" :alt="doctor.name" />
      <div>
        <p class="doctor-department">{{ doctor.department }}</p>
        <h1>
          {{ doctor.name }}<span>{{ doctor.title }}</span>
        </h1>
        <p>{{ doctor.focus }}</p>
      </div>
    </header>
    <section>
      <h2><Icon name="user" :size="17" />医生介绍</h2>
      <p>{{ doctor.introduction }}</p>
    </section>
    <section>
      <h2><Icon name="report" :size="17" />服务内容</h2>
      <ul>
        <li v-for="item in doctor.services" :key="item">{{ item }}</li>
      </ul>
    </section>
    <section>
      <h2><Icon name="calendar" :size="17" />资料准备</h2>
      <ul>
        <li v-for="item in doctor.preparation" :key="item">{{ item }}</li>
      </ul>
    </section>
    <p class="doctor-note">本页资料用于课程项目展示，具体出诊与体检安排请联系预约机构。</p>
    <nav>
      <RouterLink class="btn secondary" to="/index">返回首页</RouterLink
      ><RouterLink class="btn" to="/appointment">体检预约</RouterLink>
    </nav>
  </article>
  <section v-else class="doctor-missing">
    <h1>暂未找到这位医生</h1>
    <RouterLink class="btn secondary" to="/index">返回首页</RouterLink>
  </section>
</template>
<style scoped>
.doctor-detail {
  padding: 18px 16px 26px;
}
.doctor-profile {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 0 22px;
}
.doctor-profile img {
  width: 82px;
  height: 82px;
  object-fit: cover;
  border-radius: 50%;
  border: 3px solid #fff;
  flex-shrink: 0;
}
.doctor-profile > div {
  min-width: 0;
}
.doctor-profile h1 {
  font-size: calc(23px * var(--reading-scale, 1));
  margin: 5px 0;
  color: #305d68;
}
.doctor-profile h1 span {
  font-size: calc(12px * var(--reading-scale, 1));
  color: #7c9297;
  font-weight: 400;
  margin-left: 8px;
}
.doctor-profile p {
  font-size: calc(12px * var(--reading-scale, 1));
  line-height: 1.8;
  color: #819396;
}
.doctor-profile .doctor-department {
  color: #25858b;
}
.doctor-detail section {
  background: #fff;
  border: 1px solid #e3eceb;
  border-radius: 9px;
  padding: 16px;
  margin-bottom: 12px;
}
.doctor-detail h2 {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: calc(15px * var(--reading-scale, 1));
  color: #47767e;
  font-weight: 600;
  margin-bottom: 10px;
}
.doctor-detail section p,
.doctor-detail li {
  font-size: calc(13px * var(--reading-scale, 1));
  line-height: 1.9;
  color: #6e8388;
}
.doctor-detail ul {
  list-style: disc;
  padding-left: 18px;
}
.doctor-detail li {
  margin-top: 5px;
}
.doctor-note {
  font-size: calc(11px * var(--reading-scale, 1));
  color: #98a7aa;
  line-height: 1.9;
  margin: 15px 2px;
}
.doctor-detail nav {
  display: flex;
  gap: 12px;
}
.doctor-detail nav a {
  flex: 1;
  font-size: calc(13px * var(--reading-scale, 1));
}
.doctor-missing {
  padding: 40px 20px;
  text-align: center;
}
.doctor-missing h1 {
  font-size: calc(18px * var(--reading-scale, 1));
  margin-bottom: 22px;
}
</style>
