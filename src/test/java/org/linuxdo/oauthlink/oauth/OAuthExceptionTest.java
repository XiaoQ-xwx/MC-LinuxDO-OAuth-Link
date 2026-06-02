package org.linuxdo.oauthlink.oauth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OAuthException.
 */
class OAuthExceptionTest {

    @Test
    void constructor_WithErrorOnly_ShouldUseDefaultMessage() {
        OAuthException ex = new OAuthException(OAuthError.CONFIG_INVALID);

        assertEquals(OAuthError.CONFIG_INVALID, ex.getError());
        assertEquals(OAuthError.CONFIG_INVALID.defaultMessage(), ex.getSafeMessage());
        assertEquals(OAuthError.CONFIG_INVALID.defaultMessage(), ex.getMessage());
    }

    @Test
    void constructor_WithCustomMessage_ShouldUseCustomMessage() {
        String customMessage = "自定义错误信息";
        OAuthException ex = new OAuthException(OAuthError.NETWORK_FAILED, customMessage);

        assertEquals(OAuthError.NETWORK_FAILED, ex.getError());
        assertEquals(customMessage, ex.getSafeMessage());
        assertEquals(customMessage, ex.getMessage());
    }

    @Test
    void constructor_WithCause_ShouldStoreCause() {
        Throwable cause = new RuntimeException("底层错误");
        OAuthException ex = new OAuthException(OAuthError.STORAGE_FAILED, "存储失败", cause);

        assertEquals(OAuthError.STORAGE_FAILED, ex.getError());
        assertEquals("存储失败", ex.getSafeMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void exception_ShouldBeRuntimeException() {
        OAuthException ex = new OAuthException(OAuthError.TOKEN_EXCHANGE_FAILED);
        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    void getError_ShouldReturnCorrectErrorType() {
        for (OAuthError error : OAuthError.values()) {
            OAuthException ex = new OAuthException(error);
            assertEquals(error, ex.getError());
        }
    }

    @Test
    void safeMessage_ShouldNeverBeNull() {
        for (OAuthError error : OAuthError.values()) {
            OAuthException ex = new OAuthException(error);
            assertNotNull(ex.getSafeMessage());
        }
    }
}
