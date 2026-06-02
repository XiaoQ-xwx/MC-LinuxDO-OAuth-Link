[根目录](../../../../../../../CLAUDE.md) > [oauthlink](../) > **api**

# api -- 公共 API 层

## 模块职责

为下游插件提供两种查询绑定状态的入口：
1. **静态门面** `OAuthLinkAPI` -- 零依赖，一行调用
2. **Bukkit ServicesManager** 接口 `OAuthLinkProvider` -- 依赖注入方式

## 入口与启动

- `OAuthLinkAPI.register(provider)` -- 在 `OAuthLinkService.onEnable()` 中调用
- `OAuthLinkAPI.unregister(provider)` -- 在 `OAuthLinkService.onDisable()` 中调用
- `OAuthLinkProvider` 通过 `Bukkit.getServicesManager().register()` 注册

## 对外接口

### OAuthLinkAPI（静态门面）

| 方法 | 返回值 | 说明 |
| :--- | :--- | :--- |
| `isLinked(UUID playerId)` | `boolean` | 检查玩家是否已绑定且 Token 未过期 |
| `getLinkedAccount(UUID playerId)` | `Optional<LinkedAccount>` | 获取绑定信息（不含 Token） |
| `unlink(UUID playerId)` | `CompletableFuture<Void>` | 异步解除绑定 |

### OAuthLinkProvider（服务接口）

| 方法 | 返回值 | 说明 |
| :--- | :--- | :--- |
| `isLinked(UUID playerId)` | `boolean` | 同 API |
| `getLinkedAccount(UUID playerId)` | `Optional<LinkedAccount>` | 同 API |
| `unlink(UUID playerId)` | `CompletableFuture<Void>` | 同 API |

## 关键依赖与配置

- 无外部依赖
- 依赖 `model.LinkedAccount` record
- `@NotNull` 注解来自 `org.jetbrains:annotations`

## 数据模型

不直接操作数据，仅委托给 `OAuthLinkProvider` 实现（实际为 `OAuthLinkService`）。

## 测试与质量

- **测试状态：** 无独立测试
- **建议：** 测试 `register`/`unregister` 并发安全性，以及 `provider == null` 时的降级行为

## 常见问题 (FAQ)

**Q: 下游插件应该用哪个入口？**
A: 优先用 `OAuthLinkAPI` 静态门面，代码更简洁。如果已经使用 Bukkit ServicesManager 模式，可用 `OAuthLinkProvider`。

**Q: provider 为 null 时会发生什么？**
A: `isLinked()` 返回 `false`，`getLinkedAccount()` 返回 `Optional.empty()`，`unlink()` 返回失败 Future。

## 相关文件清单

| 文件 | 职责 |
| :--- | :--- |
| `OAuthLinkAPI.java` | 静态门面，volatile provider 引用 |
| `OAuthLinkProvider.java` | 服务接口定义 |

## 变更记录 (Changelog)

| 日期 | 变更 |
| :--- | :--- |
| 2026-06-01 | 初始化模块文档 |
