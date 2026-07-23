package jp.co.query.attendance.calendar;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jp.co.query.attendance.common.AuditLogRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompanyCalendarService {

    public record HolidayItem(LocalDate date, String name) {}

    public record ImportResult(long importId, int pageCount, int importedDates, List<HolidayItem> holidays) {}

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MAX_PAGES = 50;
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(20\\d{2})\\s*年");
    private static final Pattern FULL_DATE = Pattern.compile(
            "(?<!\\d)(20\\d{2})\\s*[年/.-]\\s*(\\d{1,2})\\s*[月/.-]\\s*(\\d{1,2})\\s*日?");
    private static final Pattern MONTH_DAY = Pattern.compile(
            "(?<!\\d)(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日");
    private static final Pattern HOLIDAY_LINE = Pattern.compile(
            "休|休日|休暇|創立|年末|年始|祝日|holiday", Pattern.CASE_INSENSITIVE);
    private static final Pattern RANGE_MARKER = Pattern.compile("[～〜~]");

    private final CompanyCalendarRepository calendars;
    private final AuditLogRepository auditLogs;

    public CompanyCalendarService(CompanyCalendarRepository calendars, AuditLogRepository auditLogs) {
        this.calendars = calendars;
        this.auditLogs = auditLogs;
    }

    @Transactional
    public ImportResult importPdf(String actor, MultipartFile file) {
        byte[] bytes = validateAndRead(file);
        int pageCount;
        String text;
        try (var source = new RandomAccessReadBuffer(bytes);
                var document = Loader.loadPDF(source)) {
            pageCount = document.getNumberOfPages();
            if (pageCount < 1 || pageCount > MAX_PAGES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDFは1～50ページにしてください。");
            }
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(document);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDFを読み取れませんでした。", exception);
        }

        Map<LocalDate, String> holidays = parseText(text);
        if (holidays.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "休日名と日付を抽出できませんでした。画像だけのPDF、または休日名のないカレンダーには対応していません。");
        }

        String filename = safeFilename(file.getOriginalFilename());
        long importId = calendars.createImport(
                filename, sha256(bytes), bytes.length, pageCount, holidays.size(), actor);
        calendars.upsertHolidays(importId, holidays);
        auditLogs.record(actor, "COMPANY_CALENDAR_IMPORTED", "COMPANY_CALENDAR_IMPORT",
                Long.toString(importId), "dates=" + holidays.size());
        List<HolidayItem> items = holidays.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new HolidayItem(entry.getKey(), entry.getValue()))
                .toList();
        return new ImportResult(importId, pageCount, items.size(), items);
    }

    static Map<LocalDate, String> parseText(String text) {
        int defaultYear = extractDefaultYear(text);
        Map<LocalDate, String> result = new LinkedHashMap<>();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.strip();
            if (line.isEmpty() || !HOLIDAY_LINE.matcher(line).find()) continue;
            List<LocalDate> dates = extractDates(line, defaultYear);
            if (dates.isEmpty()) continue;
            if (RANGE_MARKER.matcher(line).find() && dates.size() >= 2) {
                LocalDate start = dates.stream().min(LocalDate::compareTo).orElseThrow();
                LocalDate end = dates.stream().max(LocalDate::compareTo).orElseThrow();
                if (!end.isBefore(start) && end.toEpochDay() - start.toEpochDay() <= 31) {
                    dates = start.datesUntil(end.plusDays(1)).toList();
                }
            }
            String name = holidayName(line);
            dates.forEach(date -> result.put(date, name));
        }
        return result;
    }

    private static byte[] validateAndRead(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDFファイルを選択してください。");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "PDFは10MB以下にしてください。");
        }
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        if ((contentType == null || !contentType.equalsIgnoreCase("application/pdf"))
                && (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF形式のファイルだけアップロードできます。");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 5
                    || bytes[0] != '%'
                    || bytes[1] != 'P'
                    || bytes[2] != 'D'
                    || bytes[3] != 'F'
                    || bytes[4] != '-') {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDFのファイル内容が正しくありません。");
            }
            return bytes;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDFを読み取れませんでした。", exception);
        }
    }

    private static int extractDefaultYear(String text) {
        Matcher matcher = YEAR.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : Year.now().getValue();
    }

    private static List<LocalDate> extractDates(String line, int defaultYear) {
        List<LocalDate> dates = new ArrayList<>();
        Matcher full = FULL_DATE.matcher(line);
        String withoutFullDates = line;
        while (full.find()) {
            addDate(dates, full.group(1), full.group(2), full.group(3));
        }
        withoutFullDates = full.replaceAll(" ");
        Matcher monthDay = MONTH_DAY.matcher(withoutFullDates);
        while (monthDay.find()) {
            addDate(dates, Integer.toString(defaultYear), monthDay.group(1), monthDay.group(2));
        }
        return dates.stream().distinct().toList();
    }

    private static void addDate(List<LocalDate> dates, String year, String month, String day) {
        try {
            dates.add(LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day)));
        } catch (java.time.DateTimeException ignored) {
            // PDF内の不正な日付は取込対象外とし、有効な日付だけをプレビューへ返す。
        }
    }

    private static String holidayName(String line) {
        String name = FULL_DATE.matcher(line).replaceAll(" ");
        name = MONTH_DAY.matcher(name).replaceAll(" ");
        name = name.replaceAll("[～〜~、,：:()（）\\[\\]]", " ").replaceAll("\\s+", " ").strip();
        if (name.isEmpty()) return "会社休日";
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    private static String safeFilename(String value) {
        String filename = value == null ? "company-calendar.pdf" : value.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).strip();
        if (filename.isEmpty()) filename = "company-calendar.pdf";
        return filename.length() > 255 ? filename.substring(filename.length() - 255) : filename;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
