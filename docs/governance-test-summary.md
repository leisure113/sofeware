# 治理模块测试总结（governance-module）

> 实验：单元测试与 JaCoCo 覆盖率门禁
> 模块：`governance-module`（独立 Maven 模块，Java 17 语法，JUnit 5.10.2，JaCoCo 0.8.12）
> 日期：2026-11-17
> 约束：只修改 `governance-module/` 与本文件；未改动其他模块、根 README、报告 DOCX、Git 或 `.m2repo`，未提交 Git。

## 1. 最终结果

| 项目 | 结果 |
|---|---|
| 测试总数 | **101**（基线 12 + 新增 89） |
| 测试通过 | 101 / 101，Failures 0，Errors 0，Skipped 0 |
| `mvn verify` | **BUILD SUCCESS**（测试 + JaCoCo report + check 全部通过） |
| JaCoCo 阈值 | 保持 instruction ≥ 0.85、branch ≥ 0.75、line ≥ 0.85，**未下调** |
| 新增运行时依赖 | 无（仅 JUnit 5，pom 依赖未改动） |

执行命令（在 `governance-module` 目录下）：

```bat
C:\Coding\apache-maven-3.10.0\bin\mvn.cmd -B "-Dmaven.repo.local=C:\Users\warxi\Desktop\dsh\Software Engineering\.m2repo" verify
```

输出摘要：

```text
[INFO] Tests run: 101, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- jacoco:0.8.12:report (report) @ governance-module ---
[INFO] --- jacoco:0.8.12:check (check) @ governance-module ---
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```

## 2. 覆盖率（取自 `target/site/jacoco/jacoco.csv`）

| 指标 | 基线（12 用例） | 最终（101 用例） | 门禁 | 对比 |
|---|---|---|---|---|
| 指令 INSTRUCTION | 1171/1232 = **95.05%** | 1301/1309 = **99.39%** | ≥ 85% | +4.34 pp |
| 分支 BRANCH | 50/65 = **76.92%** | 70/71 = **98.59%** | ≥ 75% | +21.67 pp |
| 行 LINE | 260/269 = **96.65%** | 286/288 = **99.31%** | ≥ 85% | +2.66 pp |

说明：

- 统计范围为 pom 中 JaCoCo 配置的全部类（`GovernanceModuleDemo` 按既有配置排除）。
- 剩余未覆盖的 **2 行 / 8 条指令 / 1 个分支**集中在 `GovernanceServiceImpl.applyTargetAction` 枚举 `switch` 的 `default` 兜底分支：四种目标类型（POST/COMMENT/USER/TEAM）均已覆盖，该分支只在枚举新增而 `switch` 未同步时可达，属于防御性代码。
- 其余 13 个类均为 0 未覆盖行/分支，包括新增的 `addEvidence` 路径与 `Report.appendEvidence`。

## 3. TDD 红-绿过程（addEvidence）

### 3.1 需求

在 `GovernanceService` 新增：

```java
ReportDetail addEvidence(long reportId, long reporterId, List<String> evidence);
```

规则：举报必须存在（`REPORT_NOT_FOUND`）；只有原举报人可追加（`NOT_REPORTER`）；
举报必须为 PENDING（`REPORT_NOT_PENDING`）；追加后证据合计不能超过 3 条（`TOO_MANY_EVIDENCE`）；
成功时追加证据、持久化并返回更新后的 `ReportDetail`，不改变状态、不写审计日志、不发通知。

新增错误码：

```java
NOT_REPORTER("GOV-403-2", "Only the reporter can add evidence to a report")
```

### 3.2 红阶段（先写测试）

先只加入接口声明、`NOT_REPORTER` 错误码和临时实现：

```java
@Override
public ReportDetail addEvidence(long reportId, long reporterId, List<String> evidence) {
    throw new UnsupportedOperationException("addEvidence is not implemented yet");
}
```

新增 `AddEvidenceTest` 的 12 个用例：举报人追加成功、空证据补第一条、补满 3 条、空列表补 3 条、
非举报人、已 RESOLVED、已 REJECTED、举报人校验先于状态校验、合计超 3、单次超 3、未知举报、null 证据列表。
执行 `mvn test` 得到红阶段证据（完整日志保存在 `target/tdd-red-phase.log`）：

```text
[ERROR]   AddEvidenceTest.evidenceCanReachExactlyThreeItems:60 ? UnsupportedOperation addEvidence is not implemented yet
[ERROR]   AddEvidenceTest.nullEvidenceAddsNothing:160 ? UnsupportedOperation addEvidence is not implemented yet
[ERROR]   AddEvidenceTest.reporterAddsFirstEvidenceItems:49 ? UnsupportedOperation addEvidence is not implemented yet
[ERROR]   AddEvidenceTest.reporterAppendsEvidenceToPendingReport:30 ? UnsupportedOperation addEvidence is not implemented yet
[ERROR]   AddEvidenceTest.threeNewItemsOnEmptyEvidenceAreAllowed:71 ? UnsupportedOperation addEvidence is not implemented yet
[ERROR] Tests run: 100, Failures: 7, Errors: 5, Skipped: 0
[ERROR] BUILD FAILURE
```

红阶段汇总：**Tests run: 100, Failures: 7, Errors: 5**。新增的 12 个 `AddEvidenceTest` 用例全部失败
（5 个期望成功的用例抛 `UnsupportedOperationException` 记为 Errors；7 个期望业务异常的用例因异常类型不符记为 Failures），
其余 88 个既有/已扩展用例保持通过。红阶段总数为 100：此时域层 `Report.appendEvidence` 的单元测试尚未加入，
它随实现一起在绿阶段补充，因此最终为 101。

### 3.3 绿阶段（实现）

实现要点：

1. `requireReport(reportId)` 找不到举报 → `REPORT_NOT_FOUND`；
2. `report.getReporterId() != reporterId` → `NOT_REPORTER`（在状态校验之前）；
3. `!report.isPending()` → `REPORT_NOT_PENDING`；
4. `normalizeEvidence(evidence)` 处理 null/复制，`已有证据数 + 新增数 > 3` → `TOO_MANY_EVIDENCE`；
5. `Report.appendEvidence(List<String>)` 以不可变快照替换证据列表（`evidence` 字段由 `final` 改为可变）；
6. `reportRepository.save(report)` 持久化后返回 `toDetail(report)`。

实现后执行 `mvn test`：

```text
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 -- in com.coplay.governance.AddEvidenceTest
[INFO] Tests run: 101, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

随后执行 `mvn verify`，101 个用例全绿且 JaCoCo 门禁通过（见第 1、2 节）。

## 4. 缺陷与修复

**结论：未发现既有功能的真实缺陷，未修改既有业务行为。**

- 本轮把 README §4.1–§4.3 的每条规则（提交校验、重复拦截、管理员处理、目标处置分发、失败回滚、日志/通知、列表排序、统计）都转成了正常/异常/边界用例，实现与 README、Demo 输出完全一致，未暴露与规格不符的行为。
- 唯一 API 缺口是需求新增的 `addEvidence`，属于功能缺失而非缺陷；已按 TDD 补齐并新增 12 个回归用例。
- 按现有契约固化、但不算缺陷的行为：
  - 目标处置抛异常时 `TARGET_ACTION_FAILED` 只携带异常消息、不保留原始 cause（`BizException` 无 cause 构造器），是既有异常模型约定；
  - `Report.resolve/reject` 直接调用时 `handledAt` 取墙钟，服务层随后用注入 `Clock` 的 `markHandledAt` 覆盖，属于领域对象“可脱离服务使用”的既有设计（README §9）；
  - `submit(null)`、`handle(null)` 等编程式误用抛 `NullPointerException`，调用方 DTO 校验应保证非空；证据列表中包含 `null` 元素时 `List.copyOf` 会抛 `NullPointerException`，因 API 契约未定义此类输入，未改变行为。
  - 内存仓储的 `submit/handle/addEvidence` 是“先查后写”，并发下非原子；本模块定位为可独立演示的领域核心（生产环境由数据库唯一约束/事务保证），未在本轮引入锁。
- 防御性 `switch default` 分支不可达，是覆盖率中唯一的未覆盖分支，已在 README 与本文件中说明。

## 5. 新增测试类与关键场景

| 测试类 | 用例数 | 关键场景 |
|---|---|---|
| `GovernanceServiceTest`（保留） | 12 | 基线主流程：提交、重复拦截、管理员处理、状态机、通知、统计、目标失败回滚；未删改 |
| `SubmitReportTest` | 30 | 四种目标类型提交；reason 2/100 通过、1/101 拒绝；description 500 通过、501 拒绝；evidence 3 通过、4 拒绝；null description/evidence；reporter 非 ACTIVE/未知/≤0；targetId 0/-1、targetType null；重复 PENDING 拦截；已处理（RESOLVED/REJECTED）后允许再次提交；不同举报人/不同目标不误判 |
| `HandleReportTest` | 20 | 非管理员先于举报查询被拒；operator ≤0；未知举报；已 RESOLVED/REJECTED 再处理；valid=false 不触达目标；POST/COMMENT/USER/TEAM 分别调用 hidePost/hideComment/muteUser/closeTeam 且不误调；四种目标动作失败均保持 PENDING、handler 字段为空、无日志无通知；目标动作执行时举报仍为 PENDING；note trim/空 note；每条处理一条审计日志；仅通知举报人 |
| `AddEvidenceTest` | 12 | 举报人追加证据（空列表补第一条、补满 3 条）；非举报人 `NOT_REPORTER`；已处理 `REPORT_NOT_PENDING`；合计 >3 与单次 >3 均为 `TOO_MANY_EVIDENCE`；未知举报 `REPORT_NOT_FOUND`；举报人校验先于状态校验；null 证据列表为空操作；返回并持久化更新后的详情 |
| `ReportQueryTest` | 7 | listPending 新到旧（固定时钟下按 id 倒序）、过滤已处理、空列表；brief 字段；stats 按状态/目标类型计数、空仓储全 0、byType 覆盖四种类型且合计等于 total |
| `DomainModelTest` | 12 | Report 初始 PENDING、evidence 防御性拷贝、resolve/reject 终态与重复迁移异常、markHandledAt 仅限已处理且非 null、appendEvidence 不可变；AdminLog trim/非空校验；SubmitReportCommand/ReportDetail/StatsOverview 的 null 与可变集合防御性拷贝 |
| `InMemoryRepositoryTest` | 8 | id 自增；save/findById/未知 id；findPending 按 createdAt 倒序、同时间按 id 倒序、过滤已处理；countByStatus；findAll 防御性拷贝；审计日志插入顺序与 id 自增 |

测试确定性：

- 所有服务用例注入 `Clock.fixed(2026-11-17T00:00:00Z)`，`createdAt` / `handledAt` / 审计日志时间均为固定值；
- 端口全部使用内存 Fake（管理员、用户状态、内容处置、用户处置、队伍处置、通知）；
- 共享夹具 `GovernanceTestSupport` 为每个用例创建全新的仓储与 Fake，避免用例间状态泄漏；
- 时间/ID 断言不依赖真实时钟、网络或数据库。

## 6. 变更文件

主代码：

- `src/main/java/com/coplay/governance/api/GovernanceService.java`：新增 `addEvidence` 声明与 Javadoc；
- `src/main/java/com/coplay/governance/service/GovernanceServiceImpl.java`：实现 `addEvidence`（存在性 → 举报人 → PENDING → 合计 ≤3 → 追加/持久化）；
- `src/main/java/com/coplay/governance/domain/Report.java`：`evidence` 支持不可变替换，新增 `appendEvidence(List<String>)`；
- `src/main/java/com/coplay/governance/support/ErrorCode.java`：新增 `NOT_REPORTER`（GOV-403-2）。

测试代码：

- 新增 `GovernanceTestSupport`（共享夹具）与 6 个测试类（见第 5 节，共 89 个新用例）；
- 保留 `GovernanceServiceTest`（12 个基线用例）与既有 Fake 端口，未删改用例。

文档：

- `governance-module/README.md`：功能、目录结构、业务规则（新增 4.2 补充证据）、错误码、验收命令、测试清单与覆盖率结果；
- `docs/governance-test-summary.md`：本文件。

## 7. 复现方式

```bat
cd "C:\Users\warxi\Desktop\dsh\Software Engineering\governance-module"

rem 仅测试（101 个）
C:\Coding\apache-maven-3.10.0\bin\mvn.cmd -B "-Dmaven.repo.local=C:\Users\warxi\Desktop\dsh\Software Engineering\.m2repo" test

rem 测试 + JaCoCo 报告 + 覆盖率门禁
C:\Coding\apache-maven-3.10.0\bin\mvn.cmd -B "-Dmaven.repo.local=C:\Users\warxi\Desktop\dsh\Software Engineering\.m2repo" verify
```

报告位置：`governance-module/target/site/jacoco/index.html`；机器可读数据：`governance-module/target/site/jacoco/jacoco.csv`；
TDD 红阶段日志：`governance-module/target/tdd-red-phase.log`。
