package jp.co.query.attendance.employee;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.security.Principal;
import java.time.LocalTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/employees")
public class AdminEmployeeController {

    public record CreateRequest(
            @NotBlank @Size(max = 50) String username,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotBlank @Pattern(regexp = "USER|MANAGER|ADMIN") String accessRole,
            @Size(max = 100) String department,
            @NotBlank @Size(max = 100) String displayName,
            @Size(max = 100) String positionName,
            @NotBlank @Size(max = 50) String employeeCode,
            @Size(max = 50) String workScheduleType,
            @NotNull LocalTime standardStart,
            @NotNull LocalTime standardEnd,
            @Min(0) @Max(1440) int standardBreakMinutes,
            @Size(max = 50) String defaultSystemCode) {}

    public record EnabledRequest(boolean enabled) {}

    public record PasswordRequest(@NotBlank @Size(min = 12, max = 128) String password) {}

    private final AdminEmployeeService service;

    public AdminEmployeeController(AdminEmployeeService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminEmployeeService.EmployeeAccount> list(
            @RequestParam(name = "query", defaultValue = "") @Size(max = 100) String query,
            @RequestParam(name = "limit", defaultValue = "100") @Min(1) @Max(100) int limit) {
        return service.search(query, limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminEmployeeService.EmployeeAccount create(Principal principal, @Valid @RequestBody CreateRequest request) {
        return service.create(principal.getName(), new AdminEmployeeService.CreateCommand(
                request.username(), request.password(), request.accessRole(), request.department(), request.displayName(),
                request.positionName(), request.employeeCode(), request.workScheduleType(),
                request.standardStart(), request.standardEnd(), request.standardBreakMinutes(),
                request.defaultSystemCode()));
    }

    @PutMapping("/{username}/enabled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enabled(Principal principal, @PathVariable String username, @RequestBody EnabledRequest request) {
        service.changeEnabled(principal.getName(), username, request.enabled());
    }

    @PutMapping("/{username}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(Principal principal, @PathVariable String username, @Valid @RequestBody PasswordRequest request) {
        service.resetPassword(principal.getName(), username, request.password());
    }
}
