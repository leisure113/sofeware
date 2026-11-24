# ISSUE-INT-002 — 治理模块关闭队伍时缺少队长上下文（模拟缺陷）

> **GitHub Issue 草稿**。此问题为实验 8 模拟缺陷，用于验证“接口契约不足”类问题的记录和关闭流程；不是已确认的生产 bug。

- **标签**：`simulation`、`integration`
- **状态**：已通过适配器规避，记录为设计改进项
- **发现阶段**：实验 8 集成测试

## 环境

- 模块：team-module、governance-module
- 端口：`TeamAdminService.closeTeam(long teamId, String reason)`

## 复现步骤

1. 用户在组队模块创建队伍，队长为 A；
2. 另一用户对队伍提交举报；
3. 管理员在治理模块处理举报并令其成立；
4. 治理模块调用 `TeamAdminService.closeTeam(teamId, reason)`。

## 预期结果

治理模块只凭 `teamId` 即可关闭被举报队伍，不需要了解队长是谁。

## 实际结果

team 模块的 `TeamService.close(teamId, captainId)` 需要队长 id；`TeamAdminService` 签名没有提供队长上下文。若适配器没有额外查询能力，就无法完成关闭。

## 处理

- 集成测试的 `TeamAdminAdapter` 在创建队伍时维护 `teamId -> captainId` 映射，从而完成关闭；
- 该问题作为**模拟缺陷/接口契约缺口**记录，说明生产适配器应通过队伍查询接口解析队长，而不是让治理模块直接依赖 team 内部数据；
- 集成回归：`GovernanceModerationIntegrationTest.reportedTeamIsClosed` 通过。

## 后续改进

在完整 Web 系统中，由 team 模块提供 `TeamQueryService.findCaptain(teamId)` 或 `TeamAdminService` 的内部查询实现，治理模块不直接持有队长映射。
