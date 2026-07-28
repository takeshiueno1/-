package jp.co.query.attendance.timesheet;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WorkTimeCalculatorTest {

    private final WorkTimeCalculator calculator = new WorkTimeCalculator();

    @Test
    void roundsStartAndBreakUpAndEndDownAtExcelBoundaries() {
        assertThat(WorkTimeCalculator.roundUp(0)).isZero();
        assertThat(WorkTimeCalculator.roundUp(1)).isEqualTo(15);
        assertThat(WorkTimeCalculator.roundUp(15)).isEqualTo(15);
        assertThat(WorkTimeCalculator.roundUp(16)).isEqualTo(30);
        assertThat(WorkTimeCalculator.roundUp(30)).isEqualTo(30);
        assertThat(WorkTimeCalculator.roundUp(31)).isEqualTo(45);
        assertThat(WorkTimeCalculator.roundUp(45)).isEqualTo(45);
        assertThat(WorkTimeCalculator.roundUp(46)).isEqualTo(60);
        assertThat(WorkTimeCalculator.roundUp(59)).isEqualTo(60);

        assertThat(WorkTimeCalculator.roundDown(0)).isZero();
        assertThat(WorkTimeCalculator.roundDown(14)).isZero();
        assertThat(WorkTimeCalculator.roundDown(15)).isEqualTo(15);
        assertThat(WorkTimeCalculator.roundDown(29)).isEqualTo(15);
        assertThat(WorkTimeCalculator.roundDown(30)).isEqualTo(30);
        assertThat(WorkTimeCalculator.roundDown(44)).isEqualTo(30);
        assertThat(WorkTimeCalculator.roundDown(45)).isEqualTo(45);
        assertThat(WorkTimeCalculator.roundDown(59)).isEqualTo(45);
    }

    @Test
    void calculatesNormalWorkUsingExcelRounding() {
        WorkCalculationResult result = calculator.calculate(
                LocalDate.of(2026, 6, 1), DayType.WORKDAY, 480,
                LocalTime.of(9, 31), LocalTime.of(19, 14), 61,
                null, "案件", "CODE1", "DEFAULT");

        assertThat(result.weekdayMinutes()).isEqualTo(480);
        assertThat(result.holidayMinutes()).isNull();
    }

    @Test
    void calculatesOvernightWork() {
        WorkCalculationResult result = calculator.calculate(
                LocalDate.of(2026, 6, 1), DayType.WORKDAY, 480,
                LocalTime.of(22, 0), LocalTime.of(6, 0), 60,
                null, "夜間作業", "CODE1", "DEFAULT");

        assertThat(result.weekdayMinutes()).isEqualTo(420);
    }

    @Test
    void putsSundayWorkInHolidayColumn() {
        WorkCalculationResult result = calculator.calculate(
                LocalDate.of(2026, 6, 7), DayType.SUNDAY, 480,
                LocalTime.of(9, 0), LocalTime.of(18, 0), 60,
                null, "休日作業", "CODE1", "DEFAULT");

        assertThat(result.weekdayMinutes()).isNull();
        assertThat(result.holidayMinutes()).isEqualTo(480);
    }

    @Test
    void convertsPaidLeaveToQs001() {
        WorkCalculationResult result = calculator.calculate(
                LocalDate.of(2026, 6, 1), DayType.WORKDAY, 480,
                null, null, null, "有給休暇", "", "", "DEFAULT");

        assertThat(result.systemCode()).isEqualTo("QS001");
        assertThat(result.weekdayMinutes()).isEqualTo(480);
        assertThat(result.warnings()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "360, 360",
        "420, 420"
    })
    void reproducesSixAndSevenHourPaidLeaveFromExcelMacro(
            int standardWorkMinutes,
            int expectedMinutes) {
        WorkCalculationResult result = calculator.calculate(
                LocalDate.of(2026, 6, 1), DayType.WORKDAY, standardWorkMinutes,
                null, null, null, "有給休暇", "", "", "DEFAULT");

        assertThat(result.systemCode()).isEqualTo("QS001");
        assertThat(result.weekdayMinutes()).isEqualTo(expectedMinutes);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void addsHalfDayLeaveToWorkedTime() {
        WorkCalculationResult result = calculator.calculate(
                LocalDate.of(2026, 6, 26), DayType.WORKDAY, 480,
                LocalTime.of(8, 0), LocalTime.of(13, 0), 60,
                "PM半休", "", "TKC23J001", "DEFAULT");

        assertThat(result.weekdayMinutes()).isEqualTo(480);
        assertThat(result.systemCode()).isEqualTo("TKC23J001");
    }

    @Test
    void warnsForCoreTimeAndMissingRequiredFields() {
        WorkCalculationResult late = calculator.calculate(
                LocalDate.of(2026, 6, 1), DayType.WORKDAY, 480,
                LocalTime.of(10, 1), LocalTime.of(15, 59), 60,
                null, "案件", "", "DEFAULT");
        assertThat(late.warnings()).contains(
                "始業時刻が10:00を過ぎています。",
                "終業時刻が16:00より前です。");
        assertThat(late.systemCode()).isEqualTo("DEFAULT");

        WorkCalculationResult missing = calculator.calculate(
                LocalDate.of(2026, 6, 2), DayType.WORKDAY, 480,
                null, null, null, null, "", "", "");
        assertThat(missing.warnings()).contains(
                "始業時刻が未入力です。",
                "終業時刻が未入力です。",
                "システム番号が未入力です。");
    }
}
