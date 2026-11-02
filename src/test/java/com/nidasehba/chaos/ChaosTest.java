package com.nidasehba.chaos;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hundreds of random but reproducible fault mixes. If one breaks an invariant, the message gives the
 * seed, so the exact run can be replayed in a debugger with {@code ChaosRun.run(seed, ...)}.
 */
class ChaosTest {

    private static final int SEEDS = 400;
    private static final int ORDERS = 25;

    @Test
    void resilientClientSurvivesEveryChaosRun() {
        List<String> failures = new ArrayList<>();
        for (long seed = 1; seed <= SEEDS; seed++) {
            ChaosRun.Result r = ChaosRun.run(seed, ORDERS, ResilientPaymentClient::new);
            if (!r.clean()) {
                failures.add("seed " + seed + " " + r.plan() + ": " + r.violations());
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " failing runs, first: " + (failures.isEmpty() ? "" : failures.get(0)));
    }

    @Test
    void theSameSeedReplaysTheSameRun() {
        ChaosRun.Result a = ChaosRun.run(42, ORDERS, ResilientPaymentClient::new);
        ChaosRun.Result b = ChaosRun.run(42, ORDERS, ResilientPaymentClient::new);
        assertEquals(a.plan(), b.plan());
        assertEquals(a.gatewayCalls(), b.gatewayCalls());
    }

    /** A chaos suite is only worth having if it catches real bugs. The naive client must fail it. */
    @Test
    void chaosRunsCatchTheNaiveClientsBugs() {
        int broken = 0;
        boolean doubleCharge = false;
        boolean stuckOrRegressed = false;
        for (long seed = 1; seed <= SEEDS; seed++) {
            ChaosRun.Result r = ChaosRun.run(seed, ORDERS, NaivePaymentClient::new);
            if (!r.clean()) {
                broken++;
                doubleCharge |= r.violations().stream().anyMatch(v -> v.startsWith("double charge"));
                stuckOrRegressed |= r.violations().stream().anyMatch(v -> v.contains("PENDING"));
            }
        }
        assertTrue(broken > SEEDS / 2, "naive client broke only " + broken + " runs");
        assertTrue(doubleCharge, "expected at least one double charge");
        assertTrue(stuckOrRegressed, "expected at least one order stuck or knocked back to PENDING");
    }

    @Test
    void noFaultsMeansNoViolationsEvenForTheNaiveClient() {
        FakeGateway gw = new FakeGateway(FaultPlan.none(), 9);
        NaivePaymentClient client = new NaivePaymentClient(gw, 3);
        for (int i = 1; i <= 10; i++) {
            client.pay(String.valueOf(i), 1_000);
        }
        gw.drainWebhooks().forEach(client::onWebhook);
        assertFalse(Invariants.check(client, gw).stream().anyMatch(v -> v.startsWith("double")));
        assertTrue(Invariants.check(client, gw).isEmpty());
    }
}
