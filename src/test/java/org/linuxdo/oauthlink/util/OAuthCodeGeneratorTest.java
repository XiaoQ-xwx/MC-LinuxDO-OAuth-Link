package org.linuxdo.oauthlink.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OAuthCodeGenerator.
 */
class OAuthCodeGeneratorTest {

    private OAuthCodeGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new OAuthCodeGenerator();
    }

    @Test
    void generateState_ShouldReturn32CharUrlSafeString() {
        String state = generator.generateState();

        assertNotNull(state);
        assertEquals(32, state.length(), "State should be 32 characters (24 bytes base64)");

        // URL-safe base64: only A-Z, a-z, 0-9, -, _
        assertTrue(state.matches("^[A-Za-z0-9\\-_]+$"),
                "State should only contain URL-safe base64 characters");
    }

    @Test
    void generateState_ShouldNotContainPadding() {
        String state = generator.generateState();

        assertFalse(state.contains("="),
                "State should not contain base64 padding characters");
    }

    @Test
    void generateState_ShouldProduceUniqueValues() {
        Set<String> states = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            assertTrue(states.add(generator.generateState()),
                    "Each generated state should be unique");
        }
    }

    @Test
    void generateLinkCode_ShouldReturn8CharString() {
        String code = generator.generateLinkCode();

        assertNotNull(code);
        assertEquals(8, code.length(), "Link code should be 8 characters");
    }

    @Test
    void generateLinkCode_ShouldUseOnlyValidCharacters() {
        String validChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        for (int i = 0; i < 50; i++) {
            String code = generator.generateLinkCode();
            for (char c : code.toCharArray()) {
                assertTrue(validChars.indexOf(c) >= 0,
                        "Link code should only contain allowed characters, got: " + c);
            }
        }
    }

    @Test
    void generateLinkCode_ShouldNotContainAmbiguousCharacters() {
        // Characters that should never appear: 0, O, I, 1
        String ambiguousChars = "0OI1";

        for (int i = 0; i < 50; i++) {
            String code = generator.generateLinkCode();
            for (char c : ambiguousChars.toCharArray()) {
                assertFalse(code.contains(String.valueOf(c)),
                        "Link code should not contain ambiguous character: " + c);
            }
        }
    }

    @Test
    void generateLinkCode_ShouldProduceUniqueValues() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            assertTrue(codes.add(generator.generateLinkCode()),
                    "Each generated link code should be unique within 100 attempts");
        }
    }
}
