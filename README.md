# 院内 SPD 系统

基于《院内SPD系统产品需求文档》的初始工程骨架。

## 端口

- 后端 Spring Boot：`1818`
- 前端 Vue3/Vite：`1820`

## 启动

```powershell
npm install
npm run dev:frontend
```

```powershell
npm run dev:backend
```

后端健康检查：`http://localhost:1818/api/health`

Windows 可通过桌面的“SPD 后端控制”快捷方式启动、重启、关闭或查看后端状态，也可使用命令：

```powershell
npm run backend:start
npm run backend:restart
npm run backend:stop
npm run backend:status
```

## 数据库

默认连接 MySQL：

- 数据库：`ISPD`
- 地址：`localhost:3306`
- 账号：`admin`
- 密码：`admin123`

初始化脚本：[01-create-ispd-database.sql](D:/SPD-PR/backend/src/main/resources/db/init/01-create-ispd-database.sql)

## 代码规范

- [SPD Code Standards](D:/SPD-PR/docs/code-standards.md)
