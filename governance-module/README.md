# Governance Module — 举报治理模块核心代码

课程设计《同游社——校园游戏同好与开黑搭子社区》的**举报与后台治理**模块实现。
本模块只实现治理一个模块，不依赖 Spring、MySQL 和其他模块的实现，可独立编译、测试、运行。

## 1. 模块功能

- 用户提交举报：目标可以是帖子、评论、用户或队伍；可填写原因、描述和最多 3 条证据。
- 重复举报拦截：同一用户对同一目标已存在 PENDING 举报时拒绝再次提交。
- 举报人可在管理员处理前补充证据（`addEvidence`），合计最多 3 条；仅举报人本人可追加。
- 管理员处理举报：校验管理员身份，举报必须存在且为 PENDING。
- 举报成立（`valid=true`）时按目标类型调用对应模块的管理接口：
  - `POST` → 内容模块隐藏帖子
  - `COMMENT` → 内容模块隐藏评论
  - `USER` → 用户模块禁言用户（普通成立举报不直接封号）
  - `TEAM` → 队伍模块关闭队伍
- 举报不成立（`valid=false`）时直接置为 `REJECTED`，不触发任何目标处置动作。
- 每次处理写一条管理员审计日志，并向举报人发送 `REPORT_RESULT` 通知。
- 提供待处理举报列表（按时间倒序）和按状态/目标类型的统计概览。

## 2. 目录结构

```text
governance-module/
├─ pom.xml
├─ README.md
├─ run.bat / test.bat / build.bat
├─ src/main/java/com/coplay/governance/
│  ├─ api/            GovernanceService、AdminQueryService、UserQueryService、
│  │                  ContentAdminService、UserAdminService、TeamAdminService、NotificationService
│  ├─ domain/         Report、ReportStatus、ReportTargetType、AdminLog
│  ├─ dto/            SubmitReportCommand、HandleReportCommand、ReportDetail、ReportBrief、StatsOverview
│  ├─ repository/     ReportRepository、AdminLogRepository、
│  │                  InMemoryReportRepository、InMemoryAdminLogRepository
│  ├─ support/        BizException、ErrorCode
│  ├─ service/        GovernanceServiceImpl
│  └─ GovernanceModuleDemo.java
└─ src/test/java/com/coplay/governance/
   ├─ GovernanceServiceTest.java        基线用例（12）
   ├─ GovernanceTestSupport.java        共享夹具（固定 Clock、内存仓储与 Fake 端口）
   ├─ SubmitReportTest.java             提交举报正常/异常/边界（30）
   ├─ HandleReportTest.java             处理举报、目标处置与副作用（20）
   ├─ AddEvidenceTest.java              补充证据（TDD，12）
   ├─ ReportQueryTest.java              待处理列表与统计（7）
   ├─ DomainModelTest.java              领域对象与 DTO 契约（12）
   ├─ InMemoryRepositoryTest.java       内存仓储排序/计数（8）
   └─ fake/           FakeAdminQueryService、FakeUserQueryService、FakeContentAdminService、
                      FakeUserAdminService、FakeTeamAdminService、FakeNotificationService
```

## 3. 模块间接口调用

`GovernanceServiceImpl` 只依赖端口，不依赖其他模块的实现：

```text
GovernanceServiceImpl
 ├─ AdminQueryService       用户/权限模块  校验操作人是否为管理员
 ├─ UserQueryService        用户模块      校验举报人是否 ACTIVE
 ├─ ContentAdminService     内容模块      隐藏帖子 / 隐藏评论
 ├─ UserAdminService        用户模块      禁言用户 / 封禁用户
 ├─ TeamAdminService        队伍模块      关闭队伍
 ├─ NotificationService     通知模块      通知举报人处理结果
 ├─ ReportRepository                       举报持久化（本模块内存实现）
 └─ AdminLogRepository                     管理员审计日志（本模块内存实现）
```

真实项目中只需把两个 `InMemory*Repository` 换成 MyBatis 实现，把 Fake 换成调用用户/内容/队伍/通知模块的 Spring Bean，`GovernanceServiceImpl` 不需要改。

## 4. 业务规则

### 4.1 提交举报

- 举报人必须存在且处于 ACTIVE 状态。
- `targetType` 不能为空，`targetId` 必须大于 0，否则 `INVALID_TARGET`。
- `reason` 去除首尾空格后长度必须在 2—100 之间，否则 `INVALID_REASON`。
- `description` 可为空，最多 500 字符，否则 `INVALID_REASON`。
- `evidence` 可为空，最多 3 条，否则 `TOO_MANY_EVIDENCE`。
- 同一举报人对同一 `(targetType, targetId)` 已有 PENDING 举报时拒绝，返回 `DUPLICATE_REPORT`。
- 校验通过后创建 PENDING 举报，`createdAt` 取注入的 `Clock`。

### 4.2 补充证据

- 只有原举报人可以补充证据，其他人返回 `NOT_REPORTER`。
- 举报必须存在（`REPORT_NOT_FOUND`）且为 PENDING（`REPORT_NOT_PENDING`）。
- 追加后证据总数不能超过 3 条，否则 `TOO_MANY_EVIDENCE`；`null` 证据列表视为空列表。
- 成功时返回更新后的 `ReportDetail`，持久化到仓储；不改变状态、不写审计日志、不发通知。

### 4.3 处理举报

- 操作人必须是管理员，否则 `NOT_ADMIN`；操作人 id 非法时为 `INVALID_OPERATOR`。
- 举报必须存在（`REPORT_NOT_FOUND`）且为 PENDING（`REPORT_NOT_PENDING`）。
- `valid=false`：置为 `REJECTED`，写审计日志、通知举报人，不调用任何目标模块接口。
- `valid=true`：先执行目标处置动作（见第 1 节），成功后再置为 `RESOLVED`，写日志并通知。
- 目标处置动作抛异常时整体返回 `TARGET_ACTION_FAILED`，举报保持 PENDING，不写日志、不发通知。

### 4.4 状态机

```text
            handle(valid=true)  + target action
PENDING ───────────────────────────────────────→ RESOLVED
   │
   └──────── handle(valid=false) ──────────────→ REJECTED
```

`RESOLVED` / `REJECTED` 为终态，不能再次处理。

### 4.5 错误码

| 错误码 | 常量 | 含义 |
|---|---|---|
| GOV-404 | `REPORT_NOT_FOUND` | 举报不存在 |
| GOV-403 | `NOT_ADMIN` | 操作人不是管理员 |
| GOV-409 | `REPORT_NOT_PENDING` | 举报已被处理 |
| GOV-409-1 | `DUPLICATE_REPORT` | 同一目标存在待处理举报 |
| GOV-422 | `INVALID_TARGET` | 目标类型或目标 id 非法 |
| GOV-422-1 | `INVALID_REASON` | 原因/描述长度不合法 |
| GOV-422-2 | `TOO_MANY_EVIDENCE` | 证据超过 3 条 |
| GOV-403-1 | `INVALID_OPERATOR` | 用户 id 非法或非 ACTIVE |
| GOV-403-2 | `NOT_REPORTER` | 非举报人不能补充证据 |
| GOV-502 | `TARGET_ACTION_FAILED` | 目标处置接口调用失败 |

## 5. 运行

### 一键运行

```bat
run.bat
```

依次执行：Maven 编译 → JUnit 5 测试 → 打包 → `java -jar` 演示。

### 只跑测试

```bat
test.bat
```

### 只打包

```bat
build.bat
```

### 手动命令

在 `governance-module` 目录下：

```bat
mvn test
mvn package
java -jar target\governance-module-1.0.0.jar
```

如果 `mvn` 不在 PATH，使用绝对路径并复用项目本地仓库：

```bat
C:\Coding\apache-maven-3.10.0\bin\mvn.cmd -B -Dmaven.repo.local=..\.m2repo test
C:\Coding\apache-maven-3.10.0\bin\mvn.cmd -B -Dmaven.repo.local=..\.m2repo package
java -jar target\governance-module-1.0.0.jar
```

本次验收命令（在 `governance-module` 目录执行）：

```bat
& 'C:\Coding\apache-maven-3.10.0\bin\mvn.cmd' -B "-Dmaven.repo.local=C:\Users\warxi\Desktop\dsh\Software Engineering\.m2repo" test
& 'C:\Coding\apache-maven-3.10.0\bin\mvn.cmd' -B "-Dmaven.repo.local=C:\Users\warxi\Desktop\dsh\Software Engineering\.m2repo" verify
```

## 6. 测试清单（JUnit 5，101 个）

测试使用 Fake 端口适配器和固定 `Clock`（`2026-11-17T00:00:00Z`），不依赖真实时间、网络与数据库；
共 7 个测试类 101 个用例（基线 `GovernanceServiceTest` 12 个 + 新增 89 个）。

| 测试类 | 用例数 | 验证点 |
|---|---|---|
| `GovernanceServiceTest`（保留） | 12 | 基线主流程：提交、重复拦截、管理员处理、状态机、通知、统计、目标失败回滚 |
| `SubmitReportTest` | 30 | 四种目标类型均可提交；reason 2/100 通过、1/101 拒绝；description 500 通过、501 拒绝；evidence 3 通过、4 拒绝；null description/evidence；reporter 非 ACTIVE/未知/≤0；targetId 0/-1、targetType null；重复 PENDING 拦截；已处理（RESOLVED/REJECTED）后允许再次提交；不同举报人/不同目标不误判 |
| `HandleReportTest` | 20 | 非管理员先于举报查询被拒；operator ≤0；未知举报；已 RESOLVED/REJECTED 再处理；valid=false 不触达目标；POST/COMMENT/USER/TEAM 分别调用 hidePost/hideComment/muteUser/closeTeam 且不误调；四种目标动作失败均保持 PENDING、无日志无通知、handler 字段为空；目标动作执行时举报仍为 PENDING；note trim/空 note；每条处理一条审计日志；仅通知举报人 |
| `AddEvidenceTest` | 12 | 举报人追加证据（空列表补第一条、补满 3 条）；非举报人 `NOT_REPORTER`；已处理举报 `REPORT_NOT_PENDING`；合计 >3 与单次 >3 均为 `TOO_MANY_EVIDENCE`；未知举报 `REPORT_NOT_FOUND`；举报人校验先于状态校验；null 证据列表为空操作；返回并持久化更新后的详情 |
| `ReportQueryTest` | 7 | listPending 新到旧（固定时钟下按 id 倒序）、过滤已处理、空列表；brief 字段；stats 按状态/目标类型计数、空仓储全 0、byType 覆盖四种类型且合计等于 total |
| `DomainModelTest` | 12 | Report 初始 PENDING、evidence 防御性拷贝、resolve/reject 终态与重复迁移异常、markHandledAt 仅限已处理且非 null、appendEvidence 不可变；AdminLog trim/非空校验；SubmitReportCommand/ReportDetail/StatsOverview 的 null 与可变集合防御性拷贝 |
| `InMemoryRepositoryTest` | 8 | id 自增；save/findById/未知 id；findPending 按 createdAt 倒序、同时间按 id 倒序、过滤已处理；countByStatus；findAll 防御性拷贝；审计日志插入顺序与 id 自增 |

当前测试结果：

```text
Tests run: 101, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

覆盖率（JaCoCo 0.8.12，`mvn verify` 门禁，未下调阈值）：

| 指标 | 基线（12 用例） | 最终（101 用例） | 门禁 |
|---|---|---|---|
| 指令 INSTRUCTION | 1171/1232 = 95.05% | 1301/1309 = **99.39%** | ≥ 85% |
| 分支 BRANCH | 50/65 = 76.92% | 70/71 = **98.59%** | ≥ 75% |
| 行 LINE | 260/269 = 96.65% | 286/288 = **99.31%** | ≥ 85% |

剩余未覆盖的 2 行/1 个分支是 `GovernanceServiceImpl.applyTargetAction` 中枚举
`switch` 的 `default` 兜底分支；四种目标类型均已覆盖，该分支只在枚举值被扩展且未同步处理时可达，
属于防御性代码。覆盖率报告位于 `target/site/jacoco/index.html`，机器可读数据为
`target/site/jacoco/jacoco.csv`。

## 7. Demo 输出

`java -jar target\governance-module-1.0.0.jar` 实际输出：

```text
=== CoPlay governance module demo ===
[1] Submitted #2001: target=POST#5001, reporter=2, status=PENDING
[2] Duplicate pending report rejected: GOV-409-1 / A pending report for the same target already exists
[3] Submitted #2002: target=TEAM#4001, reporter=3, status=PENDING
[4] Submitted #2003: target=COMMENT#6001, reporter=4, status=PENDING
[5] Submitted #2004: target=USER#3001, reporter=1, status=PENDING
[6] Too many evidence items rejected: GOV-422-2 / At most 3 evidence items are allowed
[7] Non-admin handle rejected: GOV-403 / Operator is not an administrator
[8] Handled #2001: status=RESOLVED, handler=9
[9] Handled #2002: status=RESOLVED, handler=9
[10] Handled #2003: status=REJECTED, handler=9
[11] Handled #2004: status=RESOLVED, handler=9
[12] Re-handle rejected: GOV-409 / Report is not pending
[13] Pending reports: 0
[14] Stats: total=4, pending=0, resolved=3, rejected=1
     By type: POST=1, COMMENT=1, USER=1, TEAM=1
[15] Target actions:
  - ContentAdminService.hidePost(5001, Report 2001: Harassment (confirmed harassment))
  - UserAdminService.muteUser(3001, Report 2004: Toxic behavior (muted for 7 days))
  - TeamAdminService.closeTeam(4001, Report 2002: Cheating team (confirmed cheating))
[16] Admin logs:
  - logId=7001, adminId=9, action=HANDLE_REPORT, target=POST#5001, result=RESOLVED
  - logId=7002, adminId=9, action=HANDLE_REPORT, target=TEAM#4001, result=RESOLVED
  - logId=7003, adminId=9, action=HANDLE_REPORT, target=COMMENT#6001, result=REJECTED
  - logId=7004, adminId=9, action=HANDLE_REPORT, target=USER#3001, result=RESOLVED
[17] Notifications:
  - userId=2, type=REPORT_RESULT, content=Your report #2001 was resolved: confirmed harassment
  - userId=3, type=REPORT_RESULT, content=Your report #2002 was resolved: confirmed cheating
  - userId=4, type=REPORT_RESULT, content=Your report #2003 was rejected: not enough evidence
  - userId=1, type=REPORT_RESULT, content=Your report #2004 was resolved: muted for 7 days
```

## 8. 与详细设计的对应

- 类图：`Report`、`AdminLog`、`ReportStatus`、`ReportTargetType`
- 时序图：用户提交举报 → 管理员处理举报 → 目标模块处置 → 通知举报人
- 数据库表：`report`、`admin_log`
- 模块间接口：`AdminQueryService`、`UserQueryService`、`ContentAdminService`、
  `UserAdminService`、`TeamAdminService`、`NotificationService`
- 用例：举报内容、处理举报、查看举报统计

## 9. 说明

- 本模块定位为“可独立编译运行的领域核心”，没有引入 Spring 和数据库；真正的 Web 层、MyBatis 仓储、JWT 权限在完整项目中接入。
- 代码使用 Java 17 语法，JDK 21 可编译；测试使用 JUnit 5.10.2。
- DTO 使用 record（对 evidence 和 byType 做防御性拷贝），领域对象保留 `resolve` / `reject` / `isPending` / `appendEvidence` 状态变更方法，接口与实现分离。
- 服务通过构造器注入 `Clock`，测试使用固定时钟，保证 `createdAt` / `handledAt` / 日志时间可断言。
- `addEvidence` 按 TDD 实现：先写 12 个失败用例并保留红阶段证据（`target/tdd-red-phase.log`），实现后全部转绿；最终 101 个用例。
- 所有 Java 源码、注释和控制台输出均为英文；本 README 使用中文。
