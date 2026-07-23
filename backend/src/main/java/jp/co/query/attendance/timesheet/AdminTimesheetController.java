package jp.co.query.attendance.timesheet;

import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
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
@RequestMapping("/api/admin/employees/{username}/timesheets")
public class AdminTimesheetController {

    private final TimesheetService service;
    private final TimesheetDeletionService deletionService;

    public AdminTimesheetController(
            TimesheetService service,
            TimesheetDeletionService deletionService) {
        this.service = service;
        this.deletionService = deletionService;
    }

    @GetMapping("/history")
    public List<TimesheetService.HistoryItem> history(
            Principal principal, @PathVariable String username) {
        return service.historyAsAdmin(principal.getName(), username);
    }

    @GetMapping("/{year}/{month}")
    public Timesheet get(
            Principal principal,
            @PathVariable String username,
            @PathVariable int year,
            @PathVariable int month) {
        return service.getAsAdmin(principal.getName(), username, year, month);
    }

    @PostMapping("/{year}/{month}/initialize")
    public Timesheet initialize(
            Principal principal,
            @PathVariable String username,
            @PathVariable int year,
            @PathVariable int month,
            @Valid @RequestBody TimesheetController.InitializeRequest request) {
        return service.initializeAsAdmin(
                principal.getName(),
                username,
                year,
                month,
                new TimesheetService.InitializeCommand(
                        request.standardStart(), request.standardEnd(), request.standardBreakMinutes(),
                        request.defaultSystemCode(), request.overwrite()));
    }

    @PutMapping("/{year}/{month}/entries/{date}")
    public Timesheet updateEntry(
            Principal principal,
            @PathVariable String username,
            @PathVariable int year,
            @PathVariable int month,
            @PathVariable LocalDate date,
            @Valid @RequestBody TimesheetController.UpdateEntryRequest request) {
        return service.updateEntryAsAdmin(
                principal.getName(),
                username,
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
            @PathVariable String username,
            @PathVariable int year,
            @PathVariable int month,
            @Valid @RequestBody TimesheetController.WgRequest request) {
        return service.updateWgParticipationAsAdmin(
                principal.getName(), username, year, month, request.value());
    }

    @DeleteMapping("/{year}/{month}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            Principal principal,
            @PathVariable String username,
            @PathVariable int year,
            @PathVariable int month,
            @Valid @RequestBody TimesheetController.DeleteRequest request) {
        deletionService.deleteAsAdmin(
                principal.getName(),
                username,
                year,
                month,
                request.password(),
                request.confirmation());
    }
}
