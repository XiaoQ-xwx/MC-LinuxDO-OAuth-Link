package org.linuxdo.oauthlink.oauth;

/**
 * Stable failure taxonomy for OAuth events and logging.
 */
public enum OAuthError {
    CONFIG_INVALID("插件配置无效，请联系管理员"),
    HTTP_SERVER_FAILED("内建 HTTP 服务器启动失败，请检查端口配置"),
    INVALID_STATE("OAuth 状态验证失败，请重新操作"),
    EXPIRED_STATE("OAuth 会话已过期，请重新登录"),
    INVALID_LINK_CODE("无效的验证码"),
    EXPIRED_LINK_CODE("验证码已过期，请重新获取"),
    TOKEN_EXCHANGE_FAILED("Token 交换失败，请稍后重试"),
    USERINFO_FAILED("获取用户信息失败，请稍后重试"),
    PROFILE_INVALID("返回的用户信息无效"),
    ALREADY_LINKED("该 LinuxDO 账号已绑定其他玩家"),
    TOKEN_EXPIRED("授权已过期，请重新登录"),
    STORAGE_FAILED("数据存储失败，请稍后重试"),
    NETWORK_FAILED("网络请求失败，请检查网络连接");

    private final String defaultMessage;

    OAuthError(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    /**
     * Returns a player-safe Chinese message for this error.
     */
    public String defaultMessage() {
        return defaultMessage;
    }
}
