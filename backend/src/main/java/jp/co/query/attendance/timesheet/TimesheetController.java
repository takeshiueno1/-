package jp.co.query.attendance.timesheet;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/timesheets")
public class TimesheetController {

    public record InitializeRequest(
            LocalTime standardStart,
            LocalTime standardEnd,
            @Min(0) @Max(1440) int standardBreakMinutes,
            @Size(max = 50) String defaultSystemCode,
            boolean overwrite) {}

    public record UpdateEntryRequest(
            LocalTime startTime,
            LocalTime endTime,
            @Min(0) @Max(1440) Integer breakMinutes,
            @Size(max = 50) String leaveType,
            @Size(max = 500) String workDetail,
            @Size(max = 50) String systemCode) {}

    public record WgRequest(@Size(max = 20) String value) {}

    public record DeleteRequest(
            @NotBlank @Size(max = 128) String password,
            @NotBlank @Size(max = 20) String confirmation) {}

    private final TimesheetService service;
    private final TimesheetDeletionService deletionService;

    public TimesheetController(
            TimesheetService service,
            TimesheetDeletionService deletionService) {
        this.service = service;
        this.deletionService = deletionService;
    }

    @GetMapping("/history")
    public List<TimesheetService.HistoryItem> history(Principal principal) {
        return service.history(principal.getName());
    }

    @GetMapping("/{year}/{month}")
    public Timesheet get(Principal principal, @PathVariable int year, @PathVariable int month) {
        return service.get(principal.getName(), year, month);
    }

    @PostMapping("/{year}/{month}/initialize")
    public Timesheet initialize(
            Principal principal,
            @PathVariable int year,
            @PathVariable int month,
            @Valid @RequestBody InitializeRequest request) {
        return service.initialize(
                principal.getName(),
                year,
                month,
                new TimesheetService.InitializeCommand(
                        request.standardStart(), request.standardEnd(), request.standardBreakMinutes(),
                        request.defaultSystemCode(), request.overwrite()));
    }

    @PutMapping("/{year}/{month}/entries/{date}")
    public Timesheet updateEntry(
            Principal principal,
            @PathVariable int year,
            @PathVariable int month,
            @PathVariable LocalDate date,
            @Valid @RequestBody UpdateEntryRequest request) {
        return service.updateEntry(
                principal.getName(),
                year,
                month,
                date,
                new TimesheetService.UpdateEntryCommand(
                        request.startTime(), request.endTime(), request.breakMinutes(), request.leaveType(),
                        request.workDetail(), request.systemCode()));
    }

    @PutMapping("/{year}/{month}/wg-participation")
    public Timesheet updateWgParticipation(
            Principal principal,
            @PathVariable int year,
            @PathVariable int month,
            @Valid @RequestBody WgRequest request) {
        return service.updateWgParticipation(principal.getName(), year, month, request.value());
    }

    @DeleteMapping("/{year}/{month}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            Principal principal,
            @PathVariable int year,
            @PathVariable int month,
            @Valid @RequestBody DeleteRequest request) {
        deletionService.deleteOwn(
                principal.getName(),
                year,
                month,
                request.password(),
                request.confirmation());
    }
}
