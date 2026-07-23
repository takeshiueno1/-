package jp.co.query.attendance.calendar;

import java.security.Principal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/company-calendar")
public class CompanyCalendarController {

    private final CompanyCalendarService service;

    public CompanyCalendarController(CompanyCalendarService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompanyCalendarService.ImportResult upload(
            Principal principal,
            @RequestPart("file") MultipartFile file) {
        return service.importPdf(principal.getName(), file);
    }
}
