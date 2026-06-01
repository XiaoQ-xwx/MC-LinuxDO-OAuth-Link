# Implementation Plan: LinuxDO OAuth Framework

## Architecture Overview

```
┌─────────────────────────────────────────────┐
│           Downstream Plugins                 │
│  (Login plugins, Rank sync, Whitelist)       │
├─────────────────────────────────────────────┤
│  Events (Success/Fail)  │  OAuthFrameworkAPI │
├─────────────────────────────────────────────┤
│           OAuthFrameworkService              │
├──────────┬──────────┬──────────┬────────────┤
│ OAuth    │ Callback │ Storage  │ Commands   │
│ Client   │ Server   │ (YAML)   │            │
├──────────┴──────────┴──────────┴────────────┤
│  Config  │  Model/DTO  │  Executors          │
└─────────────────────────────────────────────┘
```

## Layered Implementation (5 Layers, ~18 files)

### Layer 1 — Foundation (config, model, util)
| # | File | Purpose |
|---|------|---------|
| 1 | `src/main/resources/config.yml` | Default configuration |
| 2 | `config/OAuthConfig.java` | Immutable validated config POJO |
| 3 | `model/LinkedAccount.java` | Record: UUID → LinuxDO identity |
| 4 | `model/LinuxDoProfile.java` | LinuxDO API response DTO |
| 5 | `model/OAuthTokens.java` | Short-lived in-memory token (never persisted) |
| 6 | `model/PendingAuthorization.java` | Callback → manual code bridge |
| 7 | `oauth/OAuthError.java` | Failure taxonomy enum |
| 8 | `oauth/OAuthException.java` | Typed internal exception |
| 9 | `util/OAuthCodeGenerator.java` | SecureRandom state/code generation |

### Layer 2 — Storage & State
| # | File | Purpose |
|---|------|---------|
| 10 | `storage/LinkRepository.java` | Interface: load/save/delete/findByPlayer |
| 11 | `storage/YamlLinkRepository.java` | YAML impl with atomic temp-file writes |
| 12 | `oauth/PendingOAuthRegistry.java` | ConcurrentHashMap state ↔ UUID mapping |

### Layer 3 — OAuth & HTTP
| # | File | Purpose |
|---|------|---------|
| 13 | `oauth/LinuxDoOAuthClient.java` | HttpClient: auth URL, token exchange, userinfo |
| 14 | `http/CallbackHttpServer.java` | com.sun.net.httpserver lifecycle |
| 15 | `http/OAuthCallbackHandler.java` | Validate state → exchange → store → HTML response |

### Layer 4 — Public API & Events
| # | File | Purpose |
|---|------|---------|
| 16 | `api/OAuthFrameworkAPI.java` | Static facade + ServicesManager registration |
| 17 | `event/PlayerOAuthSuccessEvent.java` | Bukkit event (fired on main thread after async auth) |
| 18 | `event/PlayerOAuthFailEvent.java` | Bukkit event with OAuthError + safe message |

### Layer 5 — Commands & Plugin Composition
| # | File | Purpose |
|---|------|---------|
| 19 | `command/LinkCommand.java` | `/link` and `/link <code>` |
| 20 | `command/OAuthFrameworkCommand.java` | `/oauthfw reload` admin command |
| 21 | `OAuth_Framework.java` | onEnable/onDisable composition root |

## Build Changes

### build.gradle.kts
```kotlin
plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.4.1"  // ADD
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.3")  // ADD
}

tasks {
    shadowJar {                                 // ADD
        relocate("com.fasterxml.jackson", "org.OAuth_Framework.oAuth_Framework.libs.jackson")
        archiveClassifier.set("")
    }
    build { dependsOn(shadowJar) }              // ADD
}
```

### plugin.yml — Commands & Permissions
```yaml
commands:
  link:
    description: Start or complete LinuxDO OAuth account linking
    usage: /<command> [code]
    permission: oauth_framework.command.link
  oauthframework:
    description: OAuth Framework administration
    usage: /<command> reload
    aliases: [oauthfw]
    permission: oauth_framework.admin

permissions:
  oauth_framework.command.link:
    description: Link Minecraft account with LinuxDO OAuth
    default: true
  oauth_framework.admin:
    description: Admin commands
    default: op
```

### config.yml
```yaml
oauth:
  client-id: ""
  client-secret: ""
  authorization-url: "https://connect.linux.do/oauth2/authorize"
  token-url: "https://connect.linux.do/oauth2/token"
  user-info-url: "https://connect.linux.do/api/user"
  redirect-uri: "http://127.0.0.1:8181/oauth/callback"

callback:
  host: "127.0.0.1"
  port: 8181
  path: "/oauth/callback"

security:
  state-ttl-seconds: 300
  link-code-ttl-seconds: 300
  token-expiry-skew-seconds: 60

storage:
  file: "data.yml"
```

## OAuthFrameworkAPI Design

```java
// ServicesManager interface (primary API)
public interface OAuthFrameworkProvider {
    boolean isLinked(@NotNull UUID playerId);
    @NotNull Optional<LinkedAccount> getLinkedAccount(@NotNull UUID playerId);
    @NotNull CompletableFuture<Void> unlink(@NotNull UUID playerId);
}

// Static convenience facade
public final class OAuthFrameworkAPI {
    public static boolean isLinked(@NotNull UUID playerId);
    public static @NotNull Optional<LinkedAccount> getLinkedAccount(@NotNull UUID playerId);
    public static @NotNull CompletableFuture<Void> unlink(@NotNull UUID playerId);
}
```

## Data Model

```java
// Immutable, persisted, exposed via API
public record LinkedAccount(
    UUID playerId,
    String playerName,
    String linuxDoId,       // LinuxDO user ID (String for safety)
    String linuxDoUsername, // @username
    Instant linkedAt,
    Instant tokenExpiresAt  // When token expires → re-auth needed
) {}

// Internal DTO, never persisted
public record LinuxDoProfile(
    String id,
    String username,
    String displayName
) {}
```

## Events

```java
// Fired on main thread after async auth completes
public class PlayerOAuthSuccessEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final LinkedAccount account;
    // LinkedAccount getAccount() — no tokens exposed
}

public class PlayerOAuthFailEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final OAuthError error;
    private final String safeMessage; // player-friendly, no secrets
}
```

## Thread Safety Architecture

| Zone | Thread | What runs here |
|------|--------|---------------|
| Bukkit Main | Server thread | Commands, player objects, events, API registration |
| OAuth HTTP | `HttpClient` async | Token exchange, userinfo fetch |
| Callback HTTP | HttpServer executor | Parse query, validate state, browser response |
| Storage IO | Single-thread executor | YAML load/save, atomic temp-file writes |

**Handoff**: Complete async work → `Bukkit.getScheduler().runTask(plugin, () -> fireEvent(...))` → main thread.

## OAuth Data Flow

```
Player types /link
  → Main thread: generate state, store in PendingOAuthRegistry
  → Send clickable auth URL in chat
  → Player opens browser, authorizes on LinuxDO
  → Callback: validate state, exchange code (async)
  → Store PendingAuthorization in registry
  → Player types /link <code>
  → Main thread: validate code from registry
  → Fire PlayerOAuthSuccessEvent / send success message
  → Async write to data.yml
```

## Error Handling

| Failure | Behavior |
|---------|----------|
| Invalid/missing config | Log, disable plugin before registering anything |
| Callback port busy | Log error, plugin stays enabled (manual mode still works) |
| Expired/missing state | HTTP 400, safe message to player |
| Token exchange fails | HTTP 502, log sanitized, fire FailEvent |
| Invalid link code | Player message "Invalid or expired code", fire FailEvent |
| Duplicate LinuxDO ID | Fail with ALREADY_LINKED error message |
| Storage save fails | Fire FailEvent with STORAGE_FAILED, keep in memory |

## Testing Strategy

- **Unit tests**: OAuthCodeGenerator, OAuthConfig validation, LinkedAccount expiry logic, PendingOAuthRegistry
- **Integration tests**: YamlLinkRepository load/save cycle, LinuxDoOAuthClient with mock HTTP server
- **Manual tests**: Full OAuth flow with real LinuxDO credentials, port conflict handling, plugin reload
