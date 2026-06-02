[根目录](../../../../../../../CLAUDE.md) > [oAuth_Framework](../) > **http**

# http -- 内建 HTTP 回调服务器

## 模块职责

- `CallbackHttpServer` -- 嵌入式 JDK HttpServer 生命周期管理
- `OAuthCallbackHandler` -- OAuth 回调请求处理（自动绑定 + 手动降级）

## 入口与启动

- `CallbackHttpServer.start()` 在 `OAuthFrameworkService.onEnable()` 中调用
- 绑定地址和端口由 `OAuthConfig` 提供（默认 `127.0.0.1:2790`）
- 使用 2 线程池（daemon）处理 HTTP 请求
- `CallbackHttpServer.stop()` 在 `onDisable()` 中调用，2 秒优雅关闭

## 对外接口

### CallbackHttpServer

| 方法 | 说明 |
| :--- | :--- |
| `start()` | 启动 HTTP 服务器，端口冲突时抛 `OAuthException(HTTP_SERVER_FAILED)` |
| `stop()` | 优雅关闭（2s 超时），关闭线程池 |
| `isRunning()` | 返回服务器运行状态 |

### OAuthCallbackHandler（实现 `HttpHandler`）

处理流程：
1. 解析 `code` 和 `state` 参数
2. `registry.consumeState(state)` 验证 CSRF state，获取玩家身份
3. `oauthClient.exchangeCode(code)` 换取 token
4. `oauthClient.fetchProfile(token)` 获取用户信息
5. 尝试自动绑定 `bindingCallback.bind(playerId, playerName, profile, tokens)`
6. 自动绑定成功 -> 返回 HTML 成功页面
7. 自动绑定失败 -> 降级为手动模式，生成验证码并返回 HTML 验证码页面

## 关键依赖与配置

- 使用 `com.sun.net.httpserver.HttpServer`（JDK 内置，非标准 API，可能在未来版本受限）
- 依赖 `PendingOAuthRegistry`、`LinuxDoOAuthClient`
- 通过 `OAuthBindingCallback` 函数式接口回调 `OAuthFrameworkService.onAutoBind()`
- HTML 页面内联 CSS，无外部资源依赖

## 数据模型

不直接操作数据模型，通过回调函数委托给 `OAuthFrameworkService`。

## 测试与质量

- **测试状态：** 无独立测试
- **建议：**
  1. `CallbackHttpServer` -- 测试启动/停止/端口冲突
  2. `OAuthCallbackHandler` -- 使用 WireMock 模拟 LinuxDO API，测试自动绑定成功/失败/state 过期路径

## 常见问题 (FAQ)

**Q: 为什么不用 Netty/Jetty 等成熟 HTTP 框架？**
A: 项目哲学是零外部 HTTP 依赖。`com.sun.net.httpserver` 是 JDK 内置，对于低频 OAuth 回调（每分钟几次）完全够用。

**Q: 端口被占用了怎么办？**
A: 抛出 `OAuthException(HTTP_SERVER_FAILED)`，插件继续以手动模式运行（`/linkld <code>` 仍可用）。修改 `config.yml` 中 `callback.port` 后 `/oauthfw reload`。

## 相关文件清单

| 文件 | 职责 |
| :--- | :--- |
| `CallbackHttpServer.java` | HTTP 服务器生命周期（77 行） |
| `OAuthCallbackHandler.java` | 回调处理逻辑（182 行，含 HTML 模板） |

## 变更记录 (Changelog)

| 日期 | 变更 |
| :--- | :--- |
| 2026-06-01 | 初始化模块文档；记录自动绑定 + 手动降级双路径 |
