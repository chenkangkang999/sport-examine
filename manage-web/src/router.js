import { createRouter, createWebHashHistory } from 'vue-router';
import { loadSession, session } from './api';
const Login = () => import('./views/Login.vue');
const Orders = () => import('./views/Orders.vue');
const Report = () => import('./views/Report.vue');
const Catalog = () => import('./views/Catalog.vue');
const Schedules = () => import('./views/Schedules.vue');
const Associations = () => import('./views/Associations.vue');
const router = createRouter({
  // 与用户端一致使用 Hash 路由；各业务页按需导入，减少首次加载内容。
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: Login, meta: { title: '登录工作台', public: true } },
    { path: '/', redirect: '/orders' },
    { path: '/orders', component: Orders, meta: { title: '预约与报告' } },
    { path: '/orders/:id', component: Report, meta: { title: '体检报告' } },
    {
      path: '/catalog/:resource(hospitals|setmeals|checkitems|doctors)',
      component: Catalog,
      meta: { title: '基础资料管理', admin: true },
    },
    { path: '/schedules', component: Schedules, meta: { title: '预约名额管理', admin: true } },
    {
      path: '/associations',
      component: Associations,
      meta: { title: '医院套餐配置', admin: true },
    },
    { path: '/:pathMatch(.*)*', redirect: '/orders' },
  ],
});
// 守卫按后端返回的 staff/role 引导登录和管理员页面；医院与角色权限由接口再次校验。
router.beforeEach(async (to) => {
  document.title = `${to.meta.title || '医生工作台'} · 健康体检`;
  try {
    await loadSession();
  } catch {
    if (!to.meta.public) return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (!to.meta.public && !session.staff)
    return { path: '/login', query: { redirect: to.fullPath } };
  if (to.meta.admin && session.staff?.role !== 'ADMIN') return '/orders';
  if (to.path === '/login' && session.staff) return '/orders';
});
// API 模块遇到 401 时发出事件，将已打开的工作台统一引导回登录页。
window.addEventListener('staff-session-expired', () => {
  if (router.currentRoute.value.path !== '/login') router.push('/login');
});
export default router;
