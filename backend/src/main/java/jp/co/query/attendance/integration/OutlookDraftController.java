package jp.co.query.attendance.integration;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class OutlookDraftController {

    private final OutlookDraftService drafts;

    public OutlookDraftController(OutlookDraftService drafts) {
        this.drafts = drafts;
    }

    @PostMapping("/timesheets/{year}/{month}/outlook-draft")
    public OutlookDraftService.DraftToken create(
            Principal principal,
            @PathVariable int year,
            @PathVariable int month) {
        return drafts.create(principal.getName(), year, month);
    }

    @GetMapping("/outlook-drafts/{token}.eml")
    public ResponseEntity<byte[]> download(@PathVariable String token) {
        OutlookDraftService.DraftMail mail = drafts.consume(token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("message/rfc822"))
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(mail.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentLength(mail.content().length)
                .body(mail.content());
    }
}
