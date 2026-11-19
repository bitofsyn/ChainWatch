package com.chainwatch.backend.common.health;

import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * 실행 중인 빌드의 커밋. Render는 RENDER_GIT_COMMIT을 자동으로 넣어 준다.
     * CD는 이 값이 배포를 요청한 커밋과 같아질 때까지 기다려, 이전 인스턴스의 응답을 배포 완료로 착각하지 않는다.
     */
    private final String commit;

    public HealthController(@Value("${RENDER_GIT_COMMIT:${CHAINWATCH_GIT_COMMIT:unknown}}") String commit) {
        this.commit = commit;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "service", "chainwatch-backend",
                "status", "UP",
                "commit", commit,
                "timestamp", Instant.now().toString()
        );
    }
}
