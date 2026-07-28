package jp.co.query.attendance.common;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

@Component
public class GeneratedKeyJdbc {

    private final JdbcTemplate jdbc;

    public GeneratedKeyJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(String sql, List<?> parameters) {
        var keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < parameters.size(); index++) {
                statement.setObject(index + 1, parameters.get(index));
            }
            return statement;
        }, keyHolder);
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null) {
            for (var entry : keys.entrySet()) {
                if (entry.getKey().equalsIgnoreCase("id") && entry.getValue() instanceof Number id) {
                    return id.longValue();
                }
            }
            for (Object value : keys.values()) {
                if (value instanceof Number id) return id.longValue();
            }
        }
        throw new IllegalStateException("登録したデータのIDを取得できませんでした。");
    }
}
