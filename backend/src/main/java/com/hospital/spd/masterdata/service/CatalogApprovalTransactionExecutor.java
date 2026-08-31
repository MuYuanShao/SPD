package com.hospital.spd.masterdata.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** Runs one batch item in an independent transaction. */
@Component
public class CatalogApprovalTransactionExecutor {
    private final TransactionTemplate requiresNew;

    public CatalogApprovalTransactionExecutor(PlatformTransactionManager transactionManager) {
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T execute(Supplier<T> action) {
        return requiresNew.execute(status -> action.get());
    }
}
