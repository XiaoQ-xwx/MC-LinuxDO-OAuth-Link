[根目录](../../../../../../../CLAUDE.md) > [oauthlink](../) > **event**

# event -- Bukkit 事件

## 模块职责

定义三个 Bukkit Event，供下游插件监听 OAuth 生命周期变化。

## 事件列表

### PlayerOAuthSuccessEvent

- **触发时机：** 玩家成功绑定 LinuxDO 账号后（主线程）
- **可取消：** 是（`Cancellable`）
- **携带数据：** `LinkedAccount`（不含 Token）
- **典型用途：** 同步权限组、发放称号、记录日志

```java
@EventHandler
public void onOAuthSuccess(PlayerOAuthSuccessEvent event) {
    LinkedAccount account = event.getAccount();
    // 根据 account.linuxDoUsername() / account.trustLevel() 发放权限
}
```

### PlayerOAuthFailEvent

- **触发时机：** 绑定尝试失败时（主线程）
- **可取消：** 是（`Cancellable`）
- **携带数据：** `playerId`（UUID, 因为玩家可能离线）、`OAuthError`（枚举，可编程判断）、`safeMessage`（玩家安全消息）
- **典型用途：** 记录失败日志、向管理员告警

```java
@EventHandler
public void onOAuthFail(PlayerOAuthFailEvent event) {
    if (event.getError() == OAuthError.ALREADY_LINKED) {
        // 特殊处理重复绑定
    }
}
```

### PlayerOAuthUnlinkEvent

- **触发时机：** 玩家解除绑定后（主线程，数据已删除）
- **可取消：** 否（不可取消，下游插件必须级联撤销权限）
- **携带数据：** `playerId`、`playerName`、`linuxDoId`、`linuxDoUsername`
- **典型用途：** 撤销因 OAuth 绑定而授予的所有权限/称号/白名单

```java
@EventHandler
public void onOAuthUnlink(PlayerOAuthUnlinkEvent event) {
    // 撤销玩家因 LinuxDO 身份获得的所有权限
    revokePermissions(event.getPlayerId());
}
```

## 入口与启动

事件在 `OAuthLinkService` 中通过 `Bukkit.getPluginManager().callEvent()` 触发，所有事件在主线程触发。

## 关键依赖与配置

- 纯 Bukkit Event API，无外部依赖
- `PlayerOAuthSuccessEvent` 和 `PlayerOAuthFailEvent` 实现 `Cancellable`，允许下游插件阻止后续处理
- `PlayerOAuthUnlinkEvent` 不可取消 -- 解绑已是最终状态，下游必须级联处理

## 数据模型

- `PlayerOAuthSuccessEvent` 携带完整 `LinkedAccount` record
- `PlayerOAuthUnlinkEvent` 仅携带关键标识字段（数据已从存储中删除）

## 测试与质量

- **测试状态：** 无独立测试（低优先级，纯 POJO 事件类）

## 常见问题 (FAQ)

**Q: 为什么 `PlayerOAuthUnlinkEvent` 不可取消？**
A: 解除绑定是用户主动操作，数据已从存储中删除。如果允许下游取消，会导致数据与事件状态不一致。下游插件应在收到此事件后无条件撤销权限。

**Q: 如何区分"绑定成功"和"重新绑定"？**
A: `PlayerOAuthSuccessEvent` 在每次绑定成功时触发（含重新绑定）。下游插件可通过检查之前是否已有该玩家的记录来判断。

## 相关文件清单

| 文件 | 职责 |
| :--- | :--- |
| `PlayerOAuthSuccessEvent.java` | 绑定成功事件（53 行） |
| `PlayerOAuthFailEvent.java` | 绑定失败事件（76 行） |
| `PlayerOAuthUnlinkEvent.java` | 解除绑定事件（74 行） |

## 变更记录 (Changelog)

| 日期 | 变更 |
| :--- | :--- |
| 2026-06-01 | 初始化模块文档；记录 PlayerOAuthUnlinkEvent（新增）不可取消的设计决策 |
