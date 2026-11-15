import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import './style.css';
import './care-theme.css';
// 单页应用入口：App 提供公共页头、底部导航，router 按地址切换业务页面。
createApp(App).use(router).mount('#app');
