package jp.co.query.attendance.importer;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import jp.co.query.attendance.common.AuditLogRepository;
import jp.co.query.attendance.common.GeneratedKeyJdbc;
import jp.co.query.attendance.employee.Employee;
import jp.co.query.attendance.employee.EmployeeRepository;
import jp.co.query.attendance.timesheet.Timesheet;
import jp.co.query.attendance.timesheet.TimesheetRepository;
import jp.co.query.attendance.timesheet.TimesheetService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExcelTimesheetImportService {

    public record ImportResult(
            long importId,
            String targetUsername,
            String sourceEmployeeName,
            String sourceEmployeeCode,
            int year,
            int month,
            int importedRows,
            List<String> warnings,
            Timesheet timesheet) {}

    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final ExcelTimesheetParser parser;
    private final EmployeeRepository employees;
    private final TimesheetService timesheetService;
    private final TimesheetRepository timesheets;
    private final GeneratedKeyJdbc generatedKeys;
    private final AuditLogRepository auditLogs;

    public ExcelTimesheetImportService(
            ExcelTimesheetParser parser,
            EmployeeRepository employees,
            TimesheetService timesheetService,
            TimesheetRepository timesheets,
            GeneratedKeyJdbc generatedKeys,
            AuditLogRepository auditLogs) {
        this.parser = parser;
        this.employees = employees;
        this.timesheetService = timesheetService;
        this.timesheets = timesheets;
        this.generatedKeys = generatedKeys;
        this.auditLogs = auditLogs;
    }

    @Transactional
    public ImportResult importWorkbook(
            String actor,
            String targetUsername,
            MultipartFile file,
            String password,
            boolean overwrite) {
        byte[] bytes = validateAndRead(file, password);
        ExcelTimesheetParser.ParsedTimesheet parsed = parser.parse(bytes, password);
        Employee target = employees.findByUsername(targetUsername)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "取込先の社員が見つかりません。"));
        String filename = safeFilename(file.getOriginalFilename());
        long importId = generatedKeys.insert("""
                        INSERT INTO excel_timesheet_imports (
                            employee_id, source_filename, source_sha256, source_bytes,
                            source_employee_name, source_employee_code, target_year, target_month,
                            imported_rows, imported_by)
                        VALUES (
                            ?, ?, ?, ?,
                            ?, ?, ?, ?, ?, ?)
                        """,
                List.of(
                        target.id(),
                        filename,
                        sha256(bytes),
                        bytes.length,
                        parsed.sourceEmployeeName(),
                        parsed.sourceEmployeeCode(),
                        parsed.year(),
                        parsed.month(),
                        parsed.entries().size(),
                        actor));

        Timesheet imported = timesheetService.initializeAsAdmin(
                actor,
                targetUsername,
                parsed.year(),
                parsed.month(),
                new TimesheetService.InitializeCommand(
                        parsed.standardStart(),
                        parsed.standardEnd(),
                        parsed.standardBreakMinutes(),
                        parsed.defaultSystemCode(),
                        overwrite));
        for (ExcelTimesheetParser.ImportedEntry entry : parsed.entries()) {
            imported = timesheetService.updateEntryAsAdmin(
                    actor,
                    targetUsername,
                    parsed.year(),
                    parsed.month(),
                    entry.workDate(),
                    new TimesheetService.UpdateEntryCommand(
                            entry.startTime(),
                            entry.endTime(),
                            entry.breakMinutes(),
                            entry.leaveType(),
                            entry.workDetail(),
                            entry.systemCode()));
        }
        if (parsed.wgParticipation() != null) {
            imported = timesheetService.updateWgParticipationAsAdmin(
                    actor, targetUsername, parsed.year(), parsed.month(), parsed.wgParticipation());
        }
        timesheets.setSourceImport(imported.id(), importId);
        auditLogs.record(actor, "EXCEL_TIMESHEET_IMPORTED", "TIMESHEET",
                Long.toString(imported.id()), "importId=" + importId + ",rows=" + parsed.entries().size());

        List<String> warnings = metadataWarnings(target, parsed);
        return new ImportResult(
                importId, targetUsername, parsed.sourceEmployeeName(), parsed.sourceEmployeeCode(),
                parsed.year(), parsed.month(), parsed.entries().size(), warnings,
                timesheetService.getAsAdmin(actor, targetUsername, parsed.year(), parsed.month()));
    }

    private static byte[] validateAndRead(MultipartFile file, String password) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excelファイルを選択してください。");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Excelは10MB以下にしてください。");
        }
        if (password != null && password.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excelパスワードが長すぎます。");
        }
        String filename = safeFilename(file.getOriginalFilename()).toLowerCase(java.util.Locale.ROOT);
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xlsm")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ".xlsx または .xlsm を選択してください。");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excelを読み取れませんでした。", exception);
        }
    }

    private static List<String> metadataWarnings(
            Employee target, ExcelTimesheetParser.ParsedTimesheet parsed) {
        java.util.ArrayList<String> warnings = new java.util.ArrayList<>();
        if (!parsed.sourceEmployeeName().isBlank()
                && !normalizeName(parsed.sourceEmployeeName()).equals(normalizeName(target.displayName()))) {
            warnings.add("Excelの氏名「" + parsed.sourceEmployeeName()
                    + "」と取込先「" + target.displayName() + "」が異なります。");
        }
        if (!parsed.sourceEmployeeCode().isBlank()
                && !parsed.sourceEmployeeCode().equalsIgnoreCase(target.employeeCode())) {
            warnings.add("Excelのコード「" + parsed.sourceEmployeeCode()
                    + "」と取込先コード「" + target.employeeCode() + "」が異なります。");
        }
        return List.copyOf(warnings);
    }

    private static String normalizeName(String value) {
        return value.replace(" ", "").replace("　", "").strip();
    }

    private static String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "timesheet.xlsx";
        String safe = Path.of(filename).getFileName().toString();
        return safe.length() <= 255 ? safe : safe.substring(safe.length() - 255);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256を使用できません。", exception);
        }
    }
}
