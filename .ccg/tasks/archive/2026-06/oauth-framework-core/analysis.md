# Phase 2 Analysis: Dual-Model Synthesis

## Codex (Backend Architecture Analysis)
**Session**: `019e81aa-4163-7141-b96f-1095e010c735`

### Module Structure (Recommended)
```
org.oauthframework/
├── OAuthFrameworkPlugin          // JavaPlugin lifecycle
├── config/
│   ├── OAuthPluginConfig
│   ├── OAuthMode (AUTO/MANUAL/BOTH)
│   └── ConfigReloader
├── oauth/
│   ├── OAuthService              // orchestration
│   ├── OAuthSessionStore         // ConcurrentHashMap<String, OAuthSession>
│   ├── OAuthSession
│   ├── OAuthUrlBuilder
│   ├── TokenClient
│   ├── UserInfoClient
│   └── OAuthFailureReason
├── oauth/dto/
│   ├── TokenResponse
│   ├── LinuxDoUserProfile
│   └── LinuxDoExternalId
├── callback/
│   ├── CallbackServer            // interface
│   ├── JdkHttpCallbackServer     // com.sun.net.httpserver
│   └── CallbackRequest
├── storage/
│   ├── OAuthUserRepository
│   ├── YamlOAuthUserRepository
│   ├── LinkedUserRecord
│   └── AsyncYamlWriter
├── api/
│   ├── OAuthFrameworkAPI         // static facade
│   ├── OAuthFrameworkService     // service interface
│   └── OAuthUserInfo
├── event/
│   ├── PlayerOAuthSuccessEvent
│   └── PlayerOAuthFailEvent
├── command/
│   ├── LoginCommand
│   ├── LinkCommand
│   ├── UnlinkCommand
│   └── OAuthAdminCommand
└── scheduler/
    ├── BukkitMainThreadExecutor
    └── PluginExecutors
```

### Technology Choices
| Component | Choice | Rationale |
|-----------|--------|-----------|
| HTTP Client | `java.net.http.HttpClient` (Java 17) | No dependency, async, built-in |
| Callback Server | `com.sun.net.httpserver.HttpServer` | No dependency, sufficient for GET callback |
| JSON Parser | Jackson (shaded/relocated) | Robust DTO mapping, ignore unknown fields |
| YAML | Bukkit `YamlConfiguration` (snapshot copies) | Built-in, avoid shared mutable objects |

### Thread Safety (3 Execution Zones)
- **Main thread**: commands, messages, events, plugin lifecycle
- **OAuth executor**: token exchange, userinfo fetch (async)
- **Persistence executor**: serialized async YAML writes (temp-file-then-replace)

### Security
- High-entropy one-time `state` per `/login`, bound to UUID + expiry
- Reject missing/expired/reused/unknown state
- Never persist access/refresh tokens
- Redact sensitive data from logs and events
- Rate limiting on callback
- HTTPS via reverse proxy for production

### OAuth Data Flow (11 steps)
1. onEnable → load config, data.yml, register, start callback server
2. `/login` → create OAuthSession
3. Build authorization URL with state
4. Player opens browser → LinuxDO authorization
5. Callback → validate state
6. Token exchange (async)
7. Userinfo fetch (async)
8. Map to DTO
9. Main thread → fire events, message player
10. Persist to data.yml

---

## Gemini (Developer/Admin/Player Experience Analysis)
**Session**: `b0325499-a8f6-4642-875d-9228fab2914f`

### API Design
```java
public interface OAuthFrameworkAPI {
    boolean isLinked(UUID playerUuid);
    Optional<LinuxDoUser> getUser(UUID playerUuid);
    CompletableFuture<Boolean> refreshUser(UUID playerUuid);
}
```
- ServicesManager (primary) + static convenience class
- `LinuxDoUser` POJO: curated fields + `getRawData()` fallback
- Events: `PlayerOAuthSuccessEvent`, `PlayerOAuthUnlinkEvent`, `PlayerOAuthLoadEvent`

### Config Design
```yaml
oauth:
  client-id: "your_id_here"
  client-secret: "your_secret_here"
  redirect-uri: "http://localhost:8080/callback"
auth-mode: AUTO      # AUTO | MANUAL | BOTH
http-server:
  enabled: true
  port: 8080
  host: "0.0.0.0"
requirements:
  min-trust-level: 0
  must-be-active: true
```

### Player UX
- Clickable JSON chat messages with hover text
- Title messages for success/failure
- Color scheme: `§b` (Aqua) primary, `§7` (Gray) secondary
- `/login` primary command, `/oauth status` for status check

---

## Synthesis: Converged Design

### Points of Agreement
- **HTTP Client**: `java.net.http.HttpClient` ✓ (both recommend)
- **Callback Server**: `com.sun.net.httpserver` ✓ (both recommend)
- **API Pattern**: static facade + service interface via ServicesManager ✓
- **Events**: async for data, sync for UI
- **Persistence**: YAML with async writes

### Points to Resolve
| Issue | Codex | Gemini | Decision |
|-------|-------|--------|----------|
| Package naming | `org.oauthframework` (rename) | (not addressed) | Keep current `org.OAuth_Framework.oAuth_Framework` for now |
| Event count | 2 events | 3 events (+LoadEvent) | Keep 2 core events (success/fail), add LoadEvent later |
| Config structure | (not detailed) | 4-section config | Adopt Gemini's config structure with Codex's security defaults |

### Recommended Implementation Order
- **Layer 1 (Foundation)**: config, DTOs, scheduler/executors, data model
- **Layer 2 (OAuth Core)**: OAuthService, TokenClient, UserInfoClient, OAuthSessionStore
- **Layer 3 (Callback)**: CallbackServer + JdkHttpCallbackServer
- **Layer 4 (Storage)**: YamlOAuthUserRepository, AsyncYamlWriter
- **Layer 5 (API + Events)**: OAuthFrameworkAPI, events
- **Layer 6 (Commands)**: LoginCommand, LinkCommand, UnlinkCommand, OAuthAdminCommand
- **Layer 7 (Integration)**: OAuthFrameworkPlugin composition + plugin.yml commands
