# 同游社——校园游戏同好与开黑搭子社区

软件工程课程设计项目。项目按“用户与认证、游戏与内容、组队、治理与统计”四个模块拆分，每个模块是一个可独立编译、测试、运行的 Maven 模块。

## 模块

| 模块 | 目录 | 核心内容 |
|---|---|---|
| 用户与认证 | `user-module/` | 注册、校园认证、登录、用户状态、Token |
| 游戏与内容 | `content-module/` | 版块、帖子、评论、敏感词、内容隐藏 |
| 组队 | `team-module/` | 发布队伍、申请、审批、满员、关闭/过期、并发控制 |
| 治理与统计 | `governance-module/` | 举报、管理员处理、通知、审计日志、统计 |

模块之间通过端口接口调用；每个模块内部使用内存仓储和 Fake 适配器，保证可以脱离 Spring/MySQL 独立运行。完整系统中替换为 MySQL 仓储和 Spring Bean 即可。

## 环境

- JDK 17+（本机 JDK 21）
- Maven 3.10+
- JUnit 5（Maven 自动下载）

## 构建全部模块

在项目根目录执行：

```bat
test-all.bat
build-all.bat
```

也可以直接使用 Maven：

```bat
mvn test
mvn package
```

如果 `mvn` 不在 PATH，使用：

```bat
C:\Coding\apache-maven-3.10.0\bin\mvn.cmd -B -Dmaven.repo.local=.m2repo test
```

当前测试规模：用户 12 个、内容 12 个、组队 16 个、治理 12 个，共 **52 个 JUnit 5 测试**。

## 单独运行某个模块

以组队模块为例：

```bat
cd team-module
run.bat
```

其他模块同样有 `run.bat`、`test.bat`、`build.bat`。

## 文档

- `同游社-校园游戏开黑搭子社区-软件需求规格说明书-v1.0.docx`
- `同游社-UML用例描述与用例图-v1.0.docx`
- `同游社-系统概要设计说明书-v1.0.docx`
- `同游社-系统详细设计说明书-v1.0.docx`
- `docs/版本控制实验记录.md`

## 分支策略

- `master`：稳定版本
- `dev`：集成分支
- `feature/<module>`：模块开发分支

协作规范见 [CONTRIBUTING.md](CONTRIBUTING.md)。
