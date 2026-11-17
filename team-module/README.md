# Team Module — 组队模块核心代码

课程设计《同游社——校园游戏同好与开黑搭子社区》实验 5 的核心模块实现。
本模块只实现**组队**一个模块，不依赖 Spring、MySQL 和其他模块的实现，可独立编译、测试、运行。

## 1. 为什么选组队模块

- 覆盖系统最核心的“找搭子”业务：发布 → 申请 → 审批 → 满员 → 关闭/过期。
- 需要调用用户模块、内容模块、通知模块的接口，能直接展示模块间接口调用。
- 有状态机、容量控制、重复申请、权限校验和“最后一个名额”并发问题，适合写测试。
- 只依赖端口接口和内存仓储，能脱离数据库独立运行。

## 2. 目录结构

```text
team-module/
├─ pom.xml
├─ README.md
├─ run.bat / test.bat / build.bat
├─ src/main/java/com/coplay/team/
│  ├─ api/            TeamService、UserQueryService、GameQueryService、NotificationService
│  ├─ domain/         Team、TeamApplication、TeamStatus、ApplicationStatus
│  ├─ dto/            CreateTeamCommand、ApplyTeamCommand、ApproveCommand、TeamBrief、TeamDetail、ApplicationBrief
│  ├─ repository/     TeamRepository、InMemoryTeamRepository
│  ├─ support/        BizException、ErrorCode、TeamLockRegistry、InMemoryTeamLockRegistry
│  ├─ service/        TeamServiceImpl
│  └─ TeamModuleDemo.java
└─ src/test/java/com/coplay/team/
   ├─ TeamServiceTest.java
   ├─ TeamConcurrencyTest.java
   └─ fake/            FakeUserQueryService、FakeGameQueryService、FakeNotificationService
```

## 3. 模块间接口调用

`TeamServiceImpl` 只依赖端口，不依赖其他模块的实现：

```text
TeamServiceImpl
 ├─ UserQueryService     I-03  校验用户 ACTIVE / 禁言
 ├─ GameQueryService     I-04  校验游戏启用
 ├─ NotificationService  I-12  通知队长/申请人
 ├─ TeamRepository             队伍/申请持久化（本模块内存实现）
 └─ TeamLockRegistry           按 teamId 加锁，保证审批原子性
```

真实项目中只需把 `InMemoryTeamRepository` 换成 MyBatis 实现，把三个 Fake 换成调用用户/内容/通知模块的 Spring Bean，`TeamServiceImpl` 不需要改。

## 4. 业务规则

- 队伍状态：`RECRUITING → FULL → CLOSED/EXPIRED`，另有 `CANCELLED`。
- 申请状态：`PENDING → APPROVED/REJECTED/CANCELLED`，预留 `LEFT/KICKED`。
- `capacity` 包含队长，取值 2—10；已通过申请数 + 1（队长）达到 capacity 时自动 `FULL`。
- 只有队长能审批、关闭队伍；队长不能申请自己的队伍。
- 同一队伍同一用户只能有一条有效申请（PENDING/APPROVED）。
- 只有 ACTIVE 且未禁言的用户可以创建/申请。
- 审批必须在 `TeamLockRegistry` 的 `teamId` 锁内执行，先重新计数再决定是否通过。
- 开始时间到达后，队伍自动变为 `EXPIRED`。

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

如果 `mvn` 已在 PATH：

```bat
mvn test
mvn package
java -jar target\team-module-1.0.0.jar
```

如果 `mvn` 不在 PATH，用绝对路径：

```bat
C:\Coding\apache-maven-3.10.0\bin\mvn.cmd -B -Dmaven.repo.local=..\.m2repo test
C:\Coding\apache-maven-3.10.0\bin\mvn.cmd -B -Dmaven.repo.local=..\.m2repo package
java -jar target\team-module-1.0.0.jar
```

脚本会自动在 PATH 中查找 `mvn`，找不到时回退到上面的绝对路径；本地仓库优先使用项目上一级的 `.m2repo`。

## 6. 测试清单（JUnit 5，16 个）

| 用例 | 验证点 |
|---|---|
| createTeamSuccess | 创建成功、状态 RECRUITING、队长可见房间号 |
| createRejectsPastStartTime | 过去时间被拒绝 |
| createRejectsInvalidCapacity | capacity 越界被拒绝 |
| applyNotifiesCaptain | 申请 PENDING、通知队长 |
| applyRejectsDuplicate | 重复申请被拒绝 |
| captainCannotApplyToOwnTeam | 队长不能申请自己的队 |
| approvalFillsTeam | 最后一个名额通过后自动 FULL |
| nonCaptainCannotApprove | 非队长审批被拒绝 |
| fullTeamRejectsApplication | 满员后新申请被拒绝 |
| closeTeamByCaptain | 队长可关闭、非队长被拒绝 |
| teamExpiresAfterStartTime | 到达开始时间自动 EXPIRED |
| mutedUserCannotApply | 禁言用户被拒绝 |
| applicantCanCancelApplication | 申请人可取消申请 |
| captainCanRejectPendingApplicationAfterFull | 满员后队长仍可拒绝待处理申请 |
| roomCodeHiddenFromNonMember | 非成员看不到房间号 |
| TeamConcurrencyTest.onlyOneApplicantGetsLastSlot | 两个线程抢最后一个名额，只允许一个成功 |

当前测试结果：

```text
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 7. Demo 输出

```text
Created team: 1001, status=RECRUITING
Application: 5001, userId=2, status=PENDING
After approval: approvedCount=1, status=FULL, roomCode=ROOM-888
Third player rejected: TEAM-409 / Team is full
Notifications:
  - userId=1, type=TEAM_APPLY, content=New team application received...
  - userId=2, type=TEAM_RESULT, content=Application approved...
```

## 8. 与实验 5 设计的对应

- 类图 2-3：`Team`、`TeamApplication`、`User`、`TeamStatus`
- 时序图 3-3：组队申请审批时序
- 数据库表 `team`、`team_application`
- 接口 I-03、I-04、I-12
- 流程图：组队申请审批流程

## 9. 说明

- 本模块定位为“可独立编译运行的领域核心”，没有引入 Spring 和数据库；真正的 Web 层、MyBatis 仓储、JWT 权限在完整项目中接入。
- 代码使用 Java 17 语法，JDK 21 可编译；测试使用 JUnit 5.10.2。
- DTO 使用 record，领域对象保留状态变更方法，接口与实现分离，便于测试和维护。

