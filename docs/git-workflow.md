# Git 工作流

## 主控分支

- `main` 是项目主控分支，只保存已经整理完成、可作为基线的代码。
- 日常功能开发、问题修复、试验性调整应从 `main` 新建分支。
- 分支命名建议：
  - `feature/功能名称`
  - `fix/问题名称`
  - `docs/文档名称`
  - `chore/维护事项`

## 分支迭代流程

1. 从 `main` 更新本地基线。
2. 新建本次迭代分支。
3. 完成代码或文档修改。
4. 运行对应验证命令。
5. 在 `CHANGELOG.md` 增加本次修改记录。
6. 提交代码并合并回 `main`。

## 修改记录要求

每次分支迭代或代码修改都需要在 `CHANGELOG.md` 中记录：

- 修改日期
- 分支名称
- 修改内容
- 影响范围
- 验证方式
- 负责人

## 常用命令

```powershell
git switch main
git pull
git switch -c feature/example

# 修改完成后
git status
git add .
git commit -m "feat: describe change"
```
