package jp.co.query.attendance.timesheet;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MonthlyAggregatorTest {

    private final MonthlyAggregator aggregator = new MonthlyAggregator();

    @Test
    void reproducesHalfDayAllocationAndDoesNotLimitSystemCodesToFive() {
        List<DailyEntry> entries = List.of(
                entry(1, "2026-06-01", "CODE1", null, 480, DayType.WORKDAY),
                entry(2, "2026-06-02", "CODE2", null, 480, DayType.WORKDAY),
                entry(3, "2026-06-03", "CODE3", null, 480, DayType.WORKDAY),
                entry(4, "2026-06-04", "CODE4", null, 480, DayType.WORKDAY),
                entry(5, "2026-06-05", "CODE5", null, 480, DayType.WORKDAY),
                entry(6, "2026-06-08", "CODE6", null, 480, DayType.WORKDAY),
                entry(7, "2026-06-09", "CODE1", "PM半休", 480, DayType.WORKDAY));

        MonthlyTotals result = aggregator.aggregate(entries, 480);

        assertThat(result.systemTotals()).hasSize(7);
        assertThat(result.systemTotals()).filteredOn(total -> total.systemCode().equals("CODE1")).singleElement().satisfies(total -> {
            assertThat(total.days()).isEqualTo(1.5);
            assertThat(total.minutes()).isEqualTo(720);
        });
        assertThat(result.systemTotals()).filteredOn(total -> total.systemCode().equals("QS001")).singleElement().satisfies(total -> {
            assertThat(total.days()).isEqualTo(0.5);
            assertThat(total.minutes()).isEqualTo(240);
        });
        assertThat(result.halfDayCount()).isEqualTo(1);
        assertThat(result.requiredDays()).isEqualTo(7);
    }

    private static DailyEntry entry(long id, String date, String code, String leave, int minutes, DayType dayType) {
        return new DailyEntry(
                id, LocalDate.parse(date), dayType, null, null, null, leave, "", code,
                minutes, null, List.of());
    }
}
