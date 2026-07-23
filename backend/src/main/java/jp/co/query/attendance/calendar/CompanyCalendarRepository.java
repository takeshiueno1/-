package jp.co.query.attendance.calendar;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CompanyCalendarRepository {

    private final JdbcClient jdbc;

    public CompanyCalendarRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long createImport(
            String filename,
            String sha256,
            long byteSize,
            int pageCount,
            int importedDates,
            String actor) {
        return jdbc.sql("""
                        INSERT INTO company_calendar_imports (
                            original_filename, content_sha256, byte_size, page_count, imported_dates, imported_by)
                        VALUES (:filename, :sha256, :byteSize, :pageCount, :importedDates, :actor)
                        RETURNING id
                        """)
                .param("filename", filename)
                .param("sha256", sha256)
                .param("byteSize", byteSize)
                .param("pageCount", pageCount)
                .param("importedDates", importedDates)
                .param("actor", actor)
                .query(Long.class)
                .single();
    }

    public void upsertHolidays(long importId, Map<LocalDate, String> holidays) {
        holidays.forEach((date, name) -> jdbc.sql("""
                        INSERT INTO company_holidays (work_date, holiday_name, source_type, import_id)
                        VALUES (:date, :name, 'PDF', :importId)
                        ON CONFLICT (work_date) DO UPDATE
                            SET holiday_name = EXCLUDED.holiday_name,
                                source_type = EXCLUDED.source_type,
                                import_id = EXCLUDED.import_id
                        """)
                .param("date", Date.valueOf(date))
                .param("name", name)
                .param("importId", importId)
                .update());
    }
}
