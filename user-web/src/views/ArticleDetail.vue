<script setup>
import { computed, watch } from 'vue';
import { useRoute } from 'vue-router';
import Icon from '../components/Icon.vue';
import { healthArticles, findHealthArticle, readingMinutes } from '../data/articles';

const route = useRoute();
const article = computed(() => findHealthArticle(route.params.slug));
const relatedArticle = computed(() =>
  healthArticles.find((item) => item.slug !== article.value?.slug),
);

watch(
  article,
  (value) => {
    document.title = `${value?.title || '资讯暂未找到'} · 云医院`;
  },
  { immediate: true },
);
</script>

<template>
  <article v-if="article" class="health-article">
    <header class="article-heading">
      <p class="article-category">
        <Icon name="leaf" :size="15" />健康资讯 · {{ article.category }}
      </p>
      <h1>{{ article.title }}</h1>
      <p class="article-meta">
        <span>约 {{ readingMinutes(article) }} 分钟阅读</span>
        <span>内容编校 {{ article.compiledOn }}</span>
      </p>
    </header>

    <section class="article-introduction" aria-label="文章导读">
      <img :src="article.image" :alt="article.imageAlt" />
      <p>{{ article.summary }}</p>
    </section>

    <div class="article-body">
      <section
        v-for="(section, index) in article.sections"
        :key="section.title"
        class="article-section"
      >
        <h2>
          <span>{{ index + 1 }}</span
          >{{ section.title }}
        </h2>
        <p v-for="paragraph in section.paragraphs || []" :key="paragraph">{{ paragraph }}</p>
        <ul v-if="section.items?.length">
          <li v-for="item in section.items" :key="item">{{ item }}</li>
        </ul>
      </section>
    </div>

    <aside class="article-sources" aria-label="参考来源">
      <h2>参考来源</h2>
      <p>本文根据下列公开资料整理，原文发布日期以来源页面为准。</p>
      <ul>
        <li v-for="source in article.sources" :key="source.url">
          <a :href="source.url" target="_blank" rel="noopener noreferrer">
            {{ source.name }}<Icon name="arrow" :size="14" />
          </a>
        </li>
      </ul>
      <p class="article-note">
        本文为通用健康科普，不用于判断个人病情。具体检查与随访安排请咨询医生。
      </p>
    </aside>

    <section v-if="relatedArticle" class="article-related">
      <p>继续阅读</p>
      <RouterLink :to="`/articles/${relatedArticle.slug}`">
        <span>{{ relatedArticle.title }}</span
        ><Icon name="arrow" :size="18" />
      </RouterLink>
    </section>
    <nav class="article-actions" aria-label="文章操作">
      <RouterLink class="btn secondary" to="/index">返回首页</RouterLink>
      <RouterLink class="btn" to="/appointment">前往体检预约</RouterLink>
    </nav>
  </article>

  <section v-else class="article-missing">
    <Icon name="report" :size="42" />
    <h1>暂未找到这篇资讯</h1>
    <p>请从首页的健康资讯中重新选择文章。</p>
    <RouterLink class="btn" to="/index">返回首页</RouterLink>
  </section>
</template>

<style scoped>
.health-article {
  padding: 22px 18px 28px;
  color: #405359;
  background: #fff;
  min-height: calc(100dvh - 124px);
}
.article-category {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #348b88;
  font-size: calc(12px * var(--reading-scale, 1));
  letter-spacing: 0.5px;
}
.article-heading h1 {
  margin: 10px 0;
  font-size: calc(23px * var(--reading-scale, 1));
  font-weight: 600;
  line-height: 1.5;
  color: #204c55;
  overflow-wrap: anywhere;
}
.article-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 14px;
  font-size: calc(11px * var(--reading-scale, 1));
  color: #829397;
}
.article-introduction {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 15px 12px;
  margin-top: 20px;
  border-radius: 10px;
  background: #f0f8f7;
  border: 1px solid #dfedeb;
}
.article-introduction img {
  width: 82px;
  height: 68px;
  border-radius: 7px;
  object-fit: cover;
  flex-shrink: 0;
}
.article-introduction p {
  margin: 0;
  font-size: calc(13px * var(--reading-scale, 1));
  line-height: 1.85;
  min-width: 0;
}
.article-section {
  margin-top: 26px;
}
.article-section h2 {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin: 0 0 12px;
  font-size: calc(16px * var(--reading-scale, 1));
  font-weight: 600;
  line-height: 1.6;
  color: #285d65;
}
.article-section h2 > span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 24px;
  height: 24px;
  border-radius: 7px;
  color: #368e8a;
  background: #edf7f5;
  font-size: calc(12px * var(--reading-scale, 1));
}
.article-section p,
.article-section li {
  font-size: calc(14px * var(--reading-scale, 1));
  line-height: 1.95;
  overflow-wrap: anywhere;
}
.article-section p {
  margin: 0 0 10px;
}
.article-section ul {
  padding-left: 19px;
  margin: 0;
  list-style: disc;
}
.article-section li + li {
  margin-top: 7px;
}
.article-section li::marker {
  color: #7eb7b1;
}
.article-sources {
  margin-top: 28px;
  padding: 17px 14px;
  border: 1px solid #e6edee;
  background: #fafcfc;
  border-radius: 10px;
}
.article-sources h2 {
  font-size: calc(13px * var(--reading-scale, 1));
  color: #526b70;
  margin: 0 0 6px;
}
.article-sources p {
  font-size: calc(11px * var(--reading-scale, 1));
  line-height: 1.8;
  color: #849398;
  margin: 0;
}
.article-sources ul {
  margin: 10px 0 12px;
}
.article-sources li + li {
  margin-top: 9px;
}
.article-sources a {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  font-size: calc(12px * var(--reading-scale, 1));
  line-height: 1.7;
  color: #267e8a;
  overflow-wrap: anywhere;
}
.article-sources a svg {
  margin-top: 3px;
}
.article-sources .article-note {
  padding-top: 12px;
  border-top: 1px solid #e6edee;
  color: #70868a;
}
.article-related {
  margin-top: 22px;
}
.article-related > p {
  font-size: calc(12px * var(--reading-scale, 1));
  color: #849398;
  margin-bottom: 6px;
}
.article-related > a {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  padding: 10px 0;
  border-bottom: 1px solid #edf1f1;
  color: #366c74;
  font-size: calc(13px * var(--reading-scale, 1));
}
.article-actions {
  display: flex;
  gap: 10px;
  margin-top: 22px;
}
.article-actions .btn {
  flex: 1;
  min-width: 0;
  font-size: calc(13px * var(--reading-scale, 1));
  padding: 0 10px;
}
.article-missing {
  padding: 72px 24px;
  text-align: center;
  color: #7b999c;
}
.article-missing h1 {
  margin-top: 16px;
  font-size: calc(19px * var(--reading-scale, 1));
  font-weight: 500;
  color: #345e66;
}
.article-missing p {
  margin: 12px 0 24px;
  color: #849398;
  font-size: calc(13px * var(--reading-scale, 1));
}
@media (max-width: 360px) {
  .health-article {
    padding-right: 14px;
    padding-left: 14px;
  }
  .article-heading h1 {
    font-size: calc(21px * var(--reading-scale, 1));
  }
  .article-introduction {
    gap: 9px;
    padding: 12px 10px;
  }
  .article-introduction img {
    width: 65px;
    height: 60px;
  }
}
</style>
