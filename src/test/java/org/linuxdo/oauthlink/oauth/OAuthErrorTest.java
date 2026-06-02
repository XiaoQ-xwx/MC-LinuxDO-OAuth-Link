package org.linuxdo.oauthlink.oauth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OAuthError enum.
 */
class OAuthErrorTest {

    @Test
    void everyError_ShouldHaveNonNullDefaultMessage() {
        for (OAuthError error : OAuthError.values()) {
            assertNotNull(error.defaultMessage(),
                    "OAuthError." + error.name() + " should have a non-null default message");
            assertFalse(error.defaultMessage().isBlank(),
                    "OAuthError." + error.name() + " should have a non-blank default message");
        }
    }

    @Test
    void everyError_ShouldHaveUniqueDefaultMessage() {
        long distinctMessages = java.util.Arrays.stream(OAuthError.values())
                .map(OAuthError::defaultMessage)
                .distinct()
                .count();
        assertEquals(OAuthError.values().length, distinctMessages,
                "Each OAuthError should have a unique default message");
    }

    @Test
    void configInvalid_ShouldHaveExpectedMessage() {
        assertEquals("插件配置无效，请联系管理员", OAuthError.CONFIG_INVALID.defaultMessage());
    }

    @Test
    void expiredState_ShouldHaveExpectedMessage() {
        assertEquals("OAuth 会话已过期，请重新登录", OAuthError.EXPIRED_STATE.defaultMessage());
    }

    @Test
    void expiredLinkCode_ShouldHaveExpectedMessage() {
        assertEquals("验证码已过期，请重新获取", OAuthError.EXPIRED_LINK_CODE.defaultMessage());
    }

    @Test
    void alreadyLinked_ShouldHaveExpectedMessage() {
        assertEquals("该 LinuxDO 账号已绑定其他玩家", OAuthError.ALREADY_LINKED.defaultMessage());
    }
}
