# 共享后端

Java 17、Spring Boot 3.5、MyBatis 与 MySQL 8，为 `user-web` 和 `manage-web` 提供真实持久化数据。

- 启动类：`src/main/java/com/neusoft/health/HealthApplication.java`。
- 业务入口：`controller/HealthController.java`，保留 `/tijian` 和 `/tijiancms` 前缀。
- 业务代码：`service/`；数据库访问：`mapper/`。
- 配置与建表：`src/main/resources/application.yml`、`db/schema.sql`。
- 初始数据：`config/DemoData.java`，可通过 `SEED_ENABLED=false` 关闭；不重置已修改的密码和配置。

在终端设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 后，执行 `mvn spring-boot:run`；默认端口 8080，可通过 `SERVER_PORT` 修改。推荐初次从仓库根目录按 [运行说明](../README.md) 启动三个工程。

执行 `mvn test` 运行无需数据库的校验测试。要运行真实 MySQL 集成测试，请仅配置独立的 `tijian_test` 库，见 [测试说明](../docs/TESTING.md)。执行 `mvn package` 生成 `target/health-server-1.0.0.jar`；Windows 上先停止正在使用该 JAR 的项目服务。

[工程分层](../docs/ARCHITECTURE.md) · [接口字段](../API_CONTRACT.md) · [数据库](../docs/DATABASE.md)
