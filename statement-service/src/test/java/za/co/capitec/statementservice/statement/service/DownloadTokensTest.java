package za.co.capitec.statementservice.statement.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DownloadTokensTest {

    private final DownloadTokens tokens = new DownloadTokens();

    @Test
    void rawTokensAreUrlSafeHighEntropyAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String raw = tokens.newRawToken();
            assertEquals(43, raw.length());
            assertTrue(raw.matches("[A-Za-z0-9_-]+"));
            assertTrue(seen.add(raw));
        }
    }

    @Test
    void hashIsDeterministicSha256HexAndDoesNotEqualTheToken() {
        String raw = tokens.newRawToken();
        String hash = tokens.hash(raw);

        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertEquals(hash, tokens.hash(raw));
        assertNotEquals(raw, hash);
        assertNotEquals(hash, tokens.hash(raw + "x"));
    }
}
