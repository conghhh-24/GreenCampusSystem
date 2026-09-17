# 绿校园——校园二手物品回收与循环利用管理系统

面向校园的闲置物品发布、二手交易、回收处理与环保贡献分析系统，服务数据库系统课程设计。

技术栈：Vue 3、Vite、Element Plus、ECharts、Spring Boot 3、Java 17、MySQL 8。

`database/` 为建库、演示数据、视图和统计 SQL；`backend/` 为 Spring Boot 服务；`frontend/` 为 Vue 页面；`docs/` 为课程设计报告材料。

启动：先执行 `database/schema.sql`、`init_data.sql`、`views.sql`；在 backend 设置 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD` 后运行 `mvn spring-boot:run`；在 frontend 执行 `npm install` 和 `npm run dev`。

默认演示账号：`admin`、`student01`。课程设计文档位于 `docs/`。
