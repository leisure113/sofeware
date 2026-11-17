# Content Module — 内容模块核心代码

课程设计《同游社——校园游戏同好与开黑搭子社区》实验 5 的核心模块实现。
本模块只实现**内容（板块 / 帖子 / 评论）**一个模块，不依赖 Spring、MySQL 和其他模块的实现，可独立编译、测试、运行。

## 1. 为什么选内容模块

- 覆盖社区最基础的 UGC 链路：浏览板块 → 发帖 → 读帖 → 评论 → 违规内容隐藏。
- 需要调用用户模块（是否激活 / 禁言）、游戏目录（游戏是否启用）、敏感词过滤和通知模块，能直接展示模块间接口调用。
- 有内容状态（PUBLISHED / HIDDEN / DELETED）、长度校验、敏感词拦截、只看已发布内容、隐藏操作幂等等规则，适合写单元测试。
- 只依赖端口接口和内存仓储，能脱离数据库独立运行。

## 2. 目录结构

```text
content-module/
├─ pom.xml
├─ README.md
├─ run.bat / test.bat / build.bat
├─ src/main/java/com/coplay/content/
│  ├─ api/            ContentService、UserQueryService、GameQueryService、SensitiveWordFilter、NotificationService
│  ├─ domain/         Board、Post、Comment、PostStatus、CommentStatus
│  ├─ dto/            CreatePostCommand、CreateCommentCommand、PostDetail、PostBrief、CommentBrief
│  ├─ repository/     ContentRepository、InMemoryContentRepository
│  ├─ support/        BizException、ErrorCode
│  ├─ service/        ContentServiceImpl
│  └─ ContentModuleDemo.java
└─ src/test/java/com/coplay/content/
   ├─ ContentServiceTest.java
   └─ fake/            FakeUserQueryService、FakeGameQueryService、FakeSensitiveWordFilter、FakeNotificationService
```

## 3. 模块间接口调用

`ContentServiceImpl` 只依赖端口，不依赖其他模块的实现：

```text
ContentServiceImpl
 ├─ UserQueryService      校验用户 ACTIVE / 是否禁言
 ├─ GameQueryService      校验板块所属游戏是否启用
 ├─ SensitiveWordFilter   对标题 + 正文、评论内容做敏感词过滤
 ├─ NotificationService   评论通知帖子作者（POST_COMMENT）、隐藏通知作者（CONTENT_HIDDEN）
 └─ ContentRepository     板块 / 帖子 / 评论持久化（本模块内存实现）
```

真实项目中只需把 `InMemoryContentRepository` 换成 MyBatis 实现，把四个 Fake 换成调用用户 / 游戏 / 敏感词 / 通知模块的 Spring Bean，`ContentServiceImpl` 不需要改。

## 4. 业务规则

- 内容状态：`PUBLISHED → HIDDEN`，预留 `DELETED`；重复隐藏是幂等操作，不会重复通知。
- 发帖：用户必须 ACTIVE 且未禁言；板块存在且启用；板块所属游戏启用；标题 3—100 字符；正文 1—5000 字符；标题 + 正文不能命中敏感词；保存后状态为 `PUBLISHED`。
- 读帖：帖子必须存在且为 `PUBLISHED`；`HIDDEN` / `DELETED` 一律按 `POST_NOT_FOUND` 处理，避免暴露违规内容是否存在。
- 帖子列表：板块必须存在；只返回 `PUBLISHED`；按创建时间倒序，时间相同时按帖子 ID 倒序，保证顺序稳定。
- 评论：帖子存在且为 `PUBLISHED`；用户必须 ACTIVE 且未禁言；评论内容 1—500 字符；命中敏感词拒绝；保存后通知帖子作者，通知类型 `POST_COMMENT`。
- 评论列表：帖子必须存在；只返回 `PUBLISHED`；按创建时间倒序，时间相同时按评论 ID 倒序。
- 隐藏帖子 / 评论：目标必须存在；隐藏操作幂等；本模块不做权限判断，由治理适配器完成鉴权；首次隐藏时给作者发 `CONTENT_HIDDEN` 通知。
- 错误码：`POST_NOT_FOUND`、`COMMENT_NOT_FOUND`、`BOARD_NOT_FOUND`、`BOARD_DISABLED`、`GAME_DISABLED`、`USER_NOT_ACTIVE`、`USER_MUTED`、`INVALID_TITLE`、`INVALID_CONTENT`、`SENSITIVE_CONTENT`。

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
java -jar target\content-module-1.0.0.jar
```

如果 `mvn` 不在 PATH，用绝对路径（在 `content-module` 目录下执行）：

```bat
C:\Coding\apache-maven-3.10.0\bin\mvn.cmd -B -Dmaven.repo.local=..\.m2repo test
C:\Coding\apache-maven-3.10.0\bin\mvn.cmd -B -Dmaven.repo.local=..\.m2repo package
java -Dfile.encoding=UTF-8 -jar target\content-module-1.0.0.jar
```

脚本会自动在 PATH 中查找 `mvn`，找不到时回退到绝对路径；本地仓库优先使用项目上一级的 `.m2repo`。

## 6. 测试清单（JUnit 5，12 个）

| 用例 | 验证点 |
|---|---|
| createPostSuccess | 发帖成功、标题去空格、状态 PUBLISHED、可按 ID 读取 |
| createPostRejectsInvalidTitle | 标题不足 3 字符被拒绝 |
| createPostRejectsInactiveUser | 非 ACTIVE 用户被拒绝 |
| createPostRejectsMutedUser | 禁言用户被拒绝 |
| createPostRejectsDisabledBoard | 板块被禁用时拒绝发帖 |
| createPostRejectsDisabledGame | 板块所属游戏被禁用时拒绝发帖 |
| createPostRejectsSensitiveTitle | 敏感词过滤覆盖标题 |
| hiddenPostIsNotReadable | 隐藏后按 POST_NOT_FOUND 处理 |
| addCommentNotifiesPostAuthor | 评论保存成功并通知帖子作者（POST_COMMENT） |
| addCommentRejectedOnHiddenPost | 隐藏帖子不能继续评论 |
| listPostsReturnsOnlyPublishedNewestFirst | 列表只含已发布帖子，最新在前，且按板块过滤 |
| hidePostAndCommentAreIdempotent | 帖子 / 评论隐藏幂等，各只通知一次（CONTENT_HIDDEN） |

当前测试结果：

```text
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 7. Demo 输出

```text
Created post: 1001, board=1, status=PUBLISHED
Created post: 1002, board=2, status=PUBLISHED
Published posts in board 1: [1001]
Comment: 5001, comments on post 1001=1
Muted user rejected: USER-403-1 / User is muted
Sensitive post rejected: CONTENT-422-2 / Text contains sensitive words
Disabled board rejected: CONTENT-410 / Board is disabled
After hide: visible posts in board 1=0
Hidden post rejected: CONTENT-404 / Post not found
Notifications:
  - userId=2, type=POST_COMMENT, content=New comment on your post 1001, comment id 5001
  - userId=2, type=CONTENT_HIDDEN, content=Your post 1001 was hidden, reason: off-topic
```

## 8. 说明

- 本模块定位为“可独立编译运行的领域核心”，没有引入 Spring 和数据库；真正的 Web 层、MyBatis 仓储、JWT 权限在完整项目中接入。
- 代码使用 Java 17 语法，JDK 21 可编译；测试使用 JUnit 5.10.2。
- DTO 使用 record，领域对象保留状态变更方法，接口与实现分离，便于测试和维护；内存仓储的 Map 为并发容器，写操作加锁，可安全用于演示和测试。
