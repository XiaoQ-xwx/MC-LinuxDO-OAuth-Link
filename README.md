# LinuxDO OAuth Framework

[![Java 17](https://img.shields.io/badge/Java-17-blue)](https://adoptium.net/)
[![Spigot 1.20](https://img.shields.io/badge/Spigot-1.20.x-orange)](https://www.spigotmc.org/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

Minecraft (Spigot/Paper) 插件 — 将 [LinuxDO](https://linux.do/) 社区 OAuth 认证引入你的服务器。玩家通过浏览器完成 LinuxDO 授权，在游戏内使用 `/linkld` 命令绑定账号，下游插件可通过 Bukkit Event 或静态 API 查询绑定状态。

## ✨ 功能

- **OAuth2 授权码流程** — 标准 authorization_code 流程，安全绑定 LinuxDO 账号
- **零依赖 Web 服务** — 内建 HTTP 回调服务器（`com.sun.net.httpserver`），无需额外 Web 服务
- **自动绑定 + 手动降级** — 浏览器授权成功后自动完成绑定，验证码手动输入作为备用路径
- **账号信息面板** — `/linkld` 查看已绑定账号的信任等级、社区分数、论坛主页
- **Bukkit 事件系统** — 绑定成功/失败/解绑分别触发事件，下游插件可监听
- **静态 API 门面** — `OAuthFrameworkAPI` 一行调用查询绑定状态，无需注入依赖
- **安全的 Token 管理** — Access Token 永不通过公共 API 暴露，仅插件内部使用
- **YAML 持久化** — 使用 Minecraft 原生 `YamlConfiguration`，人类可读、易于排查

## 🚀 快速开始

### 1. 注册 OAuth 应用

前往 [LinuxDO Connect](https://connect.linux.do/) 注册 OAuth 应用，获取 `client-id` 和 `client-secret`。

> **回调地址需与 `config.yml` 中配置的 `redirect-uri` 一致。** 根据服务器部署场景选择对应的地址：
>
> | 部署场景 | `redirect-uri` 示例 | `callback.host` | 说明 |
> | :--- | :--- | :--- | :--- |
> | **本地开发** | `http://127.0.0.1:2790/oauth/callback` | `127.0.0.1` | 服务端和浏览器在同一台机器（默认） |
> | **局域网** | `http://192.168.1.100:2790/oauth/callback` | `0.0.0.0` | 服务端在局域网内，玩家通过局域网 IP 访问 |
> | **公网 (IP)** | `http://1.2.3.4:2790/oauth/callback` | `0.0.0.0` | 服务端有公网 IP，玩家通过互联网访问 |
> | **公网 (域名)** | `https://mc.example.com/oauth/callback` | `0.0.0.0` | 服务端绑定域名（推荐 HTTPS + 反代） |
>
> 修改 `redirect-uri` 时需同步更新 `callback.host` 和 `callback.port`，并确保与 LinuxDO Connect 后台注册的回调地址**完全一致**。

### 2. 安装插件

将 `OAuth_Framework.jar` 放入服务器的 `plugins/` 目录，启动服务器。

### 3. 配置

编辑 `plugins/OAuth_Framework/config.yml`，填入注册应用时获得的凭据：

```yaml
oauth:
  client-id: "你的-client-id"
  client-secret: "你的-client-secret"
```

然后使用 `/oauthfw reload` 重载配置。

### 4. 玩家绑定

玩家进入游戏后使用 `/linkld` 命令，会收到一条可点击的聊天消息。点击后在浏览器中完成 LinuxDO 授权：

- **自动模式**：授权成功后自动完成绑定，游戏内即时收到确认消息
- **手动模式**（降级备用）：如回调服务器不可用，授权页面会显示验证码，使用 `/linkld <验证码>` 完成绑定

## 📋 命令

| 命令 | 权限 | 说明 |
| :--- | :--- | :--- |
| `/linkld` | `oauth_framework.command.link`（默认所有人） | 发起 LinuxDO 账号绑定，已绑定则显示账号信息面板 |
| `/linkld <验证码>` | 同上 | 输入授权回调页面显示的验证码完成绑定 |
| `/linkld unlink` | 同上 | 显示解除绑定确认提示 |
| `/linkld unlink confirm` | 同上 | 执行解除绑定 |
| `/oauthfw reload` | `oauth_framework.admin`（默认 OP） | 重新加载配置文件 |
| `/oauthframework` | 同上 | `oauthfw` 的全名别名 |

> `/link` 仍作为 `/linkld` 的别名可用，避免命令冲突。

## 🔧 配置参考

```yaml
# 注册 OAuth 应用: https://connect.linux.do/
oauth:
  client-id: ""                                           # 必填，LinuxDO 应用 ID
  client-secret: ""                                       # 必填，LinuxDO 应用密钥
  authorization-url: "https://connect.linux.do/oauth2/authorize"
  token-url: "https://connect.linux.do/oauth2/token"
  user-info-url: "https://connect.linux.do/api/user"
  redirect-uri: "http://127.0.0.1:2790/oauth/callback"    # 需与注册时一致

callback:
  host: "127.0.0.1"                                       # 回调监听地址
  port: 2790                                              # 回调监听端口
  path: "/oauth/callback"                                 # 回调路径

security:
  state-ttl-seconds: 300                                  # OAuth state 有效期（秒）
  link-code-ttl-seconds: 300                              # 验证码有效期（秒）
  token-expiry-skew-seconds: 60                           # Token 过期容忍偏差（秒）

storage:
  file: "data.yml"                                        # 绑定数据存储文件名
```

### 安全注意事项

- **`client-secret` 是敏感信息**，不要在公开场合分享你的配置文件
- **回调端口** 建议使用防火墙限制，仅允许 `127.0.0.1` 访问
- **验证码 TTL** 不宜过大（推荐 300 秒），降低验证码被猜测的风险
- **Token 过期偏差** (`token-expiry-skew-seconds`) 用于容忍服务器时钟不同步，建议保持默认 60 秒

## 📦 下游插件开发

> 完整 API 文档请查看 → **[docs/API.md](docs/API.md)**（事件监听、静态 API、LinkedAccount 结构、ServicesManager、常见下游场景）

### 快速上手

**Gradle 依赖：**

```kotlin
dependencies {
    compileOnly(files("libs/OAuth_Framework.jar"))
}
```

**一行检查绑定：**

```java
import org.OAuth_Framework.oAuth_Framework.api.OAuthFrameworkAPI;

if (OAuthFrameworkAPI.isLinked(player.getUniqueId())) {
    // 玩家已绑定 LinuxDO 账号
}
```

**监听绑定事件：**

```java
import org.OAuth_Framework.oAuth_Framework.event.PlayerOAuthSuccessEvent;
import org.OAuth_Framework.oAuth_Framework.event.PlayerOAuthUnlinkEvent;
import org.OAuth_Framework.oAuth_Framework.model.LinkedAccount;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MyListener implements Listener {

    @EventHandler
    public void onOAuthSuccess(PlayerOAuthSuccessEvent event) {
        LinkedAccount account = event.getAccount();
        // 根据信任等级发放权限/称号
        if (account.trustLevel() >= 3) {
            // TL3+ 获得 VIP 权限
        }
    }

    @EventHandler
    public void onOAuthUnlink(PlayerOAuthUnlinkEvent event) {
        // 清理该玩家的权限、称号等关联数据
    }
}
```

详细用法请查看 **[docs/API.md](docs/API.md)**。

## 🛠️ 构建

**环境要求：** JDK 17+

```bash
# Windows
gradlew.bat build

# Linux / macOS
./gradlew build
```

产物：`build/libs/OAuth_Framework-1.0-SNAPSHOT.jar`（shadowJar，含 relocated Jackson）

### 运行测试

```bash
./gradlew test
```

测试覆盖：配置校验、数据模型、状态注册表、存储层、OAuth HTTP 客户端、业务编排层。

## 📐 架构

```text
┌─────────────────────────────────────────────────────────┐
│                   Minecraft Server                       │
│  ┌──────────┐  ┌──────────┐  ┌───────────────────────┐ │
│  │ /linkld  │  │ /oauthfw │  │  Downstream Plugins   │ │
│  │ Command  │  │ Command  │  │  (Events / API)       │ │
│  └────┬─────┘  └────┬─────┘  └──────────┬────────────┘ │
│       │             │                   │               │
│       ▼             ▼                   ▼               │
│  ┌────────────────────────────────────────────────────┐  │
│  │           OAuthFrameworkService                    │  │
│  │  (Business Orchestrator + OAuthFrameworkProvider)  │  │
│  └──┬───────┬────────┬────────┬────────┬─────────────┘  │
│     │       │        │        │        │                 │
│     ▼       ▼        ▼        ▼        ▼                 │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────────────┐  │
│  │Config│ │OAuth │ │Repo  │ │Event │ │Callback Svr  │  │
│  │      │ │Client│ │(YAML)│ │Bus   │ │(Embedded)    │  │
│  └──────┘ └──┬───┘ └──────┘ └──────┘ └──────┬───────┘  │
│              │                               │           │
└──────────────┼───────────────────────────────┼───────────┘
               │                               │
               ▼                               ▲
         ┌──────────┐              OAuth2 Callback
         │ LinuxDO  │ (authorization_code flow)
         │ Connect  │
         └──────────┘
```

## 📂 模块结构

| 包 | 职责 | 关键类 |
| :--- | :--- | :--- |
| `oAuth_Framework` | 插件入口，生命周期管理 | `OAuth_Framework.java` |
| `api` | 公共 API（静态门面 + 服务接口） | `OAuthFrameworkAPI`, `OAuthFrameworkProvider` |
| `service` | 核心业务编排 | `OAuthFrameworkService` |
| `oauth` | OAuth2 客户端、状态注册表、错误体系 | `LinuxDoOAuthClient`, `PendingOAuthRegistry`, `OAuthError` |
| `http` | 内建 HTTP 回调服务器 | `CallbackHttpServer`, `OAuthCallbackHandler` |
| `storage` | 账号绑定持久化（接口 + YAML 实现） | `LinkRepository`, `YamlLinkRepository` |
| `command` | 游戏内命令 | `LinkCommand`, `OAuthFrameworkCommand` |
| `event` | Bukkit 事件 | `PlayerOAuthSuccessEvent`, `PlayerOAuthFailEvent`, `PlayerOAuthUnlinkEvent` |
| `config` | 配置加载与校验 | `OAuthConfig` |
| `model` | 数据记录（DTO） | `LinkedAccount`, `LinuxDoProfile`, `OAuthTokens`, `PendingAuthorization` |
| `util` | 安全随机码生成 | `OAuthCodeGenerator` |

## 🔄 绑定生命周期

```text
玩家执行 /linkld
  │
  ├─ 已绑定且 Token 有效
  │   └─ 显示账号信息面板（信任等级、社区分数、论坛链接、退出按钮）
  │
  └─ 未绑定 / Token 已过期
      └─ 发起 OAuth 流程
            │
            ▼
      浏览器授权 (LinuxDO)
            │
      ┌─────┴─────┐
      ▼           ▼
   自动绑定    手动验证码
   (HTTP 回调)  (/linkld <code>)
      │           │
      └─────┬─────┘
            ▼
    PlayerOAuthSuccessEvent
            │
            ▼
    LinkedAccount 持久化 (data.yml)
            │
            ├─ Token 未过期 → isLinked() = true
            │
            └─ Token 过期 → isLinked() = false
                  │
                  └─ 玩家需重新 /linkld
```

## 📚 参考文档

| 资源 | 类型 | 说明 |
| :--- | :--- | :--- |
| [passport-linuxdo](https://github.com/ryanzen9/passport-linuxdo) | Node.js 参考实现 | LinuxDO OAuth 的 Passport.js 策略 |
| [LinuxDO OAuth API 讨论](https://linux.do/t/topic/2055779) | 社区论坛 | OAuth 接口说明与讨论 |

## 🤝

- **XiaoQ** — [LinuxDO 主页](https://linux.do/u/xiao_q/summary)

## 📄 许可证

本项目基于 MIT 许可证开源。详见 [LICENSE](LICENSE) 文件。
