package za.co.capitec.statementservice.statement.web.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DownloadLinkResponse(UUID statementId, String token, LocalDateTime expiresAt, String downloadPath) {
}
