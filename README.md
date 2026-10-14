# 绿校园——校园二手物品回收与循环利用管理系统

面向校园的闲置物品发布、二手交易、回收处理与环保贡献分析系统，服务数据库系统课程设计。

技术栈：Vue 3、Vite、Element Plus、ECharts、Spring Boot 3、Java 17、MySQL 8。

`database/` 为建库、演示数据、视图和统计 SQL；`backend/` 为 Spring Boot 服务；`frontend/` 为 Vue 页面；`docs/` 为课程设计报告材料。

启动：先执行 `database/schema.sql`、`init_data.sql`、`views.sql`；在 backend 设置 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD` 后运行 `mvn spring-boot:run`；在 frontend 执行 `npm install` 和 `npm run dev`。

默认演示账号：`student01 / user-demo-hash`，管理员为 `admin / admin-demo-hash`。课程设计文档位于 `docs/`。

本地开发也可以直接用 IDEA 启动后端：

1. 先启动 MySQL。首次建库按顺序执行 `database/schema.sql`、`database/init_data.sql`、`database/views.sql`，已有数据时不要重复执行建表和演示数据脚本。
2. 在 IDEA 中将 `backend/pom.xml` 导入为 Maven 项目，使用 JDK 17。在 `CampusRecycleApplication` 的运行配置中填写数据库环境变量，例如 `DB_HOST=localhost;DB_PORT=3306;DB_NAME=campus_recycle;DB_USERNAME=root;DB_PASSWORD=你的数据库密码`。配置文件中的 `your_password` 只是占位符。
3. 点击 `CampusRecycleApplication` 的绿色运行按钮，等控制台出现 `Started CampusRecycleApplication`，后端默认监听 8080。
4. 在 `frontend` 目录执行 `npm install`（首次安装时），再执行 `npm run dev`，打开终端显示的前端地址（通常为 `http://localhost:5173`）。前端会将 `/api` 请求转发到后端的 8080 端口。

修改后端 Java 代码后，需要在 IDEA 中停止并重新运行后端。修改前端页面一般会自动刷新。不要同时使用 IDEA 和 `mvn spring-boot:run` 启动两个后端，以免端口冲突。

如果登录时出现 `Internal Server Error`，查看 IDEA 控制台的异常及 `Caused by`：`Access denied` 表示数据库账号或密码有误；`Communications link failure` 表示数据库连接失败；`Unknown column` 或表/视图不存在表示数据库结构不完整。若异常涉及 `LocalDateTime` 转换为 `Timestamp`，需要使用修正后的信誉积分时间读取代码并重启后端。
