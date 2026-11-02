package com.nidasehba.chaos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The properties that must hold after any amount of chaos. Each returns human-readable violations;
 * an empty list means the run was clean.
 */
public final class Invariants {

    private Invariants() {
    }

    public static List<String> check(PaymentClient client, FakeGateway gateway) {
        List<String> violations = new ArrayList<>();
        Collection<Order> orders = client.orders();
        Map<String, Order> byOrder = orders.stream().collect(Collectors.toMap(Order::orderId, o -> o));

        // 1. Nobody is charged twice: at most one successful charge per order.
        Map<String, Long> successfulChargesPerOrder = gateway.charges().stream()
                .filter(c -> c.status() == ChargeStatus.SUCCEEDED)
                .collect(Collectors.groupingBy(c -> orderIdOf(c.idempotencyKey()), Collectors.counting()));
        successfulChargesPerOrder.forEach((orderId, n) -> {
            if (n > 1) {
                violations.add("double charge: order " + orderId + " has " + n + " successful charges");
            }
        });

        for (Order order : orders) {
            // 2. Nothing is left in limbo.
            if (!order.status().isFinal()) {
                violations.add("order " + order.orderId() + " still PENDING after webhooks and reconciliation");
            }
            // 3. The merchant's status matches what actually happened at the gateway.
            boolean charged = successfulChargesPerOrder.getOrDefault(order.orderId(), 0L) > 0;
            if (order.status() == ChargeStatus.SUCCEEDED && !charged) {
                violations.add("order " + order.orderId() + " marked paid but no successful charge exists");
            }
            if (order.status() == ChargeStatus.FAILED && charged) {
                violations.add("order " + order.orderId() + " marked failed but the customer was charged");
            }
        }

        // 4. Money reconciles: what we think we collected equals what the gateway collected.
        long ledger = orders.stream().filter(o -> o.status() == ChargeStatus.SUCCEEDED).mapToLong(Order::amountMinor).sum();
        long collected = gateway.charges().stream().filter(c -> c.status() == ChargeStatus.SUCCEEDED)
                .filter(c -> byOrder.containsKey(orderIdOf(c.idempotencyKey())))
                .mapToLong(Charge::amountMinor).sum();
        if (ledger != collected) {
            violations.add("ledger " + ledger + " != collected " + collected);
        }
        return violations;
    }

    /** "order-42" and the naive client's "order-42-try-3" both belong to order 42. */
    static String orderIdOf(String idempotencyKey) {
        String rest = idempotencyKey.substring("order-".length());
        int tryIdx = rest.indexOf("-try-");
        return tryIdx < 0 ? rest : rest.substring(0, tryIdx);
    }
}
