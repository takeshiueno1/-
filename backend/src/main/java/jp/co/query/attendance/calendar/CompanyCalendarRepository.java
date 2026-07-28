package jp.co.query.attendance.calendar;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import jp.co.query.attendance.common.GeneratedKeyJdbc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CompanyCalendarRepository {

    private final JdbcClient jdbc;
    private final GeneratedKeyJdbc generatedKeys;

    public CompanyCalendarRepository(JdbcClient jdbc, GeneratedKeyJdbc generatedKeys) {
        this.jdbc = jdbc;
        this.generatedKeys = generatedKeys;
    }

    public long createImport(
            String filename,
            String sha256,
            long byteSize,
            int pageCount,
            int importedDates,
            String actor) {
        return generatedKeys.insert("""
                        INSERT INTO company_calendar_imports (
                            original_filename, content_sha256, byte_size, page_count, imported_dates, imported_by)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                List.of(filename, sha256, byteSize, pageCount, importedDates, actor));
    }

    public void upsertHolidays(long importId, Map<LocalDate, String> holidays) {
        holidays.forEach((date, name) -> {
            int updated = jdbc.sql("""
                            UPDATE company_holidays
                               SET holiday_name = :name,
                                   source_type = 'PDF',
                                   import_id = :importId
                             WHERE work_date = :date
                            """)
                    .param("date", Date.valueOf(date))
                    .param("name", name)
                    .param("importId", importId)
                    .update();
            if (updated == 0) {
                jdbc.sql("""
                                INSERT INTO company_holidays (work_date, holiday_name, source_type, import_id)
                                VALUES (:date, :name, 'PDF', :importId)
                                """)
                        .param("date", Date.valueOf(date))
                        .param("name", name)
                        .param("importId", importId)
                        .update();
            }
        });
    }
}
