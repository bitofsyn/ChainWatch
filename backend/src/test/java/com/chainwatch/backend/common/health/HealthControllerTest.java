package com.chainwatch.backend.common.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthControllerTest {

    @Test
    void reportsRunningCommitSoDeploysCanBeConfirmed() {
        assertThat(new HealthController("9f56e29abc").health())
                .containsEntry("status", "UP")
                .containsEntry("commit", "9f56e29abc");
    }
}
