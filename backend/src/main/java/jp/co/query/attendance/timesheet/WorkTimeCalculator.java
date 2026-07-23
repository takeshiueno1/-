package jp.co.query.attendance.timesheet;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkTimeCalculator {

    public WorkCalculationResult calculate(
            LocalDate workDate,
            DayType dayType,
            int standardWorkMinutes,
            LocalTime startTime,
            LocalTime endTime,
            Integer breakMinutes,
            String leaveType,
            String workDetail,
            String systemCode,
            String defaultSystemCode) {

        String leave = normalize(leaveType);
        String detail = normalize(workDetail);
        String code = normalize(systemCode);
        boolean paid = contains(leave, "有給休暇");
        boolean special = contains(leave, "特別休暇");
        boolean condolence = contains(leave, "慶弔休暇");
        boolean substitute = contains(leave, "振替休日");
        boolean absence = contains(leave, "欠勤");
        boolean half = contains(leave, "半休");
        boolean medical = contains(detail, "健康診断");
        boolean otherLeave = !leave.isBlank() && !paid && !special && !condolence
                && !substitute && !absence && !half;

        LocalTime calculatedStart = startTime;
        LocalTime calculatedEnd = endTime;
        Integer calculatedBreak = breakMinutes;
        Integer workedMinutes = null;
        List<String> warnings = new ArrayList<>();

        if (startTime != null && endTime != null) {
            calculatedBreak = breakMinutes == null ? 60 : breakMinutes;
            int roundedStart = startTime.getHour() * 60 + roundUp(startTime.getMinute());
            int roundedEnd = endTime.getHour() * 60 + roundDown(endTime.getMinute());
            int roundedBreak = roundBreakUp(calculatedBreak);
            int effectiveEnd = roundedEnd == 0 ? 24 * 60 : roundedEnd;
            int result = effectiveEnd - roundedStart - roundedBreak;
            if (result < 0) {
                result += 24 * 60;
            }
            workedMinutes = result;
            if (roundedStart > 10 * 60) {
                warnings.add("始業時刻が10:00を過ぎています。");
            }
            if (roundedEnd < 16 * 60 && roundedEnd != 0) {
                warnings.add("終業時刻が16:00より前です。");
            }
            if (dayType == DayType.WORKDAY && code.isBlank()) {
                code = normalize(defaultSystemCode);
            }
        }

        if (paid || special || condolence || substitute || absence || (half && medical)) {
            calculatedStart = null;
            calculatedEnd = null;
            calculatedBreak = null;
            workedMinutes = null;
        }

        if (paid || special || condolence || (half && medical)) {
            workedMinutes = standardWorkMinutes;
        } else if (half || medical) {
            workedMinutes = (workedMinutes == null ? 0 : workedMinutes) + standardWorkMinutes / 2;
        }

        if (paid) {
            code = "QS001";
        } else if (special) {
            code = "QS002";
        } else if (condolence) {
            code = "QS003";
        } else if (absence) {
            code = "欠勤";
        } else if (medical) {
            code = "健康診断";
        } else if (substitute) {
            code = "";
        } else if (otherLeave) {
            code = leave;
        }

        if (half && code.isBlank()) {
            code = normalize(defaultSystemCode);
        }

        if (dayType == DayType.WORKDAY && startTime == null && !isNonWarningLeave(paid, special, condolence, substitute, absence, half, medical, otherLeave)) {
            warnings.add("始業時刻が未入力です。");
        }
        if (dayType == DayType.WORKDAY && endTime == null && !isNonWarningLeave(paid, special, condolence, substitute, absence, half, medical, otherLeave)) {
            warnings.add("終業時刻が未入力です。");
        }
        if (dayType == DayType.WORKDAY && code.isBlank() && !isNonWarningLeave(paid, special, condolence, substitute, absence, half, medical, otherLeave)) {
            warnings.add("システム番号が未入力です。");
        }

        Integer weekdayMinutes = null;
        Integer holidayMinutes = null;
        if (workedMinutes != null) {
            if (workDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
                holidayMinutes = workedMinutes;
            } else {
                weekdayMinutes = workedMinutes;
            }
        }

        return new WorkCalculationResult(
                calculatedStart,
                calculatedEnd,
                calculatedBreak,
                code,
                weekdayMinutes,
                holidayMinutes,
                List.copyOf(warnings));
    }

    static int roundUp(int minute) {
        if (minute == 0) return 0;
        if (minute <= 15) return 15;
        if (minute <= 30) return 30;
        if (minute <= 45) return 45;
        return 60;
    }

    static int roundDown(int minute) {
        if (minute <= 14) return 0;
        if (minute <= 29) return 15;
        if (minute <= 44) return 30;
        return 45;
    }

    private static int roundBreakUp(int minutes) {
        int hours = minutes / 60;
        return hours * 60 + roundUp(minutes % 60);
    }

    private static boolean isNonWarningLeave(
            boolean paid,
            boolean special,
            boolean condolence,
            boolean substitute,
            boolean absence,
            boolean half,
            boolean medical,
            boolean otherLeave) {
        return paid || special || condolence || substitute || absence || otherLeave || (half && medical);
    }

    private static boolean contains(String source, String value) {
        return source.contains(value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
