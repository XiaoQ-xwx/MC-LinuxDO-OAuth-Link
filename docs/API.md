# 下游插件开发指南

> [← 返回 README](../README.md)

下游插件通过 Bukkit Event 或静态 API 获取玩家绑定状态，实现基于 LinuxDO 身份的权限、称号等扩展功能。

## Maven / Gradle 依赖

将 `OAuth_Framework.jar` 作为编译依赖引入：

**Gradle (build.gradle.kts):**

```kotlin
dependencies {
    compileOnly(files("libs/OAuth_Framework.jar"))
}
```

**Maven (pom.xml):**

```xml
<dependency>
    <groupId>org.OAuth_Framework</groupId>
    <artifactId>OAuth_Framework</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>provided</scope>
    <systemPath>${project.basedir}/libs/OAuth_Framework.jar</systemPath>
</dependency>
```

## 事件监听

插件提供三个 Bukkit 事件，下游插件通过 `@EventHandler` 监听：

### PlayerOAuthSuccessEvent — 绑定成功

```java
import org.OAuth_Framework.oAuth_Framework.event.PlayerOAuthSuccessEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MyListener implements Listener {

    @EventHandler
    public void onOAuthSuccess(PlayerOAuthSuccessEvent event) {
        LinkedAccount account = event.getAccount();
        UUID playerId = account.playerId();
        String linuxDoUser = account.linuxDoUsername();

        getLogger().info("玩家 " + account.playerName()
            + " 绑定了 LinuxDO 账号: @" + linuxDoUser);

        // 根据信任等级发放称号
        String title = switch (account.getTrustLevelLabel()) {
            case "TL4" -> "§6[社区领袖]";
            case "TL3" -> "§b[活跃成员]";
            case "TL2" -> "§a[社区会员]";
            default -> "§7[新成员]";
        };
        // 设置玩家称号...
    }
}
```

### PlayerOAuthFailEvent — 绑定失败

```java
import org.OAuth_Framework.oAuth_Framework.event.PlayerOAuthFailEvent;

@EventHandler
public void onOAuthFail(PlayerOAuthFailEvent event) {
    UUID playerId = event.getPlayerId();
    String reason = event.getSafeMessage();
    OAuthError error = event.getError();

    getLogger().warning("玩家 " + playerId + " 绑定失败: " + reason);

    // 可选：向管理员发送告警
    if (error == OAuthError.ALREADY_LINKED) {
        // 检测到重复绑定尝试
    }
}
```

### PlayerOAuthUnlinkEvent — 解除绑定

```java
import org.OAuth_Framework.oAuth_Framework.event.PlayerOAuthUnlinkEvent;

@EventHandler
public void onOAuthUnlink(PlayerOAuthUnlinkEvent event) {
    LinkedAccount account = event.getAccount();

    getLogger().info("玩家 " + account.playerName()
        + " 解除了 LinuxDO 账号 @"
        + account.linuxDoUsername() + " 的绑定");

    // 清理该玩家的权限、称号等
    revokePermissions(account.playerId());
    removePrefix(account.playerId());
}
```

## 静态 API 查询

```java
import org.OAuth_Framework.oAuth_Framework.api.OAuthFrameworkAPI;
import org.OAuth_Framework.oAuth_Framework.model.LinkedAccount;

import java.util.Optional;

// 检查是否已绑定
if (OAuthFrameworkAPI.isLinked(player.getUniqueId())) {
    // 获取完整绑定信息
    Optional<LinkedAccount> opt = OAuthFrameworkAPI.getLinkedAccount(player.getUniqueId());
    opt.ifPresent(account -> {
        // 基础信息
        String linuxDoId = account.linuxDoId();
        String username = account.linuxDoUsername();
        String displayName = account.linuxDoDisplayName();

        // 信任等级: "TL0" ~ "TL4" 或 "未知"
        String trustLevel = account.getTrustLevelLabel();

        // 社区分数 (暂时弃用)
        String likes = account.getLikesReceivedLabel();

        // 论坛主页链接
        String profileUrl = account.getProfileUrl();
        // → https://linux.do/u/{username}/summary

        // 原始 API 响应 JSON（可解析任意额外字段）
        String rawJson = account.rawProfileJson();

        // 时间信息
        java.time.Instant linkedAt = account.linkedAt();
        java.time.Instant tokenExpiresAt = account.tokenExpiresAt();
    });
}

// 解除绑定（异步操作）
OAuthFrameworkAPI.unlink(player.getUniqueId())
    .thenAccept(v -> player.sendMessage("已解除绑定"))
    .exceptionally(ex -> {
        player.sendMessage("解除绑定失败");
        return null;
    });
```

## LinkedAccount 结构

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `playerId` | `UUID` | Minecraft 玩家 UUID |
| `playerName` | `String` | 玩家名称 |
| `linuxDoId` | `String` | LinuxDO 用户 ID |
| `linuxDoUsername` | `String` | LinuxDO 用户名 |
| `linuxDoDisplayName` | `String` | LinuxDO 显示名称（`@Name` 格式） |
| `trustLevel` | `int` | 信任等级（0-4，-1 未知） |
| `likesReceived` | `int` | 获赞数（社区分数，-1 未知） |
| `rawProfileJson` | `String` | 完整 user-info API 响应 JSON |
| `linkedAt` | `Instant` | 绑定时间 |
| `tokenExpiresAt` | `Instant` | Token 过期时间 |

**派生方法：**

| 方法 | 返回值 | 说明 |
| :--- | :--- | :--- |
| `getTrustLevelLabel()` | `String` | "TL0"~"TL4" 或 "未知" |
| `getLikesReceivedLabel()` | `String` | 获赞数或 "N/A" |
| `getProfileUrl()` | `String` | `https://linux.do/u/{username}/summary` |
| `isTokenExpired(Clock, Duration)` | `boolean` | Token 是否过期（含偏差） |
| `isActive(Clock, Duration)` | `boolean` | 账号是否仍有效 |

## Bukkit ServicesManager（备选）

```java
import org.OAuth_Framework.oAuth_Framework.api.OAuthFrameworkProvider;

OAuthFrameworkProvider provider = Bukkit.getServicesManager()
    .load(OAuthFrameworkProvider.class);
if (provider != null) {
    boolean linked = provider.isLinked(playerId);
    Optional<LinkedAccount> account = provider.getLinkedAccount(playerId);
    provider.unlink(playerId).thenAccept(v -> { /* ... */ });
}
```

## 常见下游场景

### 场景 1：根据信任等级控制权限

```java
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    OAuthFrameworkAPI.getLinkedAccount(player.getUniqueId())
        .ifPresent(account -> {
            // TL3+ 获得特殊权限
            if (account.trustLevel() >= 3) {
                player.addAttachment(plugin, "myplugin.vip", true);
            }
        });
}
```

### 场景 2：解析 rawProfileJson 获取未单独提取的字段

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

OAuthFrameworkAPI.getLinkedAccount(playerId).ifPresent(account -> {
    try {
        JsonNode json = new ObjectMapper().readTree(account.rawProfileJson());
        // 获取 LinuxDO 徽章数量
        int badgeCount = json.has("badge_count")
            ? json.get("badge_count").asInt() : 0;
        // 获取用户组
        String primaryGroup = json.has("primary_group_name")
            ? json.get("primary_group_name").asText() : "";
    } catch (Exception ignored) {
        // rawProfileJson 可能为空或格式不兼容
    }
});
```

### 场景 3：OAuth 到期自动清理

```java
// 定时任务：每天检查一次，清理已过期的绑定
@EventHandler
public void onOAuthUnlink(PlayerOAuthUnlinkEvent event) {
    LinkedAccount account = event.getAccount();
    // 下游插件在此清理关联数据
    database.removePermissions(account.playerId());
    economy.resetBalance(account.playerId());
}
```
