package com.example.demo.service.eval;

import com.example.demo.config.EvalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EvalE2EReconcileScheduler {

    private static final Logger logger = LoggerFactory.getLogger(EvalE2EReconcileScheduler.class);

    private final EvalProperties evalProperties;
    private final EvalChatRunReconciler reconciler;

    public EvalE2EReconcileScheduler(EvalProperties evalProperties, EvalChatRunReconciler reconciler) {
        this.evalProperties = evalProperties;
        this.reconciler = reconciler;
    }

    @Scheduled(fixedDelayString = "${app.eval.e2e.reconcile-delay-ms:5000}")
    public void reconcileOpenItems() {
        if (!evalProperties.isReconcilerEnabled()) {
            return;
        }
        try {
            reconciler.reconcileOpenItems();
        } catch (RuntimeException exception) {
            logger.warn("E2E eval reconciliation pass failed", exception);
        }
    }
}
