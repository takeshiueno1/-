package jp.co.query.attendance.common;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {

    private final JdbcClient jdbc;

    public AuditLogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String actor, String action, String targetType, String targetId, String detail) {
        jdbc.sql("""
                        INSERT INTO audit_logs (actor_username, action_name, target_type, target_id, detail)
                        VALUES (:actor, :action, :targetType, :targetId, :detail)
                        """)
                .param("actor", actor)
                .param("action", action)
                .param("targetType", targetType)
                .param("targetId", targetId)
                .param("detail", detail == null ? "" : detail)
                .update();
    }
}
