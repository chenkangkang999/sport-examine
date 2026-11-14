import axios from 'axios';
import { reactive } from 'vue';
import { ElMessage } from 'element-plus';

// 工作台身份使用独立 staff 状态，与同一浏览器中的用户端身份分别登录、退出。
export const session = reactive({ staff: null, csrfToken: '', loaded: false });
const http = axios.create({ baseURL: '/tijiancms', withCredentials: true, timeout: 20000 });
// Cookie 携带后端会话；所有写请求同时提交会话中的 CSRF 令牌。
http.interceptors.request.use((config) => {
  if (!['get', 'head', 'options'].includes(config.method))
    config.headers['X-CSRF-Token'] = session.csrfToken;
  return config;
});
http.interceptors.response.use(
  (response) => {
    const body = response.data;
    if (body?.code !== 0) return Promise.reject(new Error(body?.message || '服务器返回了无效响应'));
    return body.data;
  },
  (error) => {
    error.message =
      error.response?.data?.message ||
      (error.code === 'ECONNABORTED'
        ? '请求超时，请稍后重试'
        : error.response
          ? '操作失败，请稍后重试'
          : '无法连接后端服务，请检查服务是否已启动');
    if (error.response?.status === 401) {
      session.staff = null;
      window.dispatchEvent(new Event('staff-session-expired'));
    }
    // 409 交给报告页面处理，保留重新加载与继续检查当前填写内容的选择。
    if (!error.config?.quiet && error.response?.status !== 409) ElMessage.error(error.message);
    return Promise.reject(error);
  },
);
let loading;
export async function loadSession(force = false) {
  if (session.loaded && !force) return session.staff;
  // 多个页面共用同一次会话读取，force 用于登录后刷新最新角色和 CSRF 信息。
  if (loading) return loading;
  loading = http
    .get('/auth/session', { quiet: true })
    .then((data) => {
      session.csrfToken = data.csrfToken;
      session.staff = data.staff;
      session.loaded = true;
      return session.staff;
    })
    .finally(() => {
      loading = null;
    });
  return loading;
}
export async function login(credentials) {
  await loadSession(true);
  await http.post('/doctor/getDoctorByCodeByPass', credentials);
  await loadSession(true);
}
export async function logout() {
  await http.post('/auth/logout');
  session.staff = null;
  session.loaded = false;
}
export const get = (url, params, config = {}) => http.get(url, { ...config, params });
export const post = (url, data) => http.post(url, data);
export async function hospitals() {
  const response = await axios.get('/tijian/hospital/listHospital', { withCredentials: true });
  if (response.data.code !== 0) throw new Error(response.data.message);
  return response.data.data;
}
export function stateText(state) {
  return { 1: '待归档', 2: '已归档', 3: '已取消' }[state] || '未知状态';
}
export function sexText(sex) {
  return Number(sex) === 1 ? '男' : Number(sex) === 0 ? '女' : '不限';
}
export function money(value) {
  return Number(value || 0).toFixed(2);
}
export function dateString(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
export function reference(detail) {
  if (Number(detail.type) === 1)
    return `${detail.minrange ?? '不限'} ~ ${detail.maxrange ?? '不限'} ${detail.unit || ''}`;
  if (Number(detail.type) === 2)
    return `${detail.normalValue ?? detail.normalValueString ?? '—'} ${detail.unit || ''}`;
  return detail.normalValueString || '由医生根据检查结果填写';
}
