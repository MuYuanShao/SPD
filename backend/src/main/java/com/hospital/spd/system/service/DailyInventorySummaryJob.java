package com.hospital.spd.system.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Generates the preceding business day's inventory summary at midnight and catches up missed days. */
@Component
public class DailyInventorySummaryJob {
    private static final Logger log = LoggerFactory.getLogger(DailyInventorySummaryJob.class);
    private final DailyInventorySummaryService summaryService;

    public DailyInventorySummaryJob(DailyInventorySummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @Scheduled(cron = "${spd.jobs.inventory-summary.cron:0 0 0 * * *}", zone = "${spd.jobs.zone:Asia/Shanghai}")
    public void runAtMidnight() {
        generateMissingDays("midnight");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialiseLatestSnapshot() {
        generateMissingDays("startup");
    }

    private void generateMissingDays(String trigger) {
        LocalDate target = LocalDate.now().minusDays(1);
        int generated = summaryService.generateThrough(target);
        log.info("Inventory daily summary {} completed through {}; generated {} day(s)", trigger, target, generated);
    }
}
