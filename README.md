# 绿校园——校园二手物品回收与循环利用管理系统

交易评价更新：买家提交评价后，后端在同一事务中保存评价、将订单状态更新为 `REVIEWED`（已评价）并按规则调整卖家信誉分；订单列表返回评分、内容及是否允许评价。重启更新后的后端会自动扩展订单状态，并将已有评价的已完成订单修正为已评价，不会重复计分。必须在 IDEA 中停止并重新启动后端，再刷新前端、重新登录；仅刷新网页不会加载新的后端逻辑。也可在停止后端时手动执行 `database/trade_feedback_status.sql`，不必重建数据库。

面向校园的闲置物品发布、二手交易、回收处理与环保贡献分析系统，服务数据库系统课程设计。

技术栈：Vue 3、Vite、Element Plus、ECharts、Spring Boot 3、Java 17、MySQL 8。

`database/` 为建库、演示数据、视图和统计 SQL；`backend/` 为 Spring Boot 服务；`frontend/` 为 Vue 页面；`docs/` 为课程设计报告材料。

启动：先执行 `database/schema.sql`、`init_data.sql`、`views.sql`；在 backend 设置 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD` 后运行 `mvn spring-boot:run`；在 frontend 执行 `npm install` 和 `npm run dev`。

默认演示账号：`student01 / user-demo-hash`，管理员为 `admin / admin-demo-hash`。课程设计文档位于 `docs/`。

本次验收完善后，需要重启后端并刷新前端，重新登录。现有数据库不必重建：后端首次启动会将旧明文密码迁移为 PBKDF2 哈希，账号原密码保持不变；新演示脚本已直接保存哈希。登录使用服务端会话，30分钟无操作后过期，重启后端也需要重新登录。前端通过 Vite 的同源代理访问接口，请保持使用同一个前端地址，不要混用 localhost 和 127.0.0.1。

统计看板已提供累计回收重量、完成回收次数、物品完成率、平均处理天数、方式占比、回收点重量和近12个月趋势。统计口径见 `docs/08-数据统计分析说明.md`；功能模块图及可编辑源文件位于 `docs/report/function-modules.*`。当前环境未连接运行中的 MySQL，仍需在本机真实数据上演练完整流程。Word 报告已插入功能图，但本环境缺少 LibreOffice，未生成更新后的 PDF，也未核验新分页。

本地开发也可以直接用 IDEA 启动后端：

1. 先启动 MySQL。首次建库按顺序执行 `database/schema.sql`、`database/init_data.sql`、`database/views.sql`，已有数据时不要重复执行建表和演示数据脚本。
2. 在 IDEA 中将 `backend/pom.xml` 导入为 Maven 项目，使用 JDK 17。在 `CampusRecycleApplication` 的运行配置中填写数据库环境变量，例如 `DB_HOST=localhost;DB_PORT=3306;DB_NAME=campus_recycle;DB_USERNAME=root;DB_PASSWORD=你的数据库密码`。配置文件中的 `your_password` 只是占位符。
3. 点击 `CampusRecycleApplication` 的绿色运行按钮，等控制台出现 `Started CampusRecycleApplication`，后端默认监听 8080。
4. 在 `frontend` 目录执行 `npm install`（首次安装时），再执行 `npm run dev`，打开终端显示的前端地址（通常为 `http://localhost:5173`）。前端会将 `/api` 请求转发到后端的 8080 端口。

修改后端 Java 代码后，需要在 IDEA 中停止并重新运行后端。修改前端页面一般会自动刷新。不要同时使用 IDEA 和 `mvn spring-boot:run` 启动两个后端，以免端口冲突。

出售、赠送的交接时间现改为具体日期（YYYY-MM-DD）和半小时时间点，前后端均要求晚于当前时间（北京时间）。重启后端会自动为 `items` 和 `trade_orders` 增加 `meeting_date DATE` 字段，已有数据库不需要重新建表。旧 `meeting_day` 列已删除，星期值已存档，无法恢复准确日期；旧物品须在“我的物品 → 编辑物品”补选日期并重新审核，才能接受新交易。已有订单业务记录继续保留。

数据库字段的实际用途、缺少页面入口的功能和预留字段见 `docs/13-数据库字段用途核对.md`。可在数据库查询控制台运行只读的 `database/field_usage_check.sql` 查看现有数据分布；不要重复执行初始化脚本来更新已有数据。

未用字段已从当前代码和新建库脚本移除。已有数据库请停止后端，依次执行 `database/remove_unused_fields.sql`（先存档旧字段值，再删除6列，仅执行一次，遇错停止），完成后启动更新后的后端并刷新前端。应用不会自动执行删列。用户表 `users.phone` 保留。

如果登录时出现 `Internal Server Error`，查看 IDEA 控制台的异常及 `Caused by`：`Access denied` 表示数据库账号或密码有误；`Communications link failure` 表示数据库连接失败；`Unknown column` 或表/视图不存在表示数据库结构不完整。若异常涉及 `LocalDateTime` 转换为 `Timestamp`，需要使用修正后的信誉积分时间读取代码并重启后端。
