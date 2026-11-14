# 用户端前端

本目录是可以运行的 Vue 3 用户端。页面沿用实践手册的移动端布局、原有图片及青绿色风格，重点实现注册登录、本人或家属预约、订单查询和报告查看。原始 HTML 页面保留在仓库根目录，可用于对照页面结构。

## 从哪里开始阅读

| 文件或目录 | 职责与修改入口 |
| --- | --- |
| `src/main.js` | 创建 Vue 应用、挂载路由和公共样式 |
| `src/App.vue` | 公共页头、返回按钮、底部导航和全局提示 |
| `src/router.js` | Hash 路由、页面标题和后端会话登录守卫 |
| `src/api.js` | Axios 请求、会话读取、CSRF 和统一错误处理 |
| `src/state.js` | 用户状态、预约草稿、提示消息、日期与金额格式化 |
| `src/style.css` | 公共组件及首页、登录、个人中心等页面样式 |
| `src/views/` | 各业务页面；部分页面在自身 `<style scoped>` 中维护局部样式 |
| `src/components/` | 加载及失败状态、确认弹窗、图标等复用组件 |
| `public/img/` | 原项目图片，通过 `/img/...` 引用 |
| `src/assets/fontawesome-webfont.woff2` | `FaIcon.vue` 使用的本地图标字体资源 |
| `vite.config.js` | 用户端 8081 端口和后端开发代理 |

先阅读 `App.vue → router.js → views/Booking.vue → api.js → state.js`，可理解页面跳转、数据请求和预约状态如何配合。

## 预约页面为什么共用一个文件

手册中的体检人、医院、套餐、日期和订单确认页面分别保留 `/appointment`、`/hospital`、`/setmeal`、`/selectdate`、`/confirmorder` 五个路由。它们使用同一个 `Booking.vue`，由 `route.meta.step` 决定当前显示内容。

预约草稿放在当前标签页的 `sessionStorage` 中。切换医院会清除之前的套餐和日期；切换套餐会清除日期。到确认步骤时生成 `requestId`，请求失败重试沿用这个编号，供后端防止重复下单。价格、排班和剩余名额来自后端，提交时只发送选择的编号及日期。

## 本地启动

需要 Node.js 和已经运行的项目后端。完整启动方式见[仓库运行说明](../README.md)；仅启动此端时，在本目录执行：

```powershell
npm ci
$env:VITE_API_TARGET = 'http://127.0.0.1:8080'
npm run dev
```

浏览器打开 `http://127.0.0.1:8081/`。如果根目录启动脚本显示后端使用 18080，则将 `VITE_API_TARGET` 改为 `http://127.0.0.1:18080`，再启动 Vite。该变量由开发服务器读取，修改后需重启。

```powershell
npm run build
```

构建产物在 `dist/`。部署时需由 Web 服务器将 `/tijian` 请求转发到后端；直接双击 `dist/index.html` 无法完成后端业务请求。

## 修改页面时的约定

- 首页健康评估保留手机原生滑动，并支持电脑鼠标拖动、滚轮和方向键。
- 公共页头、导航在 `App.vue` 修改；业务内容在对应 `views/` 页面修改。
- 图片和原有栏目顺序优先参考手册及原始 HTML，新增功能使用现有字号、留白和配色。
- 登录身份由后端 Cookie 会话核验，不把预约草稿当成登录状态。
- 问诊、商城等原型入口有明确提示，当前没有接入相应外部业务。

逐页对应、前端验收及接口说明见 [前端导读](../docs/FRONTEND_GUIDE.md)、[前端验收清单](../docs/FRONTEND_CHECKLIST.md)和[接口文档](../API_CONTRACT.md)。
