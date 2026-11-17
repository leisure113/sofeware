# 协作规范（CONTRIBUTING）

本项目按模块拆分，所有代码通过 Git 分支协作。即使只有一名开发者，也按以下规则执行，保证提交历史清晰、可回溯。

## 1. 分支策略

```text
master        稳定版本，只接收 dev 的合并
  └─ dev      集成分支，接收各 feature 分支
       ├─ feature/user-module
       ├─ feature/content-module
       ├─ feature/team-module
       └─ feature/governance-module
```

- `master`、`dev` 不直接提交业务代码；
- 每个模块从 `dev` 切出 `feature/<module>`；
- 功能完成后合并回 `dev`，合并使用 `--no-ff` 保留合并提交；
- 里程碑时 `dev -> master`，并打 tag，例如 `v1.0`。

## 2. 提交频率

- 每完成一个可验证的小任务提交一次；
- 每天至少 push 一次；
- 不允许把一周的代码攒成一个巨型提交；
- 提交前必须保证模块能编译，测试通过；
- 一个提交只做一件事，避免“顺手重构 + 新功能 + 改文档”混在一起。

## 3. 提交信息

使用 Conventional Commits：

```text
feat(user): add campus verification flow
fix(team): reject pending application when team is full
test(content): cover hidden post comment rejection
docs: add version control experiment record
chore: init maven multi-module project
```

- 标题不超过 72 个字符；
- 正文说明“为什么改”，必要时写测试方式；
- 关联 issue 时在末尾写 `(#12)`。

## 4. 代码审查

每个 feature 合并到 `dev` 前必须审查：

- 能否编译、测试是否通过；
- 是否遵守模块边界，是否通过端口接口调用其他模块；
- 异常是否使用统一错误码，是否有空指针/边界问题；
- 并发相关代码是否有锁或事务保护；
- 是否有调试输出、硬编码密码、绝对路径；
- 是否补充或更新测试；
- 是否更新 README/接口说明。

审查要求：

- 至少 1 人审查；单人项目进行自查，并在提交信息或实验记录中写明；
- 审查意见 24 小时内处理；
- 未处理的审查意见不能合并；
- PR/MR 描述包含：改了什么、为什么改、如何测试、影响范围。

## 5. 合并与冲突

```bash
git switch dev
git pull --ff-only
git merge --no-ff feature/team-module -m "merge: team module"
mvn test
```

- 合并前先同步 `dev`；
- 冲突时手动解决，保留业务语义，不直接选“任选一边”；
- 解决冲突后必须重新运行测试；
- 共享分支禁止 force push；
- 合并完成后可删除 feature 分支，但保留合并提交。

## 6. 分支命名

- `feature/<module>`：新功能
- `bugfix/<module>-<short-desc>`：缺陷修复
- `hotfix/<short-desc>`：紧急修复
- `docs/<short-desc>`：文档

## 7. 不能提交的内容

- `.m2repo/`、`target/`、`*.class`
- `.idea/`、`*.iml`
- `*.zip` 交付包
- 密码、token、私钥
- 与本次提交无关的大文件

## 8. 完成定义（DoD）

一个功能算完成，需要同时满足：

- 代码可编译；
- 新增/修改的 JUnit 测试通过；
- 模块可独立运行；
- 接口和 README 已更新；
- 没有敏感信息和构建产物进入 Git；
- 已合并到 `dev` 并保留合并记录。
