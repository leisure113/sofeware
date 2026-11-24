# ISSUE-INT-001 — MUTED 用户在内容/组队模块返回错误码不一致

> **GitHub Issue 草稿**。当前环境没有 `gh` CLI、没有 GitHub token，HTTPS API 不可达，因此先以仓库内草稿记录；请在 GitHub 网页创建同名 Issue 后回填编号和 URL。

- **标签**：`bug`、`integration`
- **状态**：已修复
- **发现阶段**：实验 8 集成测试
- **发现用例**：
  - `UserTeamIntegrationTest.mutedUserIsRejectedWithMutedCodeAcrossModules`
  - `GovernanceModerationIntegrationTest.reportedUserIsMutedAndCannotApply`

## 环境

- JDK 21.0.10（--release 17）
- Maven 3.10.0
- 模块：user-module、content-module、team-module、governance-module

## 复现步骤

1. 注册并激活用户 A；
2. 管理员把 A 的状态改为 `MUTED`；
3. 用 A 调用内容模块发帖，或调用组队模块申请队伍；
4. 观察返回的错误码。

## 预期结果

返回 `USER-403-1`（`USER_MUTED`），与业务规则“禁言用户不能发帖/申请”一致。

## 实际结果

返回 `USER-403`（`USER_NOT_ACTIVE`）。因为 user 模块的 `isActive()` 对 `MUTED` 返回 false，而 content/team 先判断 `isActive`、后判断 `isMuted`，`USER_MUTED` 分支永远走不到。

## 证据

集成测试首次执行结果：

```text
Tests run: 10, Failures: 2, Errors: 0, Skipped: 0
UserTeamIntegrationTest.mutedUserIsRejectedWithMutedCodeAcrossModules:
  expected: <USER-403-1> but was: <USER-403>
GovernanceModerationIntegrationTest.reportedUserIsMutedAndCannotApply:
  expected: <USER-403-1> but was: <USER-403>
```

## 修复

- 修改 `ContentServiceImpl.requireActiveUser` 和 `TeamServiceImpl.requireActiveUser`：先判断 `isMuted`，再判断 `isActive`；
- 修复提交：`b1e5c0d`；
- 集成回归：10/10 通过。

## 回归测试

- `UserTeamIntegrationTest.mutedUserIsRejectedWithMutedCodeAcrossModules`
- `GovernanceModerationIntegrationTest.reportedUserIsMutedAndCannotApply`
