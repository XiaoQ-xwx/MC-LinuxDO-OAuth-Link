# Requirements: LinuxDO OAuth Framework for Minecraft

## 1. 原始需求

> 制作一个 Minecraft 服务端插件，实现 LinuxDO OAuth 登录。作为框架/前置插件，
> 向其他登录插件提供 OAuth 用户信息。

## 2. 技术上下文（已验证）

### 项目骨架
- **平台**：Spigot/Paper 1.20.1
- **语言**：Java 17
- **构建**：Gradle Kotlin DSL
- **包名**：`org.OAuth_Framework.oAuth_Framework`
- **状态**：仅空壳 `JavaPlugin`，无任何实现

### LinuxDO OAuth2 API（已验证）
| 端点 | URL |
|------|-----|
| 授权端点 | `https://connect.linux.do/oauth2/authorize` |
| Token 端点 | `https://connect.linux.do/oauth2/token` |
| 用户信息端点 | `https://connect.linux.do/api/user` |

OAuth2 Authorization Code Grant 标准流程：
1. 客户端重定向用户到授权端点 → 用户授权
2. 回调接收 `code`
3. 用 `code` 换取 `access_token` + `refresh_token`
4. 用 `access_token` 获取用户信息

### 用户资料字段
`id`, `sub`, `username`, `login`, `name`, `email`, `avatar_url`, `active`, `trust_level`, `silenced`, `external_ids`, `api_key`

## 3. 需求决策（已确认）

### 3.1 OAuth 交互流程 → 方案 C：混合模式
- **模式 1（HTTP 回调）**：插件启动内置 HTTP 服务器监听回调端口
  - 玩家 `/login` → 插件生成 OAuth URL → 玩家浏览器授权 → 回调到内置服务器 → 自动关联
- **模式 2（手动令牌）**：玩家手动输入令牌
  - 玩家网页获取 code → 游戏内 `/link <code>` → 插件服务器端验证
- 管理员通过 `config.yml` 选择模式
- 默认：模式 1（HTTP 回调）

### 3.2 API 暴露方式 → 方案 C：事件 + API 混合
- **事件**：`PlayerOAuthSuccessEvent` / `PlayerOAuthFailEvent` — 下游插件监听
- **静态 API**：`OAuthFrameworkAPI` 类提供查询方法
  - `getUserInfo(Player)` / `isAuthenticated(Player)` / `getUserInfo(UUID)`
- 下游插件通过 `getPlugin("OAuth_Framework")` 检查框架是否存在

### 3.3 数据持久化 → YAML 文件
- 存储路径：`plugins/OAuth_Framework/data.yml`
- 数据结构：`UUID → { linuxdo_user_id, username, email, trust_level, avatar_url, linked_at, last_login }`
- 启动时加载到内存缓存，运行时内存操作 + 异步写入文件

### 3.4 Token 策略 → 过期重新授权
- 不实现 refresh_token 自动续期（简化实现）
- access_token 过期 → 提示玩家重新 `/login`
- 仅在校验/获取用户信息时使用 token，不长期持有

## 4. 确认的需求规格

### 功能需求
| ID | 功能 | 优先级 |
|----|------|--------|
| F1 | 内置 HTTP 服务器（OAuth 回调接收） | P0 |
| F2 | OAuth2 Authorization Code 流程实现 | P0 |
| F3 | 用户信息获取与解析 | P0 |
| F4 | `/login` 命令（触发 OAuth 流程） | P0 |
| F5 | `/link <code>` 命令（手动令牌模式） | P0 |
| F6 | `config.yml` 配置管理 | P0 |
| F7 | `PlayerOAuthSuccessEvent` / `PlayerOAuthFailEvent` 事件发布 | P0 |
| F8 | `OAuthFrameworkAPI` 静态查询类 | P0 |
| F9 | YAML 数据持久化（异步写入） | P1 |
| F10 | `/unlink` 命令（解除绑定） | P1 |
| F11 | `/oauth status` 命令（查看状态） | P2 |

### 非功能需求
- 内置 HTTP 服务器应轻量（单线程即可，仅处理 OAuth 回调）
- 异步 HTTP 请求不阻塞 Minecraft 主线程
- YAML 写入异步执行，避免卡服
- 配置文件支持热重载 (`/oauth reload`)

## 5. 完整性评分（更新）

| 维度 | 得分 | 说明 |
|------|------|------|
| 目标明确性 | 3/3 | ✅ 核心目标 + 交互模式 + API 形式全部明确 |
| 预期结果 | 3/3 | ✅ 事件 + API 混合，YAML 持久化，流程清晰 |
| 边界范围 | 2/2 | ✅ 框架职责：OAuth 全流程 + 用户信息提供，不含下游业务逻辑 |
| 约束条件 | 2/2 | ✅ 存储方式、Token 策略、线程安全要求已定义 |
| **总分** | **10/10** | ✅ ≥ 7，可进入 Phase 2 |
