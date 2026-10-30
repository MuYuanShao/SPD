# 本地 ISPD V105 迁移执行记录

日期：2026-10-30。按用户要求，对本机 MySQL 3306 的 ISPD（实际 schema 名 ispd）执行 Flyway 升级。

## 执行结果

- 升级前：V101。
- 按顺序成功应用：V102、V103、V104、V105。
- 升级后：V105，失败迁移记录为 0；Flyway 校验成功。
- V105 的四个来源关联字段、两列组合唯一索引 uk_license_mcp_source 已生效；license_name 长度为 255。
- 已批准 MCP 来源证照为 0 条，因此本次回填来源证照为 0 条。
- 原证照 1 条、原附件 1 条，升级后数量保持不变。

未执行 repair，未修改历史校验和。预检仅忽略预期的 pending 状态，然后由 Flyway 正常迁移至明确目标 105。

## 备份及审计

迁移前已创建包含表、过程、触发器和事件的完整数据库备份：

`D:/SPD-PR/output/database-backups/ISPD-before-V105-20261030-141750.sql`

大小：2473984 字节。

SHA-256：`7ebbdad2213b1795b8953a74861f8e0ed4a007c222e3252cf54e8156c889e93b`。

执行前再次校验了备份文件摘要。备份包含业务数据，仅保存在 Git 忽略的本地 output 目录。

- [迁移日志](D:/SPD-PR/output/database-backups/flyway-V105-migrate.log)
- [迁移后核验](D:/SPD-PR/output/database-backups/flyway-V105-verification.log)

本次仅执行数据库迁移；检查时本地 1818 后端和 1820 前端未运行，没有额外启动应用或业务定时任务。
