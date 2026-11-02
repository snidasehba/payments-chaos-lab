package com.nidasehba.chaos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * An in-memory payment gateway that behaves like a real one on a bad day. It is honest about one thing
 * real gateways guarantee: the same idempotency key never creates a second charge. Everything else
 * (timeouts, 5xx, duplicate and out-of-order webhooks) is driven by the {@link FaultPlan} and a seed, so
 * any failure found by a chaos run can be replayed exactly.
 */
public class FakeGateway {

    private final FaultPlan plan;
    private final Random rnd;
    private final Map<String, Charge> chargesByKey = new LinkedHashMap<>();
    private final List<WebhookEvent> outbox = new ArrayList<>();
    private int nextCharge = 1;
    private int nextEvent = 1;
    private int calls;

    public FakeGateway(FaultPlan plan, long seed) {
        this.plan = plan;
        this.rnd = new Random(seed);
    }

    public Charge charge(String idempotencyKey, long amountMinor) {
        calls++;
        if (rnd.nextDouble() < plan.timeoutBeforeProcessing()) {
            throw new GatewayTimeoutException("timeout before processing");
        }
        if (rnd.nextDouble() < plan.serverError()) {
            throw new GatewayErrorException("503 Service Unavailable");
        }
        Charge charge = chargesByKey.get(idempotencyKey);
        if (charge != null && charge.amountMinor() != amountMinor) {
            throw new IllegalArgumentException("idempotency key " + idempotencyKey + " reused with a different amount");
        }
        if (charge == null) {
            ChargeStatus result = rnd.nextDouble() < plan.declineRate() ? ChargeStatus.FAILED : ChargeStatus.SUCCEEDED;
            charge = new Charge("ch_" + nextCharge++, idempotencyKey, amountMinor, result);
            chargesByKey.put(idempotencyKey, charge);
            emit(charge, 1, ChargeStatus.PENDING);
            emit(charge, 2, result);
        }
        if (rnd.nextDouble() < plan.timeoutAfterProcessing()) {
            throw new GatewayTimeoutException("timeout after processing (charge " + charge.id() + " exists)");
        }
        return charge;
    }

    /** Lookup by idempotency key, used by reconciliation when the client never got an answer. */
    public Optional<Charge> findByKey(String idempotencyKey) {
        return Optional.ofNullable(chargesByKey.get(idempotencyKey));
    }

    /** Delivers everything queued so far, with duplicates and shuffling according to the plan. */
    public List<WebhookEvent> drainWebhooks() {
        List<WebhookEvent> delivery = new ArrayList<>();
        for (WebhookEvent e : outbox) {
            delivery.add(e);
            if (rnd.nextDouble() < plan.duplicateWebhooks()) {
                delivery.add(e);
            }
        }
        outbox.clear();
        if (plan.shuffleWebhooks()) {
            Collections.shuffle(delivery, rnd);
        }
        return delivery;
    }

    public Collection<Charge> charges() {
        return Collections.unmodifiableCollection(chargesByKey.values());
    }

    public int calls() {
        return calls;
    }

    private void emit(Charge charge, int sequence, ChargeStatus status) {
        outbox.add(new WebhookEvent("evt_" + nextEvent++, charge.id(), charge.idempotencyKey(), sequence, status));
    }
}
