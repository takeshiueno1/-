package jp.co.query.attendance.timesheet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MonthlyAggregator {

    public MonthlyTotals aggregate(List<DailyEntry> entries, int standardWorkMinutes) {
        int weekdayMinutes = entries.stream().map(DailyEntry::weekdayMinutes).filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
        int holidayMinutes = entries.stream().map(DailyEntry::holidayMinutes).filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
        int requiredDays = (int) entries.stream().filter(entry -> entry.dayType() == DayType.WORKDAY).count();
        int halfDayCount = (int) entries.stream().filter(entry -> normalize(entry.leaveType()).contains("半休")).count();

        Map<String, MutableTotal> totals = new LinkedHashMap<>();
        for (DailyEntry entry : entries) {
            String code = normalize(entry.systemCode());
            if (code.isBlank()) continue;
            int minutes = entry.weekdayMinutes() != null ? entry.weekdayMinutes() : entry.holidayMinutes() != null ? entry.holidayMinutes() : 0;
            MutableTotal total = totals.computeIfAbsent(code, ignored -> new MutableTotal());
            total.days += 1;
            total.minutes += minutes;
        }

        for (DailyEntry entry : entries) {
            if (!normalize(entry.leaveType()).contains("半休")) continue;
            String code = normalize(entry.systemCode());
            if (!code.isBlank()) {
                MutableTotal worked = totals.get(code);
                if (worked != null) {
                    worked.days -= 0.5;
                    worked.minutes -= standardWorkMinutes / 2;
                }
            }
            MutableTotal paid = totals.computeIfAbsent("QS001", ignored -> new MutableTotal());
            paid.days += 0.5;
            paid.minutes += standardWorkMinutes / 2;
        }

        var systemTotals = totals.entrySet().stream()
                .filter(entry -> entry.getValue().days != 0 || entry.getValue().minutes != 0)
                .map(entry -> new MonthlyTotals.SystemTotal(entry.getKey(), entry.getValue().days, entry.getValue().minutes))
                .toList();
        int requiredMinutes = requiredDays * standardWorkMinutes;
        return new MonthlyTotals(
                weekdayMinutes,
                holidayMinutes,
                halfDayCount,
                requiredDays,
                requiredMinutes,
                weekdayMinutes + holidayMinutes - requiredMinutes,
                systemTotals);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class MutableTotal {
        private double days;
        private int minutes;
    }
}
