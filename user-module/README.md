# User Module — 用户模块核心代码

课程设计《同游社——校园游戏同好与开黑搭子社区》的用户模块独立实现。
本模块只实现**用户**一个模块，不依赖 Spring、MySQL 和其他模块，可独立编译、测试、打包、运行。

## 1. 模块职责

- 校园身份注册：学号/邮箱唯一、密码强度校验、邮箱验证码。
- 校园认证：6 位验证码、10 分钟有效、最多 5 次尝试、成功后账号 `ACTIVE`。
- 登录与令牌：PBKDF2 密码校验，登录成功后签发 Token。
- 账号治理：管理员可把用户改为 `PENDING_VERIFICATION / ACTIVE / MUTED / BANNED`。
- 状态查询：向其他模块提供 `isActive`、`isMuted`、`isAdmin` 三个只读判断。

## 2. 目录结构

```text
user-module/
├─ pom.xml
├─ README.md
├─ build.bat / test.bat / run.bat
├─ src/main/java/com/coplay/user/
│  ├─ api/            UserService、MailService、TokenService、PasswordHasher
│  ├─ domain/         User、UserStatus、Role、VerificationCode
│  ├─ dto/            RegisterCommand、SendCodeCommand、VerifyCampusCommand、
│  │                  LoginCommand、UserProfile、LoginResult
│  ├─ repository/     UserRepository、VerificationCodeRepository、
│  │                  InMemoryUserRepository、InMemoryVerificationCodeRepository
│  ├─ support/        ErrorCode、BizException、Pbkdf2PasswordHasher、InMemoryTokenService
│  ├─ service/        UserServiceImpl
│  └─ UserModuleDemo.java
└─ src/test/java/com/coplay/user/
   ├─ UserServiceTest.java
   └─ fake/FakeMailService.java
```

## 3. 接口与模块间调用

`UserServiceImpl` 只依赖端口，不依赖具体实现：

```text
UserServiceImpl
 ├─ UserRepository                用户持久化（内存实现，真实项目换 MyBatis）
 ├─ VerificationCodeRepository    验证码持久化（同一邮箱只保留最新一条）
 ├─ MailService                   发送验证码（对应邮件/通知模块）
 ├─ PasswordHasher                PBKDF2WithHmacSHA256 哈希与校验
 ├─ TokenService                  登录令牌（可替换为 JWT 实现）
 └─ Clock                         注入时钟，测试中固定时间、验证过期
```

其他模块需要的调用面：

| 方法 | 用途 | 调用方示例 |
|---|---|---|
| `isActive(userId)` | 是否 `ACTIVE` | 组队、发帖、评论前校验 |
| `isMuted(userId)` | 是否 `MUTED` | 组队申请、发言前校验 |
| `isAdmin(userId)` | 是否 `ADMIN` | 管理端接口鉴权 |
| `getProfile(userId)` | 读取用户资料 | 展示昵称、学号 |

真实项目中只需把两个 `InMemory*Repository` 换成 MyBatis 实现，把 `MailService`、`TokenService` 换成真实 Bean，`UserServiceImpl` 不需要修改。

## 4. 业务规则

- `register`：学号/邮箱唯一；邮箱必须包含 `@`；密码长度 ≥ 8；创建 `PENDING_VERIFICATION` 用户，生成 6 位验证码，10 分钟过期，通过 `MailService` 发送，返回资料。
- `sendVerificationCode`：用户必须存在；生成并保存新验证码后重新发送；旧验证码被覆盖。
- `verifyCampus`：按邮箱找用户；验证码必须存在、未过期、尝试次数 < 5；输错会累加尝试次数并拒绝；成功后置为 `ACTIVE` 并删除验证码。
- `login`：只有 `ACTIVE` 用户可登录；密码错误返回 `INVALID_CREDENTIALS`；成功后由 `TokenService` 签发 Token，返回 Token + 资料。
- `changeStatus`：操作者必须是 `ADMIN`，目标用户必须存在，然后直接应用新状态（不额外限制管理员修改自己）。
- `isActive / isMuted / isAdmin`：分别判断状态或角色；用户不存在时返回 `false`。
- 密码使用 PBKDF2WithHmacSHA256 + 16 字节随机盐，存储格式 `pbkdf2-sha256$迭代次数$盐$哈希`，比较使用 `MessageDigest.isEqual`。
- `InMemoryTokenService` 用 `UUID` 生成 Token 并维护 `token -> userId` 映射；未知 Token 抛 `INVALID_TOKEN`。

## 5. 错误码

| ErrorCode | 编码 | 含义 |
|---|---|---|
| `USER_NOT_FOUND` | USER-404 | 用户不存在 |
| `DUPLICATE_STUDENT_NO` | USER-409-1 | 学号已注册 |
| `DUPLICATE_EMAIL` | USER-409-2 | 邮箱已注册 |
| `INVALID_STUDENT_NO` | USER-422-4 | 学号为空 |
| `INVALID_EMAIL` | USER-422-1 | 邮箱不含 `@` |
| `WEAK_PASSWORD` | USER-422-2 | 密码少于 8 位 |
| `INVALID_CODE` | USER-422-3 | 验证码错误 |
| `CODE_EXPIRED` | USER-410 | 验证码过期 |
| `TOO_MANY_ATTEMPTS` | USER-429 | 尝试次数过多 |
| `USER_NOT_ACTIVE` | USER-403 | 用户不是 ACTIVE |
| `INVALID_CREDENTIALS` | USER-401 | 邮箱或密码错误 |
| `NOT_ADMIN` | USER-403-1 | 非管理员操作 |
| `INVALID_TOKEN` | USER-401-1 | Token 无效 |

## 6. 运行

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

在 `user-module` 目录下：

```bat
C:\Coding\apache-maven-3.10.0\bin\mvn.cmd -B "-Dmaven.repo.local=..\.m2repo" test
C:\Coding\apache-maven-3.10.0\bin\mvn.cmd -B "-Dmaven.repo.local=..\.m2repo" package
java -jar target\user-module-1.0.0.jar
```

如果 `mvn` 已在 PATH，把 `mvn.cmd` 的绝对路径换成 `mvn` 即可。

当前测试结果（`mvn test`）：

```text
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 7. 测试清单（JUnit 5，12 个）

| 用例 | 验证点 |
|---|---|
| registerCreatesPendingUser | 注册成功、状态 PENDING、验证码 6 位、10 分钟过期、邮件已发 |
| registerRejectsDuplicates | 重复学号、重复邮箱分别被拒绝 |
| registerRejectsWeakPassword | 7 位密码被拒绝，不落库、不发邮件 |
| sendVerificationCodeIssuesNewCode | 重发验证码成功，旧验证码被覆盖，新验证码可完成认证 |
| verifyCampusActivatesUser | 正确验证码认证成功、状态 ACTIVE、验证码被消费 |
| wrongCodeIncrementsAttempts | 错误验证码被拒绝、attempts+1、剩余 4 次、账号仍未激活 |
| expiredCodeIsRejected | 时钟前进 11 分钟后验证码过期 |
| loginReturnsToken | 登录成功、Token 可反查 userId、资料状态 ACTIVE |
| loginRejectsInactiveUser | 待认证用户登录被拒绝 |
| loginRejectsWrongPassword | 错误密码返回 INVALID_CREDENTIALS |
| adminCanChangeStatus | 管理员禁言/封禁生效，isMuted、isAdmin 正确 |
| nonAdminCannotChangeStatus | 普通用户改状态被拒绝，状态保持不变 |

测试使用 `FakeMailService` 作为内存发件箱，服务构造时注入 `Clock.fixed(...)`；过期用例通过 `Clock.offset(...)` 前进 11 分钟。

## 8. Demo 输出

```text
CoPlay user module demo
=======================
Registered: id=1001, studentNo=20230001, status=PENDING_VERIFICATION
Verification code sent to alice@coplay.edu: 650128 (valid for 10 minutes)
Wrong code rejected: USER-422-3 / Verification code is invalid: 4 attempts left
Campus verified: status=ACTIVE
Login token: <generated-uuid>
Token resolves to userId=1001
Admin muted user: isActive=false, isMuted=true
Muted user login rejected: USER-403 / User is not active
Admin restored user: isActive=true
Duplicate student number rejected: USER-409-1 / Student number is already registered
Duplicate email rejected: USER-409-2 / Email is already registered
Weak password rejected: USER-422-2 / Password must be at least 8 characters
Non-admin change rejected: USER-403-1 / Only an administrator can perform this action
Expired code rejected: USER-410 / Verification code has expired
Unknown token rejected: USER-401-1 / Token is invalid
Mail outbox:
  - to=alice@coplay.edu, code=650128
  - to=alice@coplay.edu, code=399816
```

验证码与 Token 每次运行随机生成，上面的值仅为示例。

## 9. 说明

- 仅使用 JDK 17 语法与标准库，运行期零第三方依赖；JUnit 5.10.2 只用于测试。
- DTO 全部使用 `record`；领域对象封装状态变更；业务异常带稳定错误码。
- 模块内不含 Spring、数据库、HTTP 层；完整项目中由 Controller 调用 `UserService`，由 MyBatis 实现仓储，由 JWT 实现 `TokenService`。
