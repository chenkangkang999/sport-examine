<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { get, post } from '../api';
import { state, notify } from '../state';
import AsyncState from '../components/AsyncState.vue';
import Icon from '../components/Icon.vue';
const router = useRouter(),
  items = ref([]),
  loading = ref(true),
  error = ref(''),
  page = ref(1),
  total = ref(0),
  unreadOnly = ref(false),
  busy = ref(false);
const pageSize = 20;
let request = 0;
async function load() {
  const id = ++request;
  loading.value = true;
  error.value = '';
  try {
    const data = await get('/notifications', {
      page: page.value,
      pageSize,
      unreadOnly: unreadOnly.value,
    });
    if (id !== request) return;
    items.value = data.items;
    total.value = data.total;
    state.unreadCount = Number(data.unreadCount || 0);
    const lastPage = Math.max(1, Math.ceil(total.value / pageSize));
    if (page.value > lastPage) {
      page.value = lastPage;
      await load();
    }
  } catch (e) {
    if (id === request) error.value = e.message;
  } finally {
    if (id === request) loading.value = false;
  }
}
function filter(value) {
  unreadOnly.value = value;
  page.value = 1;
  load();
}
async function mark(item) {
  if (item.isRead || busy.value) return;
  busy.value = true;
  try {
    await post('/notifications/read', { id: item.id });
    await load();
  } catch (e) {
    notify(e.message);
  } finally {
    busy.value = false;
  }
}
async function open(item) {
  if (busy.value) return;
  busy.value = true;
  if (!item.isRead) {
    try {
      const result = await post('/notifications/read', { id: item.id });
      state.unreadCount = Number(result.unreadCount || 0);
    } catch (e) {
      notify(e.message);
    }
  }
  try {
    await router.push(
      item.type === 'ARCHIVED' ? `/report/${item.orderId}` : `/orders/${item.orderId}`,
    );
  } finally {
    busy.value = false;
  }
}
async function markAll() {
  busy.value = true;
  try {
    await post('/notifications/read-all');
    await load();
    notify('消息已全部标记为已读', 'success');
  } catch (e) {
    notify(e.message);
  } finally {
    busy.value = false;
  }
}
const time = (value) =>
  String(value || '')
    .replace('T', ' ')
    .slice(0, 16);
onMounted(load);
onBeforeUnmount(() => request++);
</script>
<template>
  <section class="message-page">
    <header class="message-heading">
      <div>
        <h2>消息中心</h2>
        <p>{{ state.unreadCount }} 条未读消息</p>
      </div>
      <button class="text-button" :disabled="busy || !state.unreadCount" @click="markAll">
        全部已读
      </button>
    </header>
    <nav class="message-tabs" aria-label="消息筛选">
      <button :aria-pressed="!unreadOnly" :class="{ active: !unreadOnly }" @click="filter(false)">
        全部消息</button
      ><button :aria-pressed="unreadOnly" :class="{ active: unreadOnly }" @click="filter(true)">
        未读消息
      </button>
    </nav>
    <AsyncState
      :loading="loading"
      :error="error"
      :empty="!items.length"
      message="暂无消息"
      @retry="load"
      ><template #hint>预约、取消或报告归档后，相应通知会保存在这里。</template>
      <article
        v-for="item in items"
        :key="item.id"
        class="message-card"
        :class="{ unread: !item.isRead }"
      >
        <div class="message-card-heading">
          <Icon
            :name="
              item.type === 'ARCHIVED' ? 'report' : item.type === 'CANCELLED' ? 'clock' : 'calendar'
            "
            :size="20"
          />
          <h3>{{ item.title }}</h3>
          <span v-if="!item.isRead" class="unread-dot" aria-label="未读"></span>
        </div>
        <p>{{ item.content }}</p>
        <time>{{ time(item.createdAt) }}</time>
        <div class="message-actions">
          <button v-if="!item.isRead" :disabled="busy" @click="mark(item)">标为已读</button
          ><button class="message-open" :disabled="busy" @click="open(item)">
            {{ item.type === 'ARCHIVED' ? '查看报告' : '查看预约' }} ›
          </button>
        </div>
      </article>
    </AsyncState>
    <nav v-if="total > pageSize" class="message-pagination" aria-label="消息分页">
      <button
        :disabled="page === 1 || loading"
        @click="
          page--;
          load();
        "
      >
        上一页</button
      ><span>{{ page }} / {{ Math.ceil(total / pageSize) }}</span
      ><button
        :disabled="page * pageSize >= total || loading"
        @click="
          page++;
          load();
        "
      >
        下一页
      </button>
    </nav>
  </section>
</template>
<style scoped>
.message-page {
  padding: 18px 14px 24px;
}
.message-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.message-heading h2 {
  font-size: calc(19px * var(--reading-scale, 1));
  font-weight: 600;
  color: #386a74;
}
.message-heading p {
  font-size: calc(12px * var(--reading-scale, 1));
  color: #8b999d;
  margin-top: 4px;
}
.message-tabs {
  display: flex;
  border-bottom: 1px solid #e4eded;
  margin-bottom: 14px;
  gap: 18px;
}
.message-tabs button {
  font-size: calc(13px * var(--reading-scale, 1));
  padding: 8px 4px;
  color: #85999c;
  border-bottom: 2px solid transparent;
}
.message-tabs .active {
  border-bottom-color: #137e92;
  color: #137e92;
}
.message-card {
  padding: 15px 14px;
  background: #fff;
  border: 1px solid #e5eded;
  border-radius: 9px;
  margin-bottom: 12px;
}
.message-card.unread {
  border-color: #c6dfe0;
}
.message-card-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #4d858d;
}
.message-card h3 {
  font-size: calc(14px * var(--reading-scale, 1));
  flex: 1;
  font-weight: 600;
}
.unread-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #31a5a2;
}
.message-card > p {
  font-size: calc(12px * var(--reading-scale, 1));
  line-height: 1.9;
  color: #6d8185;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  margin: 10px 0;
}
.message-card time {
  font-size: calc(10px * var(--reading-scale, 1));
  color: #9aa8aa;
}
.message-actions {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 10px;
}
.message-actions button {
  font-size: calc(12px * var(--reading-scale, 1));
  color: #84999d;
}
.message-actions .message-open {
  color: #137e92;
}
.message-pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  font-size: calc(12px * var(--reading-scale, 1));
  color: #68898e;
  padding-top: 10px;
}
.message-pagination button:disabled {
  color: #bac7c9;
}
</style>
