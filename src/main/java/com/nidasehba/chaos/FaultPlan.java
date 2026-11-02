package com.nidasehba.chaos;

import java.util.Random;

/**
 * How badly the fake gateway misbehaves. Probabilities are per call (or per webhook for duplicates).
 *
 * @param timeoutBeforeProcessing the request never reaches the gateway; nothing is charged
 * @param timeoutAfterProcessing  the gateway charges the card but the response is lost; the dangerous case
 * @param serverError             a 5xx before processing
 * @param declineRate             the charge is processed but declined (a business outcome, not a fault)
 * @param duplicateWebhooks       each webhook is delivered a second time
 * @param shuffleWebhooks         webhooks are delivered in random order
 */
public record FaultPlan(double timeoutBeforeProcessing, double timeoutAfterProcessing, double serverError,
                        double declineRate, double duplicateWebhooks, boolean shuffleWebhooks) {

    public static FaultPlan none() {
        return new FaultPlan(0, 0, 0, 0, 0, false);
    }

    /** A random but reproducible plan; the same seed always gives the same plan. */
    public static FaultPlan random(Random rnd) {
        return new FaultPlan(rnd.nextDouble() * 0.3, rnd.nextDouble() * 0.3, rnd.nextDouble() * 0.2,
                rnd.nextDouble() * 0.2, rnd.nextDouble() * 0.5, rnd.nextBoolean());
    }
}
