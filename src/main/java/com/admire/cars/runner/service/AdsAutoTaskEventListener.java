package com.admire.cars.runner.service;

import com.admire.cars.runner.event.AdsAutoTaskRegistrationEvent;
import com.admire.cars.runner.event.AdsAutoTaskAction;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AdsAutoTaskEventListener {

    private final AdsAutoTaskSchedulerService schedulerService;

    public AdsAutoTaskEventListener(AdsAutoTaskSchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @Async("adsAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdsAutoTaskRegistration(AdsAutoTaskRegistrationEvent event) {
        if (event.action() == AdsAutoTaskAction.DELETE) {
            schedulerService.delete(event);
            return;
        }
        schedulerService.upsert(event);
    }
}
