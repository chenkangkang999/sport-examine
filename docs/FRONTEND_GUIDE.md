# 前端开发导读与手册对照

本项目以《东软体检项目系统-实践手册(6)》为课程业务和页面结构依据。前端是展示重点：保留原图片、青绿色移动端风格、栏目顺序、顶部返回栏和底部导航，同时让预约、订单和报告使用后端数据。

手册第 4–6 页展示用户端原型，第 6–7 页展示医生端查询和报告编辑原型；第 18–22 页、第 29–32 页分别介绍两端前端工程。后续维护应先查本页中的源码位置，再对照原型修改。

## 1. 工程入口与阅读顺序

| 内容           | 用户端                                         | 医生 / 管理端                                    |
| -------------- | ---------------------------------------------- | ------------------------------------------------ |
| 工程目录       | [`user-web/`](../user-web/)                    | [`manage-web/`](../manage-web/)                  |
| 开发地址       | `http://127.0.0.1:8081/`                       | `http://127.0.0.1:8089/`                         |
| 应用入口       | [`src/main.js`](../user-web/src/main.js)       | [`src/main.js`](../manage-web/src/main.js)       |
| 页面框架       | [`src/App.vue`](../user-web/src/App.vue)       | [`src/App.vue`](../manage-web/src/App.vue)       |
| 页面路由       | [`src/router.js`](../user-web/src/router.js)   | [`src/router.js`](../manage-web/src/router.js)   |
| 请求封装       | [`src/api.js`](../user-web/src/api.js)         | [`src/api.js`](../manage-web/src/api.js)         |
| 公共样式       | [`src/style.css`](../user-web/src/style.css)   | [`src/style.css`](../manage-web/src/style.css)   |
| 开发端口和代理 | [`vite.config.js`](../user-web/vite.config.js) | [`vite.config.js`](../manage-web/vite.config.js) |

两端都使用 Vue 3、Vue Router、Axios 和普通 JavaScript；管理端另使用 Element Plus。手册示例采用 Vue CLI，本工程采用 Vite，因此开发命令是 `npm run dev`、构建命令是 `npm run build`。`main.js → App.vue → RouterView → views` 的阅读顺序与手册一致。

路由采用 Hash 模式，例如用户端首页为 `http://127.0.0.1:8081/#/index`。下表的 `/index` 等写法均指 `#` 后面的路由。完整环境准备和启动方法见[项目说明](../README.md)，请求参数见[接口文档](../API_CONTRACT.md)。

## 2. 原有 13 个用户页面迁移

根目录原 HTML、`css/`、`js/`、`img/` 和 `framework/` 保留为早期静态版本，便于课程前后对比。实际运行的 Vue 页面位于 `user-web/`，修改旧 HTML 不会改变 Vue 页面。一个 Vue 文件可以按路由渲染多个页面，不必重复复制相同逻辑。

| 原页面                    | 当前路由                       | Vue 文件                                                   | 手册页 | 页面结构与当前功能                                                              |
| ------------------------- | ------------------------------ | ---------------------------------------------------------- | ------ | ------------------------------------------------------------------------------- |
| `login.html`              | `/login`                       | [Auth.vue](../user-web/src/views/Auth.vue)                 | 4      | 蓝绿渐变背景、白色登录框、注册 / 忘记密码入口；提交手机号和密码，由后端验证登录 |
| `register.html`           | `/register`                    | [Auth.vue](../user-web/src/views/Auth.vue)                 | 4      | 返回栏、欢迎语、行式资料表单和完成按钮；校验手机号、两次密码及个人资料后注册    |
| `index.html`              | `/index`                       | [Home.vue](../user-web/src/views/Home.vue)                 | 4      | 云医院标志、宣传图、六项服务、报告、推荐医生、健康评估和资讯；评估可横向滚动    |
| `appointment.html`        | `/appointment`                 | [Booking.vue](../user-web/src/views/Booking.vue)，`step=0` | 5      | 原预约宣传图和本人 / 家属入口；选择数据库中的体检人档案                         |
| `hospital.html`           | `/hospital`                    | [Booking.vue](../user-web/src/views/Booking.vue)，`step=1` | 4      | 医院标题、左图右信息、电话和位置链接；医院列表来自后端                          |
| `setmeal.html`            | `/setmeal`                     | [Booking.vue](../user-web/src/views/Booking.vue)，`step=2` | 4      | 套餐条目、详情展开表格；按医院和体检人性别加载套餐                              |
| `selectdate.html`         | `/selectdate`                  | [Booking.vue](../user-web/src/views/Booking.vue)，`step=3` | 5      | 月历、日期余量、选择状态和下一步；休息、已满、过去和未开放日期不可选            |
| `confirmorder.html`       | `/confirmorder`                | [Booking.vue](../user-web/src/views/Booking.vue)，`step=4` | 5      | 体检人、日期、机构、套餐分节展示和底部金额 / 确认支付；核对信息后提交预约       |
| `appointmentsuccess.html` | `/appointmentsuccess/:orderId` | [OrderDetail.vue](../user-web/src/views/OrderDetail.vue)   | 5      | 青色成功区、勾选标志、查看订单和继续为家人预约；从后端读取订单                  |
| `appointmentlist.html`    | `/appointmentlist`             | [Orders.vue](../user-web/src/views/Orders.vue)             | 5      | 日期、套餐、体检人和机构条目；按状态 / 体检人筛选，符合条件的预约可取消         |
| `personal.html`           | `/personal`                    | [Personal.vue](../user-web/src/views/Personal.vue)         | 5      | 渐变头像区、姓名账号、统一字体菜单；主要功能靠前，退出登录最后                  |
| `reportlist.html`         | `/reportlist`                  | [Reports.vue](../user-web/src/views/Reports.vue)           | 6      | 原报告宣传图、日期与机构条目；按体检人查看已归档报告                            |
| `report.html`             | `/report/:orderId`             | [ReportDetail.vue](../user-web/src/views/ReportDetail.vue) | 6      | 总检结论 / 报告详情双标签、蓝色分节标题、红色异常标志、结果和参考范围           |

补充功能仍放在同一套页面框架中：

| 功能           | 入口与文件                                                                   | 说明                                                 |
| -------------- | ---------------------------------------------------------------------------- | ---------------------------------------------------- |
| 本人及家人档案 | `/persons`，[Persons.vue](../user-web/src/views/Persons.vue)                 | 列表和弹出表单完成新增、编辑、移除，证件号码脱敏展示 |
| 预约详情       | `/orders/:orderId`，[OrderDetail.vue](../user-web/src/views/OrderDetail.vue) | 查看订单快照、状态和金额；已归档订单可进入报告       |
| 资料与密码修改 | 个人中心 → 账号资料，[Personal.vue](../user-web/src/views/Personal.vue)      | 同页标签切换，不是新增路由                           |

当前页面效果见[首页](images/user-home.png)、[个人中心](images/user-personal.png)、[医院列表](images/user-hospitals.png)、[用户报告](images/user-report.png)。截图记录外观，实际日期与数据以运行结果为准。

## 3. 预约页面为什么共用一个文件

[`router.js`](../user-web/src/router.js) 用 `meta.step` 把五条路由对应到 `Booking.vue` 的五个步骤：

```text
本人 / 家属 → 体检人 → 医院 → 套餐 → 日期 → 确认支付 → 预约成功
              personId   hpId   smId   orderDate       orderId
```

[`state.js`](../user-web/src/state.js) 中的 `draft` 保存当前选择，`setDraft()` 同步到 `sessionStorage`，刷新后可恢复同一标签页草稿。`load()` 检查前置步骤，缺少体检人、医院或套餐时引导返回预约入口；改变前一步选择时会清除已不适用的后续选择。

`api.hospitals()`、`api.meals()`、`api.meal()` 和 `api.calendar()` 分别读取机构、套餐、检查项目和日期余量。确认页生成 `requestId`；提交时禁用按钮，成功后清除草稿，并用后端返回的 `orderId` 跳转。前端负责展示金额，实际价格、日期名额和重复预约限制由后端再次校验。

## 4. 医生端与管理端对照

管理端以手册的蓝灰色顶栏、左查询右表格、左客户信息右报告编辑为基础。医生处理所属医院报告，管理员在同一工程进入基础资料模块。

| 手册内容 / 补充功能               | 当前路由                                                                             | 文件                                                         | 阅读与展示重点                                                           |
| --------------------------------- | ------------------------------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------------------ |
| 医生登录，手册 `Login`            | `/login`                                                                             | [Login.vue](../manage-web/src/views/Login.vue)               | 工号登录、错误提示和登录中状态                                           |
| 第 6 页查询，手册 `OrdersList`    | `/orders`                                                                            | [Orders.vue](../manage-web/src/views/Orders.vue)             | 左侧手机号、姓名、性别、套餐、日期、归档状态筛选；右侧记录和分页         |
| 第 7 页编辑，手册 `OrdersContent` | `/orders/:id`                                                                        | [Report.vue](../manage-web/src/views/Report.vue)             | 左侧预约资料；右侧分组结果、参考范围、异常、分项保存、内联结论表单和归档 |
| 医院 / 套餐 / 检查项 / 医生维护   | `/catalog/hospitals`、`/catalog/setmeals`、`/catalog/checkitems`、`/catalog/doctors` | [Catalog.vue](../manage-web/src/views/Catalog.vue)           | 共用 CRUD 页面，按资源决定字段；历史关联资料采用停用                     |
| 医院套餐关联                      | `/associations`                                                                      | [Associations.vue](../manage-web/src/views/Associations.vue) | 选择医院，为该院配置可预约套餐                                           |
| 每日名额                          | `/schedules`                                                                         | [Schedules.vue](../manage-web/src/views/Schedules.vue)       | 按医院和日期范围读取 / 调整容量                                          |

`Orders.vue` 中性别的“全部”表示不限定性别，应保留。`Report.vue` 的 `baselines` / `dirty` 判断未保存修改，`reportVersion` 配合后端避免多人保存互相覆盖；版本冲突时提示重新加载，已归档或已取消报告只读。数值异常由后端根据参考值判定，描述类结果由医生标记。

页面效果见[医生预约查询](images/manage-orders.png)与[医生报告编辑](images/manage-report.png)。

## 5. 公共代码分别负责什么

| 文件                                                                                                 | 职责 / 修改位置                                                                     |
| ---------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| [用户端 App.vue](../user-web/src/App.vue)                                                            | 顶部标题 / 返回按钮、首页云医院标志、底部导航、全局消息；公共顶栏不用在每页重复添加 |
| [用户端 state.js](../user-web/src/state.js)                                                          | 用户状态、消息、预约草稿、日期 / 金额 / 性别显示工具                                |
| [用户端 api.js](../user-web/src/api.js)                                                              | `/tijian` 前缀、Axios、后端会话、CSRF 请求头、超时及统一错误处理                    |
| [用户端 router.js](../user-web/src/router.js)                                                        | 路由、懒加载、预约步骤、登录守卫                                                    |
| [AsyncState.vue](../user-web/src/components/AsyncState.vue)                                          | 加载、失败重试、无记录三种状态；业务页面通过属性和插槽传入文字                      |
| [ConfirmDialog.vue](../user-web/src/components/ConfirmDialog.vue)                                    | 取消预约、移除档案、退出登录等确认弹窗                                              |
| [Icon.vue](../user-web/src/components/Icon.vue)、[FaIcon.vue](../user-web/src/components/FaIcon.vue) | SVG 和本地图标字体                                                                  |
| [管理端 App.vue](../manage-web/src/App.vue)                                                          | 医生姓名、退出入口、管理员导航、页面容器                                            |
| [管理端 api.js](../manage-web/src/api.js)                                                            | `/tijiancms` 请求、医生 / 管理员会话、Element Plus 提示、显示工具                   |
| [管理端 router.js](../manage-web/src/router.js)                                                      | 登录、管理员路由守卫，以及会话失效后的跳转                                          |

登录身份来自后端会话，HttpOnly Cookie 由浏览器自动携带；`sessionStorage` 中的预约草稿不代表已登录。管理端和用户端身份分别保存、分别退出。隐藏前端菜单只是界面控制，后端仍会逐项校验角色、账号和医院范围。

## 6. 以后修改页面时从哪里下手

1. **先定位页面。** 用第 2 节定位 `views/*.vue`，先看 `<template>`，再看相关 `ref`、`computed`、事件和接口。预约五个页面先确认 `step`。
2. **公共外观改公共样式。** 用户端 `src/style.css` 包含 CSS 重置、页面框架、首页和个人中心；预约、列表、订单和报告等页面还在各自 `.vue` 底部维护 `scoped` 样式。管理端外观集中在 `manage-web/src/style.css`，与手册相近的布局使用 `manual-*` 类名。
3. **保留原型比例。** 用户端 `.app-shell` 在电脑上最大宽度 480px、居中展示，手机上随屏幕收缩。`cqw` 以页面容器宽度为基准；改字体 / 图文间距后同时检查手机和桌面，确认固定顶栏、底栏和付款栏对齐。
4. **复用本地素材。** Vue 实际读取 `user-web/public/img/`；根目录 `img/` 属于静态参考素材。图标字体在 `user-web/src/assets/`。修改前确认是哪一份，不使用仅本机可访问的图片路径。
5. **保持交互细节。** 健康评估保留 `overflow-x: auto`、卡片不收缩和相关事件，支持手机滑动、鼠标拖动 / 滚轮及方向键。个人中心链接和按钮使用同一套字体，退出登录放最后。
6. **使用后端数据。** 医院、套餐价格 / 项目、余量、订单和报告由 API 返回。不要在模板用固定订单、金额或手册旧日期代替业务接口；历史订单快照与当前基础资料不同属于正常业务行为。
7. **构建并打开查看。** 在对应前端目录运行 `npm run build`，按[前端验收清单](FRONTEND_CHECKLIST.md)检查页面。不直接修改 `dist/`，后续构建会覆盖它。

## 7. 当前范围与保留入口

已贯通注册登录、本人 / 家属档案、选院选套餐选日、预约与取消、医生录入 / 结论维护 / 归档、用户报告，以及管理员资料和名额维护。

为延续手册页面，首页六项服务、医生介绍、评估 / 资讯、商城 / 发现及个人中心部分服务入口保留展示。其中团检预约进入现有预约流程；问诊、慢病管理、护理、医生咨询、商城等外部业务未接入，点击会说明当前范围。健康评估只有卡片滚动与入口提示，没有问卷或评分服务；忘记密码没有短信找回。医院卡片提供电话和地图链接，初始化资料为课程虚构数据。

支付使用课程模拟交易，点击“确认支付”不连接真实支付渠道。新加的家属、资料和状态提示用于形成完整业务流程，保持原配色和页面层次。

前端讲解建议顺序为：原型与组件划分 → 路由 / 表单绑定 → 草稿 / 接口联动 → 日期与列表状态 → 医生编辑 / 报告发布 → 手机和电脑布局。数据库规则见[数据库说明](DATABASE.md)，完整答辩步骤见[演示步骤](DEMO.md)。
