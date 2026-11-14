import { createRouter, createWebHashHistory } from 'vue-router';
import { loadSession } from './api';
import { notify } from './state';
const router = createRouter({
  // 使用手册中的 Hash 路由形式，刷新 /#/hospital 等地址无需服务器额外重写规则。
  history: createWebHashHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', redirect: '/index' },
    {
      path: '/login',
      component: () => import('./views/Auth.vue'),
      meta: { public: true, title: '欢迎回来' },
    },
    {
      path: '/register',
      component: () => import('./views/Auth.vue'),
      meta: { public: true, title: '创建健康账号' },
    },
    { path: '/index', component: () => import('./views/Home.vue'), meta: { title: '健康首页' } },
    {
      path: '/persons',
      component: () => import('./views/Persons.vue'),
      meta: { title: '健康档案' },
    },
    // 五个预约步骤共用 Booking.vue；保留独立地址，页面内容由 meta.step 决定。
    ...['appointment', 'hospital', 'setmeal', 'selectdate', 'confirmorder'].map((path, index) => ({
      path: `/${path}`,
      component: () => import('./views/Booking.vue'),
      meta: {
        title: ['体检预约', '选择体检医院', '选择体检套餐', '选择预约日期', '确认预约信息'][index],
        step: index,
      },
    })),
    {
      path: '/appointmentsuccess/:orderId',
      component: () => import('./views/OrderDetail.vue'),
      meta: { title: '预约成功', success: true },
    },
    {
      path: '/appointmentlist',
      component: () => import('./views/Orders.vue'),
      meta: { title: '我的预约' },
    },
    {
      path: '/orders/:orderId',
      component: () => import('./views/OrderDetail.vue'),
      meta: { title: '预约详情' },
    },
    {
      path: '/reportlist',
      component: () => import('./views/Reports.vue'),
      meta: { title: '体检报告' },
    },
    {
      path: '/report/:orderId',
      component: () => import('./views/ReportDetail.vue'),
      meta: { title: '报告详情' },
    },
    {
      path: '/personal',
      component: () => import('./views/Personal.vue'),
      meta: { title: '个人中心' },
    },
    { path: '/:pathMatch(.*)*', redirect: '/index' },
  ],
});
// 登录状态取自后端会话；路由守卫负责页面引导，接口权限仍由后端检查。
router.beforeEach(async (to) => {
  document.title = `${to.meta.title || '体检健康'} · 云医院`;
  try {
    await loadSession();
  } catch (e) {
    if (!to.meta.public) {
      notify(e.message);
      return { path: '/login', query: { next: to.fullPath } };
    }
  }
  if (!to.meta.public && !(await loadSession()))
    return { path: '/login', query: { next: to.fullPath } };
});
export default router;
