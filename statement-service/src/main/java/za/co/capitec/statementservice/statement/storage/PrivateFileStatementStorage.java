package za.co.capitec.statementservice.statement.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class PrivateFileStatementStorage implements StatementStorage {

    private final Path root;

    public PrivateFileStatementStorage(@Value("${statement.storage.dir}") String directory) throws IOException {
        this.root = Path.of(directory).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    Path root() {
        return root;
    }

    @Override
    public String store(String storageKey, byte[] content) throws IOException {
        Path target = resolve(storageKey);
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "stmt-", ".tmp");
        try {
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
        return storageKey;
    }

    @Override
    public byte[] read(String storageKey) throws IOException {
        return Files.readAllBytes(resolve(storageKey));
    }

    @Override
    public boolean exists(String storageKey) {
        try {
            return Files.isRegularFile(resolve(storageKey));
        } catch (IllegalArgumentException invalidKey) {
            return false;
        }
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey is required");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("storageKey escapes the statement storage root");
        }
        return resolved;
    }
}
