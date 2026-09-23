package uk.gov.hmcts.reform.hmc.config;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import uk.gov.hmcts.reform.hmc.BaseTest;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerLockConfigurationIT extends BaseTest {

    private static final String LOCK_NAME_PREFIX = "scheduler-lock-test-";

    private final LockProvider lockProvider;
    private final JdbcTemplate jdbcTemplate;

    private String lockName;

    @Autowired
    SchedulerLockConfigurationIT(LockProvider lockProvider, JdbcTemplate jdbcTemplate) {
        this.lockProvider = lockProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void removeTestLock() {
        if (lockName != null) {
            jdbcTemplate.update("DELETE FROM public.shedlock WHERE name = ?", lockName);
        }
    }

    @Test
    @Sql("classpath:sql/create-shedlock-table.sql")
    void shouldAcquireLockAndCreateDatabaseRow() {
        lockName = newLockName();

        Optional<SimpleLock> lock = lockProvider.lock(lockConfiguration());

        assertThat(lock).isPresent();
        assertThat(lockRowCount()).isOne();

        lock.orElseThrow().unlock();
    }

    @Test
    @Sql("classpath:sql/create-shedlock-table.sql")
    void shouldRejectSecondConcurrentLockAcquisition() {
        lockName = newLockName();

        SimpleLock firstLock = lockProvider.lock(lockConfiguration()).orElseThrow();
        Optional<SimpleLock> secondLock = lockProvider.lock(lockConfiguration());

        assertThat(secondLock).isEmpty();

        firstLock.unlock();
    }

    @Test
    @Sql("classpath:sql/create-shedlock-table.sql")
    void shouldAllowLockAcquisitionAfterFirstLockIsReleased() {
        lockName = newLockName();

        SimpleLock firstLock = lockProvider.lock(lockConfiguration()).orElseThrow();
        firstLock.unlock();

        Optional<SimpleLock> secondLock = lockProvider.lock(lockConfiguration());

        assertThat(secondLock).isPresent();
        secondLock.orElseThrow().unlock();
    }

    private LockConfiguration lockConfiguration() {
        return new LockConfiguration(
            Instant.now(),
            lockName,
            Duration.ofMinutes(1),
            Duration.ZERO
        );
    }

    private String newLockName() {
        return LOCK_NAME_PREFIX + UUID.randomUUID();
    }

    private int lockRowCount() {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM public.shedlock WHERE name = ?",
            Integer.class,
            lockName
        );
    }
}
