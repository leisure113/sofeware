# content-module 单元测试与 TDD 总结

- 模块：`content-module`（同游社内容模块：板块 / 帖子 / 评论）
- 日期：2026-11-17
- 环境：Windows 11、JDK 21.0.10（`--release 17`）、Maven 3.10.0、JUnit 5.10.2、JaCoCo 0.8.12
- 执行命令（在 `content-module` 目录下）：

```bat
C:\Coding\apache-maven-3.10.0\bin\mvn.cmd -B "-Dmaven.repo.local=C:\Users\warxi\Desktop\dsh\Software Engineering\.m2repo" verify
```

- 结果：`BUILD SUCCESS`、`All coverage checks have been met.`、148 个测试全部通过。

## 1. 最终测试结果

```text
Tests run: 148, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
All coverage checks have been met.
```

| 测试类 | 用例数 | 覆盖重点 |
|---|---:|---|
| `ContentServiceTest` | 12 | 原有主流程回归（发帖、读帖、评论、列表、隐藏幂等） |
| `CreatePostTest` | 31 | 标题 2/3/100/101、正文 0/1/5000/5001、未知/禁用板块、禁用游戏、未激活/禁言用户、敏感标题/正文、去空格、持久化、空命令 |
| `GetPostTest` | 4 | 正常读取、未知帖子、HIDDEN、DELETED（均按 `POST_NOT_FOUND`） |
| `ListPostsTest` | 8 | 未知板块、空板块、仅 PUBLISHED、createdAt 倒序、同时间按帖子 ID 倒序、板块过滤、摘要字段 |
| `AddCommentTest` | 21 | 评论 0/1/500/501、空白/空值、未知/隐藏/DELETED 帖子、未激活/禁言用户、敏感评论、`POST_COMMENT` 通知与持久化 |
| `ListCommentsTest` | 7 | 未知帖子、空列表、仅 PUBLISHED、createdAt 倒序、同时间按评论 ID 倒序、跨帖子隔离 |
| `HideContentTest` | 12 | 未知帖子/评论、幂等隐藏、首次隐藏仅一次 `CONTENT_HIDDEN` 通知、reason 去空格/空值、DELETED 目标为 no-op |
| `EditPostTest` | 28 | TDD 新增 `editPost`：作者成功、非作者、未知/隐藏/DELETED、标题/正文边界、空值/空白、敏感词、无通知、多次编辑 |
| `ContentServiceContractTest` | 7 | 6 个构造器参数的非空契约、正常构造 |
| `DomainAndErrorCodeTest` | 9 | Board/Post/Comment 状态迁移、`Post.edit`、枚举、错误码、`BizException` 两种构造器 |
| `InMemoryContentRepositoryTest` | 4 | 自增 ID、board/post/comment 的保存、按 ID/板块/帖子查询 |
| `DtoContractTest` | 5 | 5 个 DTO record 的 equals / hashCode / toString 值语义 |
| **合计** | **148** | 全部通过 |

测试全部使用固定 `Clock`（`2026-11-01T00:00:00Z`）、`InMemoryContentRepository` 与四个 Fake 端口，不依赖 Spring、MySQL、网络或真实时间，可重复执行。

## 2. JaCoCo 覆盖率（精确数字）

阈值未改动：INSTRUCTION 85%、BRANCH 75%、LINE 85%（`pom.xml` 中 JaCoCo `check` 规则）。实际结果全部为 100.00%。

| 类 | 指令（覆盖/总数） | 分支（覆盖/总数） | 行（覆盖/总数） | 方法（覆盖/总数） |
|---|---:|---:|---:|---:|
| `ContentServiceImpl` | 551 / 551 | 44 / 44 | 152 / 152 | 26 / 26 |
| `InMemoryContentRepository` | 143 / 143 | 4 / 4 | 26 / 26 | 15 / 15 |
| `Post` | 83 / 83 | 4 / 4 | 23 / 23 | 11 / 11 |
| `Comment` | 61 / 61 | 4 / 4 | 18 / 18 | 9 / 9 |
| `Board` | 41 / 41 | 0 / 0 | 14 / 14 | 7 / 7 |
| `PostStatus` | 21 / 21 | 0 / 0 | 4 / 4 | 1 / 1 |
| `CommentStatus` | 21 / 21 | 0 / 0 | 4 / 4 | 1 / 1 |
| `ErrorCode` | 108 / 108 | 0 / 0 | 18 / 18 | 4 / 4 |
| `BizException` | 23 / 23 | 0 / 0 | 7 / 7 | 3 / 3 |
| `PostDetail` | 24 / 24 | 0 / 0 | 1 / 1 | 1 / 1 |
| `PostBrief` | 21 / 21 | 0 / 0 | 1 / 1 | 1 / 1 |
| `CommentBrief` | 21 / 21 | 0 / 0 | 1 / 1 | 1 / 1 |
| `CreatePostCommand` | 15 / 15 | 0 / 0 | 1 / 1 | 1 / 1 |
| `CreateCommentCommand` | 12 / 12 | 0 / 0 | 1 / 1 | 1 / 1 |
| **BUNDLE（14 个类）** | **1145 / 1145 = 100.00%** | **56 / 56 = 100.00%** | **271 / 271 = 100.00%** | **82 / 82 = 100.00%** |

- 指令覆盖率（JaCoCo INSTRUCTION，报告中的“语句覆盖率”统计口径）：100.00%，阈值 85%。
- 分支覆盖率：100.00%，阈值 75%。
- 行覆盖率：100.00%，阈值 85%。
- 方法覆盖率：100.00%。
- `ContentModuleDemo` 按原有配置排除，未计入统计。

原始数据：`target/site/jacoco/jacoco.csv`、`target/site/jacoco/index.html`。

## 3. TDD 记录：`editPost`

### 3.1 需求与测试先行

`ContentService` 原来没有编辑帖子的接口，`Post` 的标题/正文为 `final` 字段。新增接口：

```java
PostDetail editPost(long postId, long authorId, String title, String content);
```

先编写 `EditPostTest`（28 个用例/参数化实例），覆盖：

- 作者编辑成功：返回新标题/正文，帖子 ID、板块、作者、createdAt、PUBLISHED 状态不变，仓储与 `getPost` 同步更新，`listPosts` 反映新标题；
- 非作者返回 `NOT_POST_AUTHOR`，内容不变；
- 未知、HIDDEN、DELETED 帖子均返回 `POST_NOT_FOUND`，内容不变；
- 标题边界：3、100 通过；0、1、2、101、200 及 null、纯空白拒绝（`INVALID_TITLE`）；
- 正文边界：1、5000 通过；0、5001、6000 及 null、纯空白拒绝（`INVALID_CONTENT`）；
- 标题/正文命中敏感词返回 `SENSITIVE_CONTENT`，内容不变；
- 编辑不发送任何通知；同一帖子可连续编辑，createdAt 不变。

### 3.2 Red（先失败）

在接口中加入 `editPost` 签名，并在 `ContentServiceImpl` 中放置临时占位实现：

```java
@Override
public PostDetail editPost(long postId, long authorId, String title, String content) {
    throw new UnsupportedOperationException("editPost is not implemented yet");
}
```

同时先加入 `ErrorCode.NOT_POST_AUTHOR`（否则测试无法通过编译）。执行 `mvn test`：

```text
Tests run: 147, Failures: 20, Errors: 8, Skipped: 0
BUILD FAILURE
```

- 20 个失败：拒绝型用例中 `assertThrows(BizException.class, ...)` 实际抛出 `UnsupportedOperationException`；
  典型信息：`Unexpected exception type thrown, expected: <com.coplay.content.support.BizException> but was: <java.lang.UnsupportedOperationException>`；
- 8 个错误：成功路径用例直接抛出 `java.lang.UnsupportedOperationException: editPost is not implemented yet`；
- 失败/错误全部集中在 `EditPostTest`（28 个实例）；其余 119 个针对既有行为的测试全部通过，说明测试环境与既有功能基线正常。
- 原始日志：`target/tdd-red.log`。

### 3.3 Green（实现后通过）

实现：

- `ContentServiceImpl.editPost`：先 `requirePost`，非 `PUBLISHED` 抛 `POST_NOT_FOUND`；作者不匹配抛 `NOT_POST_AUTHOR`；复用 `validateTitle` / `validatePostContent` / `requireCleanText`；调用 `Post.edit` 后 `savePost`，返回 `PostDetail`。
- `Post`：`title` / `content` 改为可变字段，新增 `edit(String, String)`，保留帖子 ID、板块、作者、createdAt 与状态；null 文本由 `Objects.requireNonNull` 拒绝。
- `ErrorCode.NOT_POST_AUTHOR("CONTENT-403", "Only the post author can edit this post")`。

执行 `mvn test`：

```text
Tests run: 148, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

（148 = 147 + 1；Green 阶段补充了 `DomainAndErrorCodeTest.postEditReplacesText` 领域层用例。）

### 3.4 Refactor（重构与回归）

- 校验逻辑复用既有的私有方法，没有复制长度/敏感词规则；领域状态变更收敛到 `Post.edit`。
- 未修改 `pom.xml` 覆盖率阈值，未放宽任何门禁。
- 最终 `mvn verify` 通过，原始日志：`target/tdd-green.log`、`target/verify.log`。

## 4. Bug 发现与修复

**本次未发现既有生产代码的真实 bug，因此没有 bug 修复型回归用例。**

依据：

- 在 Red 运行中，119 个针对既有实现（发帖、读帖、列表、评论、隐藏、仓储、领域对象、DTO）的测试全部通过，没有出现与 README 业务规则不一致的行为；
- 逐条核对 README 规则：状态可见性（HIDDEN/DELETED 均按 `POST_NOT_FOUND`）、只返回 PUBLISHED、createdAt 倒序 + ID 倒序的稳定排序、板块过滤、评论长度、敏感词过滤、通知目标与幂等隐藏，均与实现一致；
- `hidePost` / `hideComment` 重复调用只通知一次；编辑失败时不会产生部分更新；未知目标返回统一错误码。

因此生产代码改动仅为新功能及其必要支撑（见第 5 节），没有对既有业务逻辑做“顺手修改”。

## 5. 生产代码改动

| 文件 | 改动 |
|---|---|
| `src/main/java/com/coplay/content/api/ContentService.java` | 新增 `editPost(long, long, String, String)` 接口方法 |
| `src/main/java/com/coplay/content/service/ContentServiceImpl.java` | 实现 `editPost`：可见性、作者、长度、敏感词校验，更新并持久化 |
| `src/main/java/com/coplay/content/domain/Post.java` | `title`/`content` 可变；新增 `edit`，保留 ID/板块/作者/创建时间/状态 |
| `src/main/java/com/coplay/content/support/ErrorCode.java` | 新增 `NOT_POST_AUTHOR`（`CONTENT-403`） |
| `README.md` | 更新目录结构、业务规则、错误码、测试清单与覆盖率结果 |

未修改 `pom.xml`、其他模块、根 README、报告 DOCX、`.m2repo`，未执行 Git 提交。

## 6. 新增测试文件

```text
src/test/java/com/coplay/content/
├─ ContentServiceTestSupport.java   共享夹具：固定 Clock、Fake、内存仓储、错误码断言、受控状态注入
├─ CreatePostTest.java
├─ GetPostTest.java
├─ ListPostsTest.java
├─ AddCommentTest.java
├─ ListCommentsTest.java
├─ HideContentTest.java
├─ EditPostTest.java                TDD 新增 editPost（28 个实例）
├─ ContentServiceContractTest.java
├─ DomainAndErrorCodeTest.java
├─ InMemoryContentRepositoryTest.java
└─ DtoContractTest.java
```

说明：

- `ContentServiceTestSupport` 统一构造测试数据（板块 1/2 属于启用游戏 100，板块 3 禁用，板块 4 属于禁用游戏 200），并隔离每个测试的仓储/Fake 状态；
- 对 `DELETED` 这一治理侧预留状态，测试通过反射设置 `Post`/`Comment` 的 `status`，不为了测试而放宽生产 API；
- 时间相关排序通过直接构造带不同 `createdAt` 的领域对象验证，服务写入仍使用固定 `Clock`，保证确定性；
- `ContentServiceTest` 的 12 个原有用例保持不变，作为回归基线。

## 7. 复现与产物

```bat
cd "C:\Users\warxi\Desktop\dsh\Software Engineering\content-module"
C:\Coding\apache-maven-3.10.0\bin\mvn.cmd -B "-Dmaven.repo.local=C:\Users\warxi\Desktop\dsh\Software Engineering\.m2repo" verify
```

| 产物 | 路径 |
|---|---|
| Surefire 文本/XML 报告 | `target/surefire-reports/` |
| JaCoCo HTML | `target/site/jacoco/index.html` |
| JaCoCo CSV（精确数字来源） | `target/site/jacoco/jacoco.csv` |
| TDD Red 日志 | `target/tdd-red.log` |
| TDD Green 日志 | `target/tdd-green.log` |
| 最终 verify 日志 | `target/verify.log` |

## 8. 结论

- 148 个 JUnit 5 测试全部通过，覆盖正常、异常、边界与可见性/排序规则；
- 指令/分支/行覆盖率均为 100.00%，高于 85% / 75% / 85% 门槛且未下调阈值；
- `editPost` 完成“测试先行 → 占位失败 → 实现转绿 → 回归验证”的完整 TDD 闭环；
- 未发现既有生产 bug，故无 bug 修复项；生产改动仅限新功能与 README 更新。
