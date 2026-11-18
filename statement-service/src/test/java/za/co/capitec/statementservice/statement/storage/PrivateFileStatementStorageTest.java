package za.co.capitec.statementservice.statement.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PrivateFileStatementStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAndReadsBackTheSameBytes() throws Exception {
        PrivateFileStatementStorage storage = new PrivateFileStatementStorage(tempDir.toString());
        byte[] pdf = "%PDF-test".getBytes();

        String key = storage.store("statements/2026/10/abc.pdf", pdf);

        assertEquals("statements/2026/10/abc.pdf", key);
        assertTrue(storage.exists(key));
        assertArrayEquals(pdf, storage.read(key));
        assertTrue(java.nio.file.Files.isRegularFile(tempDir.resolve(key)));
    }

    @Test
    void rejectsKeysThatEscapeTheStorageRoot() throws Exception {
        PrivateFileStatementStorage storage = new PrivateFileStatementStorage(tempDir.toString());

        assertThrows(IllegalArgumentException.class, () -> storage.store("../escape.pdf", new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> storage.read("../../etc/passwd"));
        assertFalse(storage.exists("../escape.pdf"));
    }

    @Test
    void missingFileIsReportedAsAbsent() throws Exception {
        PrivateFileStatementStorage storage = new PrivateFileStatementStorage(tempDir.toString());
        assertFalse(storage.exists("statements/none.pdf"));
    }
}
