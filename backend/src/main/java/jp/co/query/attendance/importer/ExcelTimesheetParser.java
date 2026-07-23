package jp.co.query.attendance.importer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ExcelTimesheetParser {

    public record ImportedEntry(
            LocalDate workDate,
            LocalTime startTime,
            LocalTime endTime,
            Integer breakMinutes,
            String leaveType,
            String workDetail,
            String systemCode) {}

    public record ParsedTimesheet(
            int year,
            int month,
            String sourceEmployeeName,
            String sourceEmployeeCode,
            LocalTime standardStart,
            LocalTime standardEnd,
            int standardBreakMinutes,
            String defaultSystemCode,
            String wgParticipation,
            List<ImportedEntry> entries) {}

    private static final Set<String> ALLOWED_LEAVE_TYPES = Set.of(
            "AM半休", "PM半休", "有給休暇", "特別休暇", "慶弔休暇", "振替休日", "欠勤");
    private static final DataFormatter FORMATTER = new DataFormatter(java.util.Locale.JAPAN);

    public ParsedTimesheet parse(byte[] bytes, String password) {
        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(bytes), normalizePassword(password))) {
            Sheet sheet = workbook.getSheet("勤務表");
            if (sheet == null) {
                throw badRequest("「勤務表」シートが見つかりません。");
            }
            String sourceName = text(sheet, "D5");
            String sourceCode = text(sheet, "L5");
            LocalTime standardStart = timeFromParts(sheet, "AH4", "AI4");
            LocalTime standardEnd = timeFromParts(sheet, "AK4", "AL4");
            if (standardStart == null || standardEnd == null) {
                throw badRequest("標準勤務時間を読み取れませんでした。");
            }
            String defaultSystemCode = text(sheet, "AH5");
            String wg = blankToNull(text(sheet, "W5"));
            if (wg != null && !List.of("参加", "不参加", "当月未開催").contains(wg)) {
                wg = null;
            }

            List<ImportedEntry> entries = new ArrayList<>();
            for (int rowNumber = 9; rowNumber <= 39; rowNumber++) {
                Row row = sheet.getRow(rowNumber - 1);
                LocalDate date = date(row == null ? null : row.getCell(1));
                if (date == null) continue;
                LocalTime start = timeFromParts(row, 3, 4);
                LocalTime end = timeFromParts(row, 5, 6);
                Integer breakMinutes = minutesFromParts(row, 7, 8);
                String leaveType = blankToNull(text(row, 13));
                if (leaveType != null && !ALLOWED_LEAVE_TYPES.contains(leaveType)) {
                    throw badRequest(date + " の休暇種別「" + leaveType + "」には対応していません。");
                }
                entries.add(new ImportedEntry(
                        date, start, end, breakMinutes, leaveType,
                        text(row, 16), text(row, 25)));
            }
            if (entries.isEmpty()) {
                throw badRequest("勤務日を読み取れませんでした。");
            }
            YearMonth target = YearMonth.from(entries.getFirst().workDate());
            if (entries.size() != target.lengthOfMonth()
                    || entries.stream().anyMatch(entry -> !YearMonth.from(entry.workDate()).equals(target))) {
                throw badRequest("1か月分の日付を正しく読み取れませんでした。");
            }
            int defaultBreak = entries.stream()
                    .map(ImportedEntry::breakMinutes)
                    .filter(java.util.Objects::nonNull)
                    .filter(value -> value > 0)
                    .findFirst()
                    .orElse(60);
            return new ParsedTimesheet(
                    target.getYear(), target.getMonthValue(), sourceName, sourceCode,
                    standardStart, standardEnd, defaultBreak, defaultSystemCode, wg,
                    List.copyOf(entries));
        } catch (EncryptedDocumentException exception) {
            throw badRequest("Excelのパスワードが正しくありません。");
        } catch (IOException | IllegalArgumentException exception) {
            throw badRequest("Excelを読み取れませんでした。ファイル形式とパスワードを確認してください。");
        }
    }

    private static LocalDate date(Cell cell) {
        if (cell == null) return null;
        CellType type = valueType(cell);
        if (type == CellType.NUMERIC) {
            if (!DateUtil.isValidExcelDate(cell.getNumericCellValue())) return null;
            return DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate();
        }
        String value = FORMATTER.formatCellValue(cell).strip();
        if (value.isEmpty()) return null;
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("yyyy/M/d"))) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // 次の書式を試す。
            }
        }
        return null;
    }

    private static LocalTime timeFromParts(Sheet sheet, String hourCell, String minuteCell) {
        CellReference hour = new CellReference(hourCell);
        CellReference minute = new CellReference(minuteCell);
        return timeFromParts(sheet.getRow(hour.getRow()), hour.getCol(), minute.getCol());
    }

    private static LocalTime timeFromParts(Row row, int hourIndex, int minuteIndex) {
        Integer hour = integer(row == null ? null : row.getCell(hourIndex));
        Integer minute = integer(row == null ? null : row.getCell(minuteIndex));
        if (hour == null && minute == null) return null;
        if (hour == null || minute == null || hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw badRequest("勤務時刻の形式が不正です。");
        }
        return LocalTime.of(hour, minute);
    }

    private static Integer minutesFromParts(Row row, int hourIndex, int minuteIndex) {
        Integer hour = integer(row == null ? null : row.getCell(hourIndex));
        Integer minute = integer(row == null ? null : row.getCell(minuteIndex));
        if (hour == null && minute == null) return null;
        if (hour == null || minute == null || hour < 0 || minute < 0 || minute > 59) {
            throw badRequest("休憩時間の形式が不正です。");
        }
        int result = hour * 60 + minute;
        if (result > 1440) throw badRequest("休憩時間が24時間を超えています。");
        return result;
    }

    private static Integer integer(Cell cell) {
        if (cell == null) return null;
        CellType type = valueType(cell);
        if (type == CellType.BLANK) return null;
        if (type == CellType.NUMERIC) return (int) Math.round(cell.getNumericCellValue());
        String value = FORMATTER.formatCellValue(cell).strip();
        if (value.isEmpty()) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw badRequest("数値セルを読み取れませんでした。");
        }
    }

    private static String text(Sheet sheet, String reference) {
        CellReference cellReference = new CellReference(reference);
        Row row = sheet.getRow(cellReference.getRow());
        Cell cell = row == null ? null : row.getCell(cellReference.getCol());
        return text(cell);
    }

    private static String text(Row row, int column) {
        return row == null ? "" : text(row.getCell(column));
    }

    private static String text(Cell cell) {
        return cell == null ? "" : FORMATTER.formatCellValue(cell).strip();
    }

    private static CellType valueType(Cell cell) {
        return cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();
    }

    private static String normalizePassword(String password) {
        return password == null || password.isEmpty() ? null : password;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
