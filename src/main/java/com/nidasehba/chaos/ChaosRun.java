package com.nidasehba.chaos;

import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;

/** One reproducible chaos experiment: a seed decides the fault plan, the orders and every fault. */
public final class ChaosRun {

    private ChaosRun() {
    }

    public record Result(long seed, FaultPlan plan, int gatewayCalls, List<String> violations) {
        public boolean clean() {
            return violations.isEmpty();
        }
    }

    public static Result run(long seed, int orders, BiFunction<FakeGateway, Integer, PaymentClient> clientFactory) {
        Random rnd = new Random(seed);
        FaultPlan plan = FaultPlan.random(rnd);
        FakeGateway gateway = new FakeGateway(plan, seed);
        PaymentClient client = clientFactory.apply(gateway, 3);

        for (int i = 1; i <= orders; i++) {
            client.pay(String.valueOf(i), 1_000L + rnd.nextInt(9_000));
            if (rnd.nextInt(4) == 0) {
                gateway.drainWebhooks().forEach(client::onWebhook); // webhooks arrive while payments are still going
            }
        }
        List<WebhookEvent> late = gateway.drainWebhooks();
        late.forEach(client::onWebhook);
        client.reconcile();
        late.forEach(client::onWebhook); // replay: a webhook delivered again after settlement must change nothing
        return new Result(seed, plan, gateway.calls(), Invariants.check(client, gateway));
    }
}
