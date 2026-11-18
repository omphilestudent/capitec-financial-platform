package za.co.capitec.statementservice.statement.storage;

import java.io.IOException;

public interface StatementStorage {

    String store(String storageKey, byte[] content) throws IOException;

    byte[] read(String storageKey) throws IOException;

    boolean exists(String storageKey);
}
