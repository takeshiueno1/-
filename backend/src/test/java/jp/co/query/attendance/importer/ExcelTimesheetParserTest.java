package jp.co.query.attendance.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelTimesheetParserTest {

    private final ExcelTimesheetParser parser = new ExcelTimesheetParser();

    @Test
    void parsesQueryTimesheetLayout() throws Exception {
        byte[] workbookBytes;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("勤務表");
            sheet.createRow(3).createCell(3).setCellValue("検証部門");
            sheet.createRow(4).createCell(3).setCellValue("検証社員");
            sheet.getRow(4).createCell(11).setCellValue("DEMO001");
            sheet.getRow(3).createCell(33).setCellValue(9);
            sheet.getRow(3).createCell(34).setCellValue(30);
            sheet.getRow(3).createCell(36).setCellValue(18);
            sheet.getRow(3).createCell(37).setCellValue(30);
            sheet.getRow(4).createCell(22).setCellValue("参加");
            sheet.getRow(4).createCell(33).setCellValue("PJ1");

            LocalDate start = LocalDate.of(2026, 6, 1);
            for (int index = 0; index < 30; index++) {
                var row = sheet.createRow(8 + index);
                row.createCell(1).setCellValue(start.plusDays(index));
                if (start.plusDays(index).getDayOfWeek().getValue() < 6) {
                    row.createCell(3).setCellValue(9);
                    row.createCell(4).setCellValue(30);
                    row.createCell(5).setCellValue(18);
                    row.createCell(6).setCellValue(30);
                    row.createCell(7).setCellValue(1);
                    row.createCell(8).setCellValue(0);
                    row.createCell(16).setCellValue("検証案件");
                    row.createCell(25).setCellValue("PJ1");
                }
            }
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }

        ExcelTimesheetParser.ParsedTimesheet result = parser.parse(workbookBytes, null);

        assertThat(result.year()).isEqualTo(2026);
        assertThat(result.month()).isEqualTo(6);
        assertThat(result.entries()).hasSize(30);
        assertThat(result.sourceEmployeeName()).isEqualTo("検証社員");
        assertThat(result.sourceEmployeeCode()).isEqualTo("DEMO001");
        assertThat(result.standardStart()).hasToString("09:30");
        assertThat(result.standardEnd()).hasToString("18:30");
        assertThat(result.standardBreakMinutes()).isEqualTo(60);
        assertThat(result.defaultSystemCode()).isEqualTo("PJ1");
        assertThat(result.wgParticipation()).isEqualTo("参加");
        assertThat(result.entries().getFirst().workDetail()).isEqualTo("検証案件");
    }
}
