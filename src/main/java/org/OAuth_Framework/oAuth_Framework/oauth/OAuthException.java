package org.OAuth_Framework.oAuth_Framework.oauth;

/**
 * Typed internal exception that carries a safe message for players.
 * Never includes sensitive data (tokens, secrets) in the message.
 */
public class OAuthException extends RuntimeException {

    private final OAuthError error;
    private final String safeMessage;

    public OAuthException(OAuthError error, String safeMessage) {
        super(safeMessage);
        this.error = error;
        this.safeMessage = safeMessage;
    }

    public OAuthException(OAuthError error) {
        this(error, error.defaultMessage());
    }

    public OAuthException(OAuthError error, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.error = error;
        this.safeMessage = safeMessage;
    }

    public OAuthError getError() {
        return error;
    }

    public String getSafeMessage() {
        return safeMessage;
    }
}
