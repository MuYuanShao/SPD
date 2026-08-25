package com.hospital.spd.system.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

/**
 * Runs the daily quota statistics batch and materialises the operation cockpit read model.
 */
@Component
public class DailyQuotaStatisticsJob {

    private static final Logger log = LoggerFactory.getLogger(DailyQuotaStatisticsJob.class);

    private final OperationCockpitSnapshotService operationCockpitService;
    private final int historyMonths;

    public DailyQuotaStatisticsJob(OperationCockpitSnapshotService operationCockpitService,
                                   @Value("${spd.jobs.daily-quota.history-months:12}") int historyMonths) {
        this.operationCockpitService = operationCockpitService;
        this.historyMonths = Math.max(1, historyMonths);
    }

    @Scheduled(cron = "${spd.jobs.daily-quota.cron:0 5 0 * * *}", zone = "${spd.jobs.zone:Asia/Shanghai}")
    public void runNightly() {
        YearMonth current = YearMonth.now();
        operationCockpitService.refreshSnapshot(current.minusMonths(1));
        operationCockpitService.refreshSnapshot(current);
        log.info("Daily quota statistics completed; cockpit snapshots refreshed for {} and {}",
                current.minusMonths(1), current);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialiseMissingSnapshots() {
        YearMonth current = YearMonth.now();
        for (int offset = historyMonths - 1; offset >= 0; offset--) {
            YearMonth month = current.minusMonths(offset);
            if (!operationCockpitService.snapshotExists(month)) {
                operationCockpitService.refreshSnapshot(month);
            }
        }
        operationCockpitService.refreshSnapshot(current.minusMonths(1));
        operationCockpitService.refreshSnapshot(current);
        log.info("Operation cockpit snapshot bootstrap completed for the latest {} months", historyMonths);
    }
}
