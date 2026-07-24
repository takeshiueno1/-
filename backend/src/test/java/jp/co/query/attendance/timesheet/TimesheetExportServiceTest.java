package jp.co.query.attendance.timesheet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import jp.co.query.attendance.employee.Employee;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class TimesheetExportServiceTest {

    @Test
    void createsExcelWorkbookWithIdentityAndDailyEntries() throws Exception {
        Employee employee = new Employee(
                1, "test", "システム部", "上野 豪", "主任", "Q001", "正社員（8時間）",
                LocalTime.of(9, 0), LocalTime.of(18, 0), 60, "SYS001");
        DailyEntry entry = new DailyEntry(
                1, LocalDate.of(2026, 6, 1), DayType.WORKDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0), 60, null,
                "開発", "SYS001", 480, null, List.of());
        MonthlyTotals totals = new MonthlyTotals(480, 0, 0, 1, 480, 0, List.of());
        Timesheet timesheet = new Timesheet(
                10, 2026, 6, LocalTime.of(9, 0), LocalTime.of(18, 0), 60,
                "SYS001", "参加", LocalDate.of(2026, 6, 30), employee, List.of(entry), totals);

        byte[] result = TimesheetExportService.createWorkbook(timesheet);

        assertThat(result).isNotEmpty();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var sheet = workbook.getSheet("勤務表");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("勤務表");
            assertThat(sheet.getRow(2).getCell(4).getStringCellValue()).isEqualTo("上野 豪");
            assertThat(sheet.getRow(7).getCell(7).getStringCellValue()).isEqualTo("開発");
            assertThat(sheet.getRow(8).getCell(4).getStringCellValue()).isEqualTo("8:00");
        }
    }
}
