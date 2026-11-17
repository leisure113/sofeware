# 用户模块测试总结（user-module）

> 实验：单元测试与 JaCoCo 覆盖率门禁
> 模块：`user-module`（独立 Maven 模块，JDK 17 语法，JUnit 5.10.2，JaCoCo 0.8.12）
> 日期：2026-11-17

## 1. 最终结果

| 项目 | 结果 |
|---|---|
| 测试总数 | **90**（基线 12 + 新增 78） |
| 测试通过 | 90 / 90，Failures 0，Errors 0，Skipped 0 |
| `mvn verify` | **BUILD SUCCESS**（测试 + JaCoCo report + check 全部通过） |
| 新增运行时依赖 | 无（仅使用 JUnit 5，pom 未改动） |
| JaCoCo 阈值 | 保持 instruction ≥ 0.85、branch ≥ 0.75、line ≥ 0.85，**未下调** |

执行命令（在 `user-module` 目录下）：

```bat
C:\Coding\apache-maven-3.10.0\bin\mvn.cmd -B "-Dmaven.repo.local=C:\Users\warxi\Desktop\dsh\Software Engineering\.m2repo" verify
```

输出摘要：

```text
[INFO] Tests run: 90, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- jacoco:0.8.12:report (report) @ user-module ---
[INFO] --- jacoco:0.8.12:check (check) @ user-module ---
[INFO] BUILD SUCCESS
```

## 2. 覆盖率（取自 `target/site/jacoco/jacoco.csv`）

| 指标 | 基线 | 最终 | 目标 | 对比 |
|---|---|---|---|---|
| 指令 INSTRUCTION | 1126/1228 = **91.69%** | 1276/1283 = **99.45%** | ≥ 85% | +7.76 pp |
| 分支 BRANCH | 38/56 = **67.86%** | 62/62 = **100.00%** | ≥ 75% | +32.14 pp |
| 行 LINE | 236/252 = **93.65%** | 261/263 = **99.24%** | ≥ 85% | +5.59 pp |

说明：

- 统计范围为 pom 中 JaCoCo 配置的全部类（`UserModuleDemo` 按既有配置排除）。
- 剩余未覆盖的 7 条指令 / 2 行集中在 `Pbkdf2PasswordHasher.derive` 的 `catch (GeneralSecurityException)` 兜底分支——标准 JDK 17/21 一定提供 `PBKDF2WithHmacSHA256`，该分支不可达，属于防御性代码。
- 分支覆盖 100%：所有可执行 `if / || / && / ==` 判定均被正常与异常用例覆盖。

## 3. TDD 红-绿过程（changePassword）

### 3.1 需求

在 `UserService` 新增：

```java
void changePassword(long userId, String oldPassword, String newPassword);
```

规则：用户必须存在；旧密码必须匹配；新密码 ≥ 8 位；成功后更新密码哈希并持久化；旧密码立即失效。

### 3.2 红阶段（先写测试）

先只加入接口声明、错误码 `INVALID_OLD_PASSWORD` 和临时实现：

```java
@Override
public void changePassword(long userId, String oldPassword, String newPassword) {
    throw new UnsupportedOperationException("changePassword is not implemented yet");
}
```

新增 `ChangePasswordTest` 的 4 个用例：成功改密、旧密码错误、新密码过短（7 位）、未知用户。
执行 `mvn test` 得到红阶段证据（完整日志保存在 `target/tdd-red-phase.log`）：

```text
[ERROR] Tests run: 4, Failures: 3, Errors: 1, Skipped: 0, Time elapsed: 1.198 s <<< FAILURE! -- in com.coplay.user.ChangePasswordTest
[ERROR]   ChangePasswordTest.changesPasswordForExistingUser:26 ? UnsupportedOperation changePassword is not implemented yet
[ERROR]   ChangePasswordTest.rejectsWrongOldPassword:46 Unexpected exception type thrown, expected: <com.coplay.user.support.BizException> but was: <java.lang.UnsupportedOperationException>
[ERROR]   ChangePasswordTest.rejectsWeakNewPassword:60 Unexpected exception type thrown, expected: <com.coplay.user.support.BizException> but was: <java.lang.UnsupportedOperationException>
[ERROR]   ChangePasswordTest.rejectsUnknownUser:71 Unexpected exception type thrown, expected: <com.coplay.user.support.BizException> but was: <java.lang.UnsupportedOperationException>
[ERROR] Tests run: 16, Failures: 3, Errors: 1, Skipped: 0
[ERROR] BUILD FAILURE
```

红阶段汇总：**Tests run: 16, Failures: 3, Errors: 1**（原有 12 个仍通过，新增 4 个全部失败）。

### 3.3 绿阶段（实现）

实现要点：

1. `UserRepository.findById` 找不到用户 → `USER_NOT_FOUND`；
2. `PasswordHasher.matches(oldPassword, hash)` 失败 → `INVALID_OLD_PASSWORD`（不修改数据）；
3. 复用 `validatePassword` 校验新密码强度（`WEAK_PASSWORD`）；
4. `User.changePassword(newHash)` 更新哈希后 `save`；
5. 领域对象 `User.passwordHash` 由 `final` 改为可变，并新增 `changePassword(String)`，保持状态变更封装在领域对象内。

实现后执行 `mvn test`：

```text
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in com.coplay.user.ChangePasswordTest
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

随后补充 4 个边界/顺序用例（新密码 8 位通过、null 新密码、null 旧密码、旧密码校验先于新密码强度），最终 `ChangePasswordTest` 共 8 个用例；全模块 `mvn verify` 为 **90/90 通过、BUILD SUCCESS**。

## 4. 缺陷与修复

**结论：未发现既有功能的真实缺陷。**

- 本轮新增测试覆盖了注册、验证码、认证、登录、状态治理、领域对象、仓储、哈希与 Token 的异常/边界路径，未暴露与业务规则不符的行为。
- 唯一 API 缺口是需求新增的 `changePassword`，属于功能缺失而非缺陷；已按 TDD 补齐并新增回归用例。
- 两处按现有契约固化的行为（非缺陷）：
  - `register(null)`、`changeStatus(..., null)` 等编程式误用抛 `NullPointerException`，调用方 DTO 校验应保证非空；
  - README 明确“不额外限制管理员修改自己”，测试固化了管理员可改自己状态并恢复的规则。
- 未修改其他模块、根 README、报告 DOCX、Git 或 `.m2repo`。

## 5. 新增测试类与关键场景

| 测试类 | 用例数 | 关键场景 |
|---|---|---|
| `RegisterValidationTest` | 8 | 学号 null/空白；邮箱 null/无 `@`；密码 null、7 位拒绝、8 位通过；重复学号/邮箱；trim + 小写规范化；昵称回退学号 |
| `CampusVerificationTest` | 13 | 未知/空邮箱；无验证码；null 验证码；错误 1..5 次；第 5 次可成功；超限后正确码也被拒；10 分钟整点过期；过期优先于次数；成功后删除验证码且不可重放；重发重置次数并延长有效期；邮箱规范化 |
| `LoginTest` | 7 | 未知邮箱；PENDING/MUTED/BANNED 均拒绝；错误/null 密码；邮箱大小写与空白；多次登录 Token 唯一；资料快照 |
| `ChangeStatusTest` | 7 | 未知操作者；未知目标；非管理员（改他人/改自己）；管理员切换四种状态；管理员改自己；null 状态 |
| `ChangePasswordTest` | 8 | 成功改密（新密码可登录、旧密码失效）；旧密码错误；新密码 7 位拒绝/8 位通过；null 旧/新密码；未知用户；旧密码校验先于新密码强度 |
| `UserQueryTest` | 4 | `getProfile` 未知用户/资料快照；`isActive/isMuted/isAdmin` 对已知/未知用户 |
| `Pbkdf2PasswordHasherTest` | 9 | 存储格式；16 字节盐、32 字节密钥；随机盐；默认/自定义迭代次数；错误密码；null 参数；畸形哈希（前缀/段数/迭代次数/base64）；非法构造参数 |
| `InMemoryTokenServiceTest` | 4 | Token 反查；唯一性（25 个）；未知/null/空 Token → `INVALID_TOKEN` |
| `UserTest` | 7 | 字段读取；`activate`；状态迁移与三个谓词；`changePassword` 替换哈希；构造参数非空 |
| `VerificationCodeTest` | 6 | 初始 0 次/剩余 5 次；次数只增且剩余不为负；过期边界（到期前 1ns / 到期时刻）；`matches` 对 null/短/长/错误值；getter |
| `InMemoryRepositoryTest` | 4 | 自增 id 跳号；按 id/学号/邮箱查询；验证码按邮箱覆盖、删除与未知邮箱 no-op |
| `SendCodeCommandTest` | 1 | `SendCodeCommand` record 契约 |
| `UserServiceTest`（保留） | 12 | 基线主流程用例，未删改 |

测试确定性：

- 所有服务用例注入 `Clock.fixed(2026-11-17T10:00:00Z)`；
- 过期/重发边界使用 `Clock.offset(...)` 构造同一仓储上的不同时钟服务（9:59、10:00、11:00、+2min）；
- 邮件使用 `FakeMailService` 内存发件箱，Token、仓储均使用内存实现；
- 验证码错误值由 `wrongCode()` 生成，保证与真实验证码不同，避免随机偶合；
- PBKDF2 格式/异常用例使用 1000 次迭代加速，服务用例保持默认 120000 次。

## 6. 变更文件

主代码：

- `src/main/java/com/coplay/user/api/UserService.java`：新增 `changePassword` 声明与 Javadoc；
- `src/main/java/com/coplay/user/service/UserServiceImpl.java`：实现 `changePassword`；
- `src/main/java/com/coplay/user/domain/User.java`：`passwordHash` 支持替换，新增 `changePassword(String)`；
- `src/main/java/com/coplay/user/support/ErrorCode.java`：新增 `INVALID_OLD_PASSWORD`（USER-401-2）。

测试代码：

- 新增 `UserServiceTestSupport`（共享夹具）与 12 个测试类（见上表，78 个新用例）；
- 保留 `UserServiceTest`（12 个基线用例）与 `FakeMailService`。

文档：

- `user-module/README.md`：职责、调用面、业务规则、错误码、测试清单与覆盖率结果；
- `docs/user-test-summary.md`：本文件。

## 7. 复现方式

```bat
cd "C:\Users\warxi\Desktop\dsh\Software Engineering\user-module"

rem 仅测试（90 个）
C:\Coding\apache-maven-3.10.0\bin\mvn.cmd -B "-Dmaven.repo.local=..\.m2repo" test

rem 测试 + JaCoCo 报告 + 覆盖率门禁
C:\Coding\apache-maven-3.10.0\bin\mvn.cmd -B "-Dmaven.repo.local=..\.m2repo" verify
```

报告位置：`user-module/target/site/jacoco/index.html`；机器可读数据：`user-module/target/site/jacoco/jacoco.csv`。
