[根目录](../../../../../../../CLAUDE.md) > [oauthlink](../) > **model**

# model -- 数据模型

## 模块职责

定义所有数据传输对象（DTO），全部使用 Java `record`。

## 模型列表

### LinkedAccount（持久化）

已绑定的账号信息，暴露给下游插件。**不含 access_token。**

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `playerId` | `UUID` | Minecraft 玩家 UUID |
| `playerName` | `String` | 玩家名称 |
| `linuxDoId` | `String` | LinuxDO 用户 ID |
| `linuxDoUsername` | `String` | LinuxDO 用户名 |
| `linuxDoDisplayName` | `String` | LinuxDO 显示名称 |
| `trustLevel` | `int` | 信任等级（0-4，-1 表示未知） |
| `likesReceived` | `int` | 获赞数（社区分数，-1 表示未知） |
| `rawProfileJson` | `String` | 完整 user-info API 响应 JSON，供下游解析任意字段 |
| `linkedAt` | `Instant` | 绑定时间 |
| `tokenExpiresAt` | `Instant` | Token 过期时间 |

派生方法：
- `isTokenExpired(Clock, Duration)` -- 判断 Token 是否过期
- `isActive(Clock, Duration)` -- 判断账号是否仍有效
- `getProfileUrl()` -- 返回 `https://linux.do/u/{username}/summary`
- `getTrustLevelLabel()` -- 返回 "TL0"-"TL4" 或 "未知"（优先用 trustLevel 字段，降级到 rawProfileJson 解析）
- `getLikesReceivedLabel()` -- 返回获赞数或 "N/A"（同上降级策略）

### LinuxDoProfile（内存）

从 user-info API 解析的用户信息，仅在绑定流程中使用，不持久化。

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | `String` | LinuxDO 用户 ID |
| `username` | `String` | 用户名 |
| `displayName` | `String` | 显示名称 |
| `trustLevel` | `int` | 信任等级（0-4，-1 未知） |
| `likesReceived` | `int` | 获赞数（-1 未知） |
| `rawJson` | `String` | 完整 API 响应 JSON |

### OAuthTokens（内存，绝密）

Token 信息，**仅内部使用，绝不通过公共 API 暴露。**

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `accessToken` | `String` | OAuth Access Token |
| `expiresAt` | `Instant` | Token 过期时间 |

### PendingAuthorization（内存临时）

回调完成后暂存的授权信息，供 `/linkld <code>` 手动验证码消费。

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `linkCode` | `String` | 8 位验证码 |
| `profile` | `LinuxDoProfile` | 用户信息 |
| `tokens` | `OAuthTokens` | Token 信息 |
| `expiresAt` | `Instant` | 过期时间 |

## 关键依赖与配置

- 所有 model 类均为 Java `record`，自动生成 equals/hashCode/toString
- `LinkedAccount` 使用 Jackson `ObjectMapper` 解析 `rawProfileJson`（降级查询时）
- `LinkedAccount` 保留向后兼容构造函数（不含 trustLevel/likesReceived/rawProfileJson）

## 测试与质量

- **测试状态：** 4 个测试文件（`LinkedAccountTest`, `LinuxDoProfileTest`, `OAuthTokensTest`, `PendingAuthorizationTest`）
- 覆盖全部 4 个 record 类型

## 常见问题 (FAQ)

**Q: 下游插件如何获取信任等级？**
A: 使用 `LinkedAccount.getTrustLevelLabel()`，或直接从 `rawProfileJson()` 解析任意字段。

**Q: `rawProfileJson` 和单独的字段（trustLevel, likesReceived）有什么区别？**
A: 单独字段是绑定时解析的快照，用于快速查询。`rawProfileJson` 是完整原始响应，用于下游插件获取我们未单独提取的字段（如 `badge_count`, `groups` 等）。

## 相关文件清单

| 文件 | 职责 |
| :--- | :--- |
| `LinkedAccount.java` | 持久化绑定记录（113 行） |
| `LinuxDoProfile.java` | 用户信息 DTO（51 行） |
| `OAuthTokens.java` | Token 记录（19 行） |
| `PendingAuthorization.java` | 待消费授权（22 行） |

## 变更记录 (Changelog)

| 日期 | 变更 |
| :--- | :--- |
| 2026-06-01 | 初始化模块文档；记录 trustLevel/likesReceived/rawProfileJson 扩展字段和降级查询策略 |
