import axios from 'axios';
import { state } from './state';
// 页面统一通过本模块请求 /tijian；Vite 开发代理将请求转发给 Spring Boot。
const http = axios.create({ baseURL: '/tijian', withCredentials: true, timeout: 15000 });
let sessionPromise;
export async function loadSession(force = false) {
  if (!force && state.ready) return state.user;
  // 路由守卫和页面可能同时读取会话，复用进行中的请求，避免重复访问。
  if (sessionPromise) return sessionPromise;
  sessionPromise = http
    .get('/auth/session')
    .then(({ data: body }) => {
      if (body.code !== 0) throw new Error(body.message || '会话读取失败');
      state.csrf = body.data.csrfToken;
      state.user = body.data.user;
      state.ready = true;
      return state.user;
    })
    .finally(() => {
      sessionPromise = null;
    });
  return sessionPromise;
}
// 身份由浏览器的 HttpOnly Cookie 传递；写操作再附上后端颁发的 CSRF 令牌。
http.interceptors.request.use(async (config) => {
  if (config.method !== 'get' && config.method !== 'head') {
    if (!state.csrf) await loadSession(true);
    config.headers['X-CSRF-Token'] = state.csrf;
  }
  return config;
});
http.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 401) {
      state.user = null;
      if (!['#/login', '#/register'].some((p) => window.location.hash.startsWith(p))) {
        const next = window.location.hash.slice(1);
        window.location.hash = `/login?next=${encodeURIComponent(next || '/index')}`;
      }
    }
    return Promise.reject(
      new Error(
        error.response?.data?.message ||
          (error.code === 'ECONNABORTED'
            ? '请求超时，请稍后重试'
            : '服务连接失败，请确认后端已启动'),
      ),
    );
  },
);
async function request(method, url, data) {
  const res = await http.request({
    method,
    url,
    ...(method === 'get' ? { params: data } : { data }),
  });
  // 在这里解开统一响应 {code, message, data}，业务页面只接收 data。
  if (res.data.code !== 0) throw new Error(res.data.message || '操作失败，请重试');
  return res.data.data;
}
export const get = (url, params) => request('get', url, params);
export const post = (url, data = {}) => request('post', url, data);
export const api = {
  persons: () => get('/persons/list'),
  hospitals: () => get('/hospital/listHospital'),
  hospital: (hpId) => get('/hospital/getHospitalById', { hpId }),
  meals: (params) => get('/setmeal/listSetmealByType', params),
  meal: (params) => get('/setmeal/getSetmealById', params),
  calendar: (params) => get('/calendar/listAppointmentCalendar', params),
  orders: (params) => get('/orders/listOrdersByUserId', params),
  order: (orderId) => get('/orders/getOrdersById', { orderId }),
  reports: (params) => get('/reports/list', params),
  report: (orderId) => get('/reports/detail', { orderId }),
};
