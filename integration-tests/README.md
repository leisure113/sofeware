# Integration Tests — 四模块集成测试

本模块验证 user-module、content-module、team-module、governance-module 的跨模块接口交互。

## 运行

在项目根目录执行：

```bat
C:\Coding\apache-maven-3.10.0\bin\mvn.cmd -B -Dmaven.repo.local=.m2repo -pl integration-tests -am test
```

或执行全量构建：

```bat
mvn verify
```

## 测试内容

共 10 个场景：

- 正常：注册/认证用户创建队伍并被申请、审批；
- 异常：MUTED/未认证用户被拦截、非管理员处理举报；
- 内容治理：举报帖子/评论 → 管理员处理 → 隐藏；
- 用户治理：举报用户 → 禁言 → 组队申请被拒；
- 队伍治理：举报队伍 → 关闭 → 申请被拒；
- 故障传播：目标模块抛异常时举报保持 PENDING，不写日志/不通知；
- 通知链路：评论、申请、举报结果通知。

## 适配器

`support/` 下的 Adapter 把各模块端口接到真实服务：

- `UserQueryAdapter`、`AdminQueryAdapter`
- `GameQueryAdapter`
- `NotificationAdapter`
- `ContentAdminAdapter`、`UserAdminAdapter`、`TeamAdminAdapter`
- `SensitiveWordAdapter`、`CapturingMailService`

当前没有 Web Controller/HTTP 层，因此使用 JUnit 5 进程内集成测试；如果后续增加 web-adapter，可把同一批场景映射为 Postman/curl 测试。

## 结果

- 10/10 通过；
- 红阶段发现 2 个 MUTED 错误码失败，修复为 `USER_MUTED` 优先于 `USER_NOT_ACTIVE`；
- 缺陷记录见 `docs/github-issues/`，报告见 `docs/实验8-集成测试报告.docx`。
