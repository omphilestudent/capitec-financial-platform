package za.co.capitec.statementservice.statement.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import za.co.capitec.statementservice.security.CurrentUser;
import za.co.capitec.statementservice.statement.service.DownloadService;

@RestController
public class DownloadController {

    private final DownloadService downloads;

    public DownloadController(DownloadService downloads) {
        this.downloads = downloads;
    }

    @GetMapping("/api/v1/statements/download/{token}")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable String token) {
        DownloadService.Download download = downloads.download(CurrentUser.from(jwt), token);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(download.filename()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store, private")
                .header("X-Content-Type-Options", "nosniff")
                .body(download.content());
    }
}
