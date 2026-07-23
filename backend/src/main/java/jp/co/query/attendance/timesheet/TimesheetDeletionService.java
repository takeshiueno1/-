package jp.co.query.attendance.timesheet;

import java.util.Objects;
import jp.co.query.attendance.common.AuditLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TimesheetDeletionService {

    public static final String CONFIRMATION_TEXT = "削除する";

    private final TimesheetRepository timesheets;
    private final UserDetailsService users;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogRepository auditLogs;

    public TimesheetDeletionService(
            TimesheetRepository timesheets,
            UserDetailsService users,
            PasswordEncoder passwordEncoder,
            AuditLogRepository auditLogs) {
        this.timesheets = timesheets;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.auditLogs = auditLogs;
    }

    @Transactional
    public void deleteOwn(
            String actor,
            int year,
            int month,
            String password,
            String confirmation) {
        delete(actor, actor, year, month, password, confirmation, false);
    }

    @Transactional
    public void deleteAsAdmin(
            String actor,
            String targetUsername,
            int year,
            int month,
            String password,
            String confirmation) {
        delete(actor, targetUsername, year, month, password, confirmation, true);
    }

    private void delete(
            String actor,
            String targetUsername,
            int year,
            int month,
            String password,
            String confirmation,
            boolean requireAdmin) {
        if (!Objects.equals(CONFIRMATION_TEXT, confirmation)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "確認欄へ「削除する」と入力してください。");
        }
        var actorDetails = users.loadUserByUsername(actor);
        if (password == null || !passwordEncoder.matches(password, actorDetails.getPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "現在ログイン中のユーザーのパスワードが正しくありません。");
        }
        if (requireAdmin && actorDetails.getAuthorities().stream()
                .noneMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理者権限が必要です。");
        }
        int deleted = timesheets.softDelete(
                targetUsername.strip().toLowerCase(java.util.Locale.ROOT),
                year,
                month,
                actor);
        if (deleted == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "削除対象の勤務表が見つかりません。");
        }
        auditLogs.record(
                actor,
                requireAdmin ? "TIMESHEET_DELETED_BY_ADMIN" : "TIMESHEET_DELETED",
                "TIMESHEET",
                targetUsername + ":" + year + "-" + month,
                "softDelete=true");
    }
}
