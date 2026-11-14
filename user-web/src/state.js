import { reactive } from 'vue';
// 少量跨页面状态用 Vue reactive 共享；ready 表示本次页面加载已向后端核验会话。
export const state = reactive({ user: null, ready: false, csrf: '', notices: [] });
export function notify(message, kind = 'error') {
  const id = Date.now() + Math.random();
  state.notices.push({ id, message, kind });
  setTimeout(() => {
    state.notices = state.notices.filter((n) => n.id !== id);
  }, 5500);
}
// sessionStorage 只保存当前标签页的预约草稿，刷新可恢复；它不代表登录凭证。
function readDraft() {
  try {
    return JSON.parse(sessionStorage.getItem('health.booking') || '{}');
  } catch {
    return {};
  }
}
export const draft = reactive(readDraft());
export function setDraft(values) {
  Object.assign(draft, values);
  sessionStorage.setItem('health.booking', JSON.stringify(draft));
}
export function clearDraft() {
  for (const key of Object.keys(draft)) delete draft[key];
  sessionStorage.removeItem('health.booking');
}
export function dateText(value) {
  return value ? String(value).slice(0, 10).replaceAll('-', '.') : '—';
}
// 按本地年月日生成预约日期，避免 toISOString() 的 UTC 换日影响日历。
export function localDate(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
export const money = (value) =>
  Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
export const orderState = (value) =>
  ({ 1: '待体检 / 未归档', 2: '报告已归档', 3: '已取消' })[value] || '未知状态';
export const sexText = (value) => (Number(value) === 1 ? '男' : '女');
export function maskId(value) {
  return value
    ? String(value).includes('*')
      ? value
      : `${String(value).slice(0, 4)}**********${String(value).slice(-4)}`
    : '已登记';
}
