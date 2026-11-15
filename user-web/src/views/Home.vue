<script setup>
import { ref } from 'vue';
import { notify } from '../state';
import { healthArticles, readingMinutes } from '../data/articles';
import { doctors } from '../data/doctors';
import MyCheckup from '../components/MyCheckup.vue';
const assessTrack = ref(null);
const draggingAssess = ref(false);
let assessDrag = null;
let suppressAssessClickUntil = 0;
// 手机保留浏览器原生触摸滚动；仅为鼠标补充拖动，并区分点击与拖拽。
function startAssessDrag(event) {
  if (event.pointerType !== 'mouse' || event.button !== 0) return;
  assessDrag = { x: event.clientX, scrollLeft: event.currentTarget.scrollLeft };
  suppressAssessClickUntil = 0;
}
function moveAssessDrag(event) {
  if (!assessDrag) return;
  if (!(event.buttons & 1)) {
    endAssessDrag();
    return;
  }
  const distance = event.clientX - assessDrag.x;
  if (!draggingAssess.value && Math.abs(distance) < 6) return;
  draggingAssess.value = true;
  event.currentTarget.setPointerCapture(event.pointerId);
  event.preventDefault();
  event.currentTarget.scrollLeft = assessDrag.scrollLeft - distance;
}
function endAssessDrag() {
  // 拖动松手后短暂拦截 click，避免将一次滑动误判为点击评估图片。
  if (draggingAssess.value) suppressAssessClickUntil = Date.now() + 200;
  assessDrag = null;
  draggingAssess.value = false;
}
function handleAssessClick(event) {
  if (Date.now() >= suppressAssessClickUntil) return;
  event.preventDefault();
  event.stopPropagation();
}
function scrollAssess(event) {
  const track = event.currentTarget;
  if (Math.abs(event.deltaX) >= Math.abs(event.deltaY)) return;
  const distance = event.deltaY * (event.deltaMode === 1 ? 20 : 1);
  const before = track.scrollLeft;
  track.scrollLeft += distance;
  // 横向已经到边界时继续允许页面纵向滚动，避免鼠标停留此处后无法向下浏览。
  if (track.scrollLeft !== before) event.preventDefault();
}
function stepAssess(direction) {
  assessTrack.value.scrollLeft += direction * assessTrack.value.clientWidth * 0.45;
}
const menus = [
  { name: '免费咨询', desc: '新型冠状病毒肺炎', image: 'menu01.png' },
  { name: '网络问诊', desc: '图文视频网络咨询', image: 'menu02.png' },
  { name: '在线门诊', desc: '复旦医科大学专家', image: 'menu03.png' },
  { name: '慢病管理', desc: '血压血糖健康管理', image: 'menu04.png' },
  { name: '上门护理', desc: '网上购买上门服务', image: 'menu05.png' },
  { name: '团检预约', desc: '团体体检套餐定制', image: 'menu06.png', to: '/appointment' },
];
function explain(name) {
  notify(`${name}为原型展示内容，本课程项目暂未接入对应外部服务。`, 'info');
}
</script>
<template>
  <div class="prototype-home">
    <nav class="home-services">
      <div class="home-banner"><img src="/img/logo.png" alt="健康生活，从关爱自己开始" /></div>
      <ul class="home-menu">
        <li v-for="menu in menus" :key="menu.name">
          <component
            :is="menu.to ? 'RouterLink' : 'button'"
            :to="menu.to"
            @click="!menu.to && explain(menu.name)"
            ><img :src="'/img/' + menu.image" alt="" />
            <div>
              <h3>{{ menu.name }}</h3>
              <p>{{ menu.desc }}</p>
            </div></component
          >
        </li>
      </ul>
    </nav>
    <MyCheckup />
    <section class="home-report">
      <div class="item-title"><p>我的健康报告</p></div>
      <div class="home-report-content">
        <p>随时随地查看体检报告</p>
        <RouterLink to="/reportlist">立即查看</RouterLink>
      </div>
    </section>
    <section class="home-doctors">
      <div class="item-title"><p>推荐医生</p></div>
      <ul>
        <li v-for="doctor in doctors" :key="doctor.id">
          <RouterLink :to="`/doctors/${doctor.id}`">
            <img :src="doctor.image" :alt="doctor.name" />
            <h3>{{ doctor.name }}</h3>
            <p>{{ doctor.title }}</p>
          </RouterLink>
        </li>
      </ul>
    </section>
    <section class="home-assess">
      <div class="item-title">
        <p>健康评估</p>
        <button @click="explain('健康评估')">更多</button>
      </div>
      <div
        ref="assessTrack"
        class="assess-scroll"
        :class="{ dragging: draggingAssess }"
        role="region"
        aria-label="健康评估，左右滑动查看更多"
        tabindex="0"
        @pointerdown="startAssessDrag"
        @pointermove="moveAssessDrag"
        @pointerleave="!draggingAssess && endAssessDrag()"
        @pointerup="endAssessDrag"
        @pointercancel="endAssessDrag"
        @lostpointercapture="endAssessDrag"
        @click.capture="handleAssessClick"
        @wheel="scrollAssess"
        @keydown.left.prevent="stepAssess(-1)"
        @keydown.right.prevent="stepAssess(1)"
      >
        <button v-for="n in 6" :key="n" @click="explain('健康评估')">
          <img :src="`/img/assess${n}.png`" alt="健康评估" draggable="false" />
        </button>
      </div>
    </section>
    <section class="home-information">
      <div class="item-title"><p>健康资讯</p></div>
      <ul>
        <li v-for="article in healthArticles" :key="article.slug">
          <RouterLink :to="`/articles/${article.slug}`" class="information-link">
            <img :src="article.image" :alt="article.imageAlt" />
            <div class="information-copy">
              <h3>{{ article.title }}</h3>
              <span>{{ article.description }}</span>
              <p>健康科普 · 约 {{ readingMinutes(article) }} 分钟</p>
            </div>
          </RouterLink>
        </li>
      </ul>
    </section>
  </div>
</template>
<style scoped>
.information-link {
  display: flex;
  align-items: flex-start;
  gap: 3cqw;
  width: 100%;
  padding: 3.6cqw;
  text-align: left;
}
.information-copy {
  flex: 1;
  min-width: 0;
}
.information-link:hover h3 {
  color: var(--teal);
}
</style>
