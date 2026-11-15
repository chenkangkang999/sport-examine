# 测试与验收

## 后端测试

在 server 目录运行 `mvn test`，默认执行不需要数据库的校验测试，MySQL 集成测试默认跳过。

真实数据库集成测试使用 `TEST_DB_URL`、`TEST_DB_USERNAME`、`TEST_DB_PASSWORD`。测试代码只接受数据库名为 `tijian_test` 的连接，阻止误用业务库。测试数据在事务结束后回滚；种子初始化在测试库单独补齐。

已使用项目独立数据库时，可在 PowerShell 设置：

```powershell
$db = Get-Content .\.runtime\local-db.json -Raw | ConvertFrom-Json
$env:TEST_DB_URL = "jdbc:mysql://127.0.0.1:$($db.port)/tijian_test?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:TEST_DB_USERNAME = $db.user
$env:TEST_DB_PASSWORD = $db.password
Push-Location server
mvn test
Pop-Location
```

如果 `mvn` 不在 PATH，可使用 `MAVEN_HOME/bin/mvn.cmd` 或 IDEA 的 Maven 测试入口。不要把实际密码写入仓库或输出日志。

## HTTP 业务验收

先启动后端，然后在项目根目录运行：

```powershell
npm run test:api
node tests/api-races.mjs
node tests/api-experience.mjs
```

默认连接 `http://127.0.0.1:18080`，其他端口设置 `$env:API_BASE`。脚本只创建带测试标识的虚构账号与资源，保留记录便于排查，不清理业务数据库。

`api-smoke.mjs` 覆盖注册登录、脱敏、CSRF、金额防篡改、下单幂等、家属重复预约约束、越权、模板幂等、历史快照、容量保护、结果保存、结论维护、归档发布和取消退款。

`api-races.mjs` 覆盖两医生同时保存、保存与取消竞争、归档与保存竞争、跨账号同一体检人同时预约；检查最终版本、订单状态与名额占用，不能只看请求响应。

结果保存在 `output/test-results/api-results.json` 和 `race-results.json`。

`api-experience.mjs` 验证首页查询、预约/取消/归档通知幂等、消息分页及已读持久化、跨账号与 CSRF 拦截、机构指南、医生待办筛选、两次真实归档报告的趋势与管理员统计口径。脚本创建单独的虚构账号、机构、套餐和医生；成功后停用它创建的机构与套餐，保留历史报告供复查。结果为 `experience-api.json`，预览账号和报告标识为 `experience-fixture.json`，都位于忽略上传的输出目录。

## 浏览器验收

```powershell
npm install
npm run test:experience
npm run test:e2e
```

使用本机已安装 Edge 的无界面测试模式。默认用户端 8081，管理端 8089，可用 `USER_ORIGIN`、`MANAGE_ORIGIN` 覆盖。首次运行新体验浏览器测试前，先运行 `test:experience` 生成独立账号及两份归档报告；已经生成后可直接重复浏览器检查。测试通过真实页面完成家属预约、医生分项录入和归档，再在用户手机布局查看报告；另检查管理员各模块、大字模式、真实趋势和移动端无横向溢出。

页面截图保存在 `output/screenshots`，失败跟踪保存在 `output/test-results/browser`，HTML报告保存在 `output/playwright-report`。

## 持久化复验

接口脚本把一个已归档测试订单记录到 `output/test-results/persistence.json`。正常停止并重新启动后端后，以该文件中的测试账号登录（密码 Health123!），查询同一订单和报告，确认它们仍存在。后端会话在重启后失效属于预期行为，数据不应丢失。
