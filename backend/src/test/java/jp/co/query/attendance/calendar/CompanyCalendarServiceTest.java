package jp.co.query.attendance.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CompanyCalendarServiceTest {

    @Test
    void extractsLabeledCompanyHolidaysAndExpandsRanges() {
        String text = """
                株式会社クエリ 2027年 会社カレンダー
                2027年8月10日 クエリ創立記念日
                2027年8月12日～2027年8月14日 夏季休暇
                2027年8月17日 通常勤務
                """;

        var holidays = CompanyCalendarService.parseText(text);

        assertThat(holidays).containsOnlyKeys(
                LocalDate.of(2027, 8, 10),
                LocalDate.of(2027, 8, 12),
                LocalDate.of(2027, 8, 13),
                LocalDate.of(2027, 8, 14));
        assertThat(holidays).doesNotContainKey(LocalDate.of(2027, 8, 17));
    }
}
