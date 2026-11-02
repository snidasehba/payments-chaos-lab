package com.nidasehba.chaos;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A payment client written the way payments code has to be written:
 * <ul>
 *   <li>one idempotency key per order, reused on every retry, so a lost response can never cause a second charge;</li>
 *   <li>retries only on timeouts and 5xx;</li>
 *   <li>webhooks deduplicated by event id and applied only if newer (by sequence) than what the order already has;</li>
 *   <li>orders still pending at the end are reconciled against the gateway instead of being guessed.</li>
 * </ul>
 */
public class ResilientPaymentClient implements PaymentClient {

    private static final int FINAL_SEQUENCE = 2;

    private final FakeGateway gateway;
    private final int maxAttempts;
    private final Map<String, Order> ordersByKey = new LinkedHashMap<>();
    private final Set<String> seenEvents = new HashSet<>();

    public ResilientPaymentClient(FakeGateway gateway, int maxAttempts) {
        this.gateway = gateway;
        this.maxAttempts = maxAttempts;
    }

    static String keyFor(String orderId) {
        return "order-" + orderId;
    }

    @Override
    public Order pay(String orderId, long amountMinor) {
        String key = keyFor(orderId);
        Order order = ordersByKey.computeIfAbsent(key, k -> new Order(orderId, amountMinor));
        if (order.status().isFinal()) {
            return order;
        }
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Charge charge = gateway.charge(key, amountMinor);
                if (charge.status().isFinal()) {
                    order.update(charge.status(), charge.id(), FINAL_SEQUENCE);
                }
                return order;
            } catch (GatewayTimeoutException | GatewayErrorException e) {
                // Same key on the next attempt: if the first call did charge, the gateway returns that charge.
            }
        }
        return order; // still PENDING: webhooks or reconciliation will settle it
    }

    @Override
    public void onWebhook(WebhookEvent event) {
        if (!seenEvents.add(event.eventId())) {
            return;
        }
        Order order = ordersByKey.get(event.idempotencyKey());
        if (order == null || event.sequence() <= order.lastSequence()) {
            return;
        }
        order.update(event.status(), event.chargeId(), event.sequence());
    }

    @Override
    public void reconcile() {
        for (Order order : ordersByKey.values()) {
            if (order.status().isFinal()) {
                continue;
            }
            gateway.findByKey(keyFor(order.orderId())).ifPresentOrElse(
                    charge -> order.update(charge.status(), charge.id(), FINAL_SEQUENCE),
                    () -> order.update(ChargeStatus.FAILED, null, FINAL_SEQUENCE)); // never charged: safe to fail
        }
    }

    @Override
    public Collection<Order> orders() {
        return ordersByKey.values();
    }
}
