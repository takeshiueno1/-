package jp.co.query.attendance.importer;

import java.security.Principal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ExcelTimesheetImportController {

    private final ExcelTimesheetImportService service;

    public ExcelTimesheetImportController(ExcelTimesheetImportService service) {
        this.service = service;
    }

    @PostMapping(value = "/admin/excel-timesheets/{username}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExcelTimesheetImportService.ImportResult importAsAdmin(
            Principal principal,
            @PathVariable String username,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "password", required = false) String password,
            @RequestParam(name = "overwrite", defaultValue = "false") boolean overwrite) {
        return service.importWorkbook(principal.getName(), username, file, password, overwrite);
    }

    @PostMapping(value = "/excel-timesheets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExcelTimesheetImportService.ImportResult importOwnWorkbook(
            Principal principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "password", required = false) String password,
            @RequestParam(name = "overwrite", defaultValue = "false") boolean overwrite) {
        return service.importWorkbook(
                principal.getName(), principal.getName(), file, password, overwrite);
    }
}
