[根目录](../../../../../../../CLAUDE.md) > [oAuth_Framework](../) > **oauth**

# oauth -- OAuth2 客户端与错误体系

## 模块职责

包含 OAuth2 协议实现、临时状态管理和错误处理：
- `LinuxDoOAuthClient` -- 所有与 LinuxDO 的 HTTP 通信
- `PendingOAuthRegistry` -- 线程安全的临时状态注册表
- `OAuthError` / `OAuthException` -- 类型化错误体系

## 入口与启动

- `LinuxDoOAuthClient` 在 `OAuth_Framework.onEnable()` 中构造，传入 `HttpClient`、`ObjectMapper`、`OAuthConfig`
- `PendingOAuthRegistry` 在插件启动时构造，传入 `OAuthCodeGenerator`、`Clock`、TTL 配置

## 对外接口

### LinuxDoOAuthClient

| 方法 | 返回值 | 说明 |
| :--- | :--- | :--- |
| `buildAuthorizationUri(String state)` | `URI` | 构建授权页面 URL（含 client_id, redirect_uri, state） |
| `exchangeCode(String code)` | `CompletableFuture<OAuthTokens>` | 用 authorization_code 换取 access_token |
| `fetchProfile(String accessToken)` | `CompletableFuture<LinuxDoProfile>` | 获取 LinuxDO 用户信息（含 trust_level, likes_received） |

### PendingOAuthRegistry

| 方法 | 返回值 | 说明 |
| :--- | :--- | :--- |
| `createState(UUID playerId, String playerName)` | `String` | 生成 CSRF state 并关联玩家身份 |
| `consumeState(String state)` | `StateEntry \| null` | 验证并一次性消费 state |
| `storeAuthorization(LinuxDoProfile, OAuthTokens)` | `String` | 存储授权结果并返回手动验证码 |
| `consumeCode(String code)` | `PendingAuthorization \| null` | 验证并一次性消费验证码 |
| `purgeExpired()` | `void` | 清理过期条目 |

### OAuthError 枚举

| 错误码 | 默认消息 |
| :--- | :--- |
| `CONFIG_INVALID` | 插件配置无效，请联系管理员 |
| `HTTP_SERVER_FAILED` | 内建 HTTP 服务器启动失败，请检查端口配置 |
| `INVALID_STATE` | OAuth 状态验证失败，请重新操作 |
| `EXPIRED_STATE` | OAuth 会话已过期，请重新登录 |
| `INVALID_LINK_CODE` | 无效的验证码 |
| `EXPIRED_LINK_CODE` | 验证码已过期，请重新获取 |
| `TOKEN_EXCHANGE_FAILED` | Token 交换失败，请稍后重试 |
| `USERINFO_FAILED` | 获取用户信息失败，请稍后重试 |
| `PROFILE_INVALID` | 返回的用户信息无效 |
| `ALREADY_LINKED` | 该 LinuxDO 账号已绑定其他玩家 |
| `TOKEN_EXPIRED` | 授权已过期，请重新登录 |
| `STORAGE_FAILED` | 数据存储失败，请稍后重试 |
| `NETWORK_FAILED` | 网络请求失败，请检查网络连接 |

## 关键依赖与配置

- `LinuxDoOAuthClient` 依赖 `java.net.http.HttpClient`（JDK 内置）
- `PendingOAuthRegistry` 使用 `ConcurrentHashMap` 保证线程安全（HTTP 回调线程 + Bukkit 主线程共享）
- State 和 LinkCode 均由 `OAuthCodeGenerator`（SecureRandom）生成
- Token 日志绝不打印敏感信息

## 数据模型

- `OAuthTokens` -- 仅内存存在，不持久化，不通过 API 暴露
- `PendingAuthorization` -- 临时存储授权结果供手动验证码消费

## 测试与质量

| 测试文件 | 状态 |
| :--- | :--- |
| `OAuthErrorTest.java` | 通过 |
| `OAuthExceptionTest.java` | 通过 |
| `PendingOAuthRegistryTest.java` | **API 签名过时**（使用 `createState()` 无参旧 API，当前为 `createState(UUID, String)`） |
| `LinuxDoOAuthClient` | 无测试 |

## 常见问题 (FAQ)

**Q: `PendingOAuthRegistryTest` 的 API 不匹配怎么办？**
A: 测试文件中的 `createState()` 需改为 `createState(UUID.randomUUID(), "testPlayer")`，`validateAndConsumeState()` 需改为 `consumeState()` 并检查返回的 `StateEntry`。

## 相关文件清单

| 文件 | 职责 |
| :--- | :--- |
| `LinuxDoOAuthClient.java` | HTTP 通信（137 行） |
| `PendingOAuthRegistry.java` | 状态注册表（111 行） |
| `OAuthError.java` | 错误枚举（33 行） |
| `OAuthException.java` | 类型化异常（35 行） |

## 变更记录 (Changelog)

| 日期 | 变更 |
| :--- | :--- |
| 2026-06-01 | 初始化模块文档；记录 PendingOAuthRegistryTest API 过时问题 |
