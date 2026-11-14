import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import 'element-plus/dist/index.css';
import './style.css';
import App from './App.vue';
import router from './router';
// 管理端独立挂载 Vue 与路由；Element Plus 提供表单、表格和中文交互提示。
createApp(App).use(router).use(ElementPlus, { locale: zhCn }).mount('#app');
