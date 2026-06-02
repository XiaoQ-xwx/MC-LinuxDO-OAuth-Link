package org.linuxdo.oauthlink.model;

import org.linuxdo.oauthlink.oauth.OAuthError;
import org.linuxdo.oauthlink.oauth.OAuthException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LinuxDoProfile record.
 */
class LinuxDoProfileTest {

    @Test
    void constructor_ValidProfile_ShouldCreateSuccessfully() {
        LinuxDoProfile profile = new LinuxDoProfile("user-123", "testuser", "Test User");

        assertEquals("user-123", profile.id());
        assertEquals("testuser", profile.username());
        assertEquals("Test User", profile.displayName());
    }

    @Test
    void constructor_NullId_ShouldThrowOAuthException() {
        OAuthException ex = assertThrows(OAuthException.class,
                () -> new LinuxDoProfile(null, "testuser", "Test User"));

        assertEquals(OAuthError.PROFILE_INVALID, ex.getError());
        assertEquals("用户 ID 缺失", ex.getSafeMessage());
    }

    @Test
    void constructor_BlankId_ShouldThrowOAuthException() {
        OAuthException ex = assertThrows(OAuthException.class,
                () -> new LinuxDoProfile("   ", "testuser", "Test User"));

        assertEquals(OAuthError.PROFILE_INVALID, ex.getError());
    }

    @Test
    void constructor_EmptyId_ShouldThrowOAuthException() {
        OAuthException ex = assertThrows(OAuthException.class,
                () -> new LinuxDoProfile("", "testuser", "Test User"));

        assertEquals(OAuthError.PROFILE_INVALID, ex.getError());
    }

    @Test
    void constructor_NullUsername_ShouldDefaultToEmptyString() {
        LinuxDoProfile profile = new LinuxDoProfile("user-123", null, "Test User");

        assertEquals("", profile.username());
    }

    @Test
    void constructor_NullUsername_NullDisplayName_ShouldBothBeEmpty() {
        LinuxDoProfile profile = new LinuxDoProfile("user-123", null, null);

        assertEquals("", profile.username());
        assertEquals("", profile.displayName());
    }

    @Test
    void constructor_NullDisplayName_ShouldDefaultToUsername() {
        LinuxDoProfile profile = new LinuxDoProfile("user-123", "testuser", null);

        assertEquals("testuser", profile.displayName());
    }

    @Test
    void constructor_NullDisplayName_WithNullUsername_ShouldDefaultToEmpty() {
        // displayName defaults to username (which is ""), so displayName is ""
        LinuxDoProfile profile = new LinuxDoProfile("user-123", null, null);

        assertEquals("", profile.username());
        assertEquals("", profile.displayName());
    }

    @Test
    void constructor_EmptyUsername_NullDisplayName_ShouldDefaultToEmptyUsername() {
        LinuxDoProfile profile = new LinuxDoProfile("user-123", "", null);

        assertEquals("", profile.username());
        assertEquals("", profile.displayName());
    }
}
