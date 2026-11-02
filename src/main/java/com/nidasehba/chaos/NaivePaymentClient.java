package com.nidasehba.chaos;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A deliberately buggy client, kept to prove the chaos tests have teeth. It makes the two classic
 * mistakes: a fresh idempotency key on every retry, and applying webhooks blindly in arrival order.
 */
public class NaivePaymentClient implements PaymentClient {

    private final FakeGateway gateway;
    private final int maxAttempts;
    private final Map<String, Order> orders = new LinkedHashMap<>();
    private final Map<String, String> orderByKey = new HashMap<>();

    public NaivePaymentClient(FakeGateway gateway, int maxAttempts) {
        this.gateway = gateway;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public Order pay(String orderId, long amountMinor) {
        Order order = orders.computeIfAbsent(orderId, id -> new Order(id, amountMinor));
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String key = "order-" + orderId + "-try-" + attempt; // BUG: new key per retry
            orderByKey.put(key, orderId);
            try {
                Charge charge = gateway.charge(key, amountMinor);
                order.update(charge.status(), charge.id(), 0);
                return order;
            } catch (GatewayTimeoutException | GatewayErrorException e) {
                // retry
            }
        }
        return order;
    }

    @Override
    public void onWebhook(WebhookEvent event) {
        Order order = orders.get(orderByKey.get(event.idempotencyKey()));
        if (order != null) {
            order.update(event.status(), event.chargeId(), 0); // BUG: an old "pending" can overwrite "succeeded"
        }
    }

    @Override
    public void reconcile() {
        // BUG: nothing; pending orders stay pending
    }

    @Override
    public Collection<Order> orders() {
        return orders.values();
    }
}
