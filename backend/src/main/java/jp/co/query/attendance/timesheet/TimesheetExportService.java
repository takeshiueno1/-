package jp.co.query.attendance.timesheet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import jp.co.query.attendance.common.AuditLogRepository;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimesheetExportService {

    public record ExportedWorkbook(String filename, byte[] content) {}

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M/d(E)");
    private final TimesheetService timesheets;
    private final AuditLogRepository auditLogs;

    public TimesheetExportService(TimesheetService timesheets, AuditLogRepository auditLogs) {
        this.timesheets = timesheets;
        this.auditLogs = auditLogs;
    }

    @Transactional
    public ExportedWorkbook export(String username, int year, int month) {
        Timesheet timesheet = timesheets.get(username, year, month);
        byte[] content = createWorkbook(timesheet);
        auditLogs.record(
                username,
                "TIMESHEET_EXPORTED",
                "TIMESHEET",
                Long.toString(timesheet.id()),
                year + "-" + month);
        String filename = "勤務表_" + safeFilename(timesheet.employee().employeeCode())
                + "_" + year + "-" + String.format("%02d", month) + ".xlsx";
        return new ExportedWorkbook(filename, content);
    }

    static byte[] createWorkbook(Timesheet timesheet) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("勤務表");
            CellStyle titleStyle = titleStyle(workbook);
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle borderStyle = borderStyle(workbook);

            Row title = sheet.createRow(0);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue("勤務表");
            titleCell.setCellStyle(titleStyle);
            title.createCell(6).setCellValue(timesheet.year() + "年"
                    + String.format("%02d", timesheet.month()) + "月分");

            writePair(sheet.createRow(2), "所属", timesheet.employee().department(), "氏名", timesheet.employee().displayName());
            writePair(sheet.createRow(3), "役職", timesheet.employee().positionName(), "コード", timesheet.employee().employeeCode());
            writePair(
                    sheet.createRow(4),
                    "Pマーク運用確認日",
                    timesheet.pmarkConfirmationDate() == null ? "" : timesheet.pmarkConfirmationDate().toString(),
                    "WG参加可否",
                    value(timesheet.wgParticipation()));

            String[] headers = {"日付", "始業", "終業", "休憩", "平日", "休日", "休暇種別", "業務内容", "システムNo."};
            Row header = sheet.createRow(6);
            for (int index = 0; index < headers.length; index++) {
                Cell cell = header.createCell(index);
                cell.setCellValue(headers[index]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 7;
            for (DailyEntry entry : timesheet.entries()) {
                Row row = sheet.createRow(rowIndex++);
                String[] values = {
                        entry.workDate().format(DATE_FORMAT),
                        entry.startTime() == null ? "" : entry.startTime().toString(),
                        entry.endTime() == null ? "" : entry.endTime().toString(),
                        formatMinutes(entry.breakMinutes()),
                        formatMinutes(entry.weekdayMinutes()),
                        formatMinutes(entry.holidayMinutes()),
                        value(entry.leaveType()),
                        value(entry.workDetail()),
                        value(entry.systemCode())
                };
                for (int index = 0; index < values.length; index++) {
                    Cell cell = row.createCell(index);
                    cell.setCellValue(values[index]);
                    cell.setCellStyle(borderStyle);
                }
            }

            Row total = sheet.createRow(rowIndex);
            total.createCell(0).setCellValue("計");
            total.createCell(4).setCellValue(formatMinutes(timesheet.totals().weekdayMinutes()));
            total.createCell(5).setCellValue(formatMinutes(timesheet.totals().holidayMinutes()));
            for (int index = 0; index < headers.length; index++) total.getCell(index, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellStyle(headerStyle);

            sheet.setColumnWidth(0, 13 * 256);
            sheet.setColumnWidth(1, 9 * 256);
            sheet.setColumnWidth(2, 9 * 256);
            sheet.setColumnWidth(3, 9 * 256);
            sheet.setColumnWidth(4, 9 * 256);
            sheet.setColumnWidth(5, 9 * 256);
            sheet.setColumnWidth(6, 14 * 256);
            sheet.setColumnWidth(7, 36 * 256);
            sheet.setColumnWidth(8, 18 * 256);
            sheet.createFreezePane(1, 7);
            sheet.setAutobreaks(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.setFitToPage(true);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("勤務表Excelを作成できませんでした。", exception);
        }
    }

    private static void writePair(Row row, String firstLabel, String firstValue, String secondLabel, String secondValue) {
        row.createCell(0).setCellValue(firstLabel);
        row.createCell(1).setCellValue(value(firstValue));
        row.createCell(3).setCellValue(secondLabel);
        row.createCell(4).setCellValue(value(secondValue));
    }

    private static CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        var font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 18);
        style.setFont(font);
        return style;
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = borderStyle(workbook);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        var font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static CellStyle borderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }

    private static String formatMinutes(Integer minutes) {
        if (minutes == null) return "";
        int absolute = Math.abs(minutes);
        return (minutes < 0 ? "-" : "") + absolute / 60 + ":" + String.format("%02d", absolute % 60);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String safeFilename(String value) {
        return value(value).replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
