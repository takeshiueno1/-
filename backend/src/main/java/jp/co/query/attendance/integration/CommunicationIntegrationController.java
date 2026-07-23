package jp.co.query.attendance.integration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations")
public class CommunicationIntegrationController {

    public record OutlookRequest(
            @NotBlank @Email @Size(max = 254) String recipient,
            @Min(2000) @Max(2100) int year,
            @Min(1) @Max(12) int month) {}

    public record SlackRequest(
            @Size(max = 500) String message,
            @Min(2000) @Max(2100) int year,
            @Min(1) @Max(12) int month) {}

    private final CommunicationIntegrationService service;

    public CommunicationIntegrationController(CommunicationIntegrationService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public CommunicationIntegrationService.Status status() {
        return service.status();
    }

    @PostMapping("/outlook")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendOutlook(
            Principal principal,
            @Valid @RequestBody OutlookRequest request) {
        service.sendOutlook(principal.getName(), request.recipient(), request.year(), request.month());
    }

    @PostMapping("/slack")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendSlack(
            Principal principal,
            @Valid @RequestBody SlackRequest request) {
        service.sendSlack(principal.getName(), request.message(), request.year(), request.month());
    }
}
