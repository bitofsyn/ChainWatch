package com.chainwatch.backend.common.schema;

import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate가 {@code @Enumerated(STRING)} 컬럼에 자동 생성하는 CHECK 제약을 제거한다.
 *
 * <p>이 프로젝트는 스키마를 {@code ddl-auto: update}로 관리하는데, update 모드는 <b>이미 존재하는</b>
 * CHECK 제약을 갱신하지 않는다. 따라서 enum에 값이 추가되면(예: EventType.FAN_OUT, EventStatus.FALSE_POSITIVE)
 * 기존 DB의 제약에는 그 값이 없어 삽입/상태변경이 런타임에 실패한다. enum 유효성은 JPA가 앱 계층에서
 * 이미 보장하므로, DB 레벨의 중복 CHECK 제약을 제거해 enum 진화를 안전하게 만든다.
 *
 * <p>동작은 멱등하다. update 모드는 삭제된 CHECK 제약을 다시 만들지 않으므로 한 번 제거되면 유지된다.
 * fresh(create) DB에서는 Hibernate가 현재 enum 값 전체로 제약을 만들지만, 이 러너가 다시 제거한다.
 * H2/PostgreSQL 등 방언 차이는 문장별 예외 처리로 흡수한다.
 */
@Component
@Order(0)
public class EnumCheckConstraintCleanupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EnumCheckConstraintCleanupRunner.class);

    /** Hibernate 기본 네이밍으로 생성되는 enum CHECK 제약들. */
    private static final List<String> ENUM_CHECK_CONSTRAINTS = List.of(
            "detection_events_event_type_check",
            "detection_events_status_check",
            "detection_events_risk_level_check",
            "transactions_network_check",
            "user_accounts_role_check"
    );

    private final JdbcTemplate jdbcTemplate;

    public EnumCheckConstraintCleanupRunner(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String constraint : ENUM_CHECK_CONSTRAINTS) {
            try {
                // 테이블/제약명은 상수 목록에서만 오므로 인젝션 위험 없음. IF EXISTS로 멱등 보장.
                jdbcTemplate.execute("ALTER TABLE " + tableOf(constraint)
                        + " DROP CONSTRAINT IF EXISTS " + constraint);
            } catch (RuntimeException exception) {
                log.debug("[SCHEMA_FIX] skipped dropping {} ({})", constraint, exception.getMessage());
            }
        }
        log.info("[SCHEMA_FIX] enum CHECK constraint cleanup complete (enum validity enforced by JPA)");
    }

    /** 제약명에서 테이블명을 복원한다(예: detection_events_event_type_check → detection_events). */
    private static String tableOf(String constraint) {
        if (constraint.startsWith("detection_events_")) {
            return "detection_events";
        }
        if (constraint.startsWith("transactions_")) {
            return "transactions";
        }
        if (constraint.startsWith("user_accounts_")) {
            return "user_accounts";
        }
        // 폴백: 마지막 세 토큰(_x_check) 이전까지를 테이블명으로 간주
        return constraint.replace("_check", "").replaceAll("_[^_]+$", "");
    }
}
