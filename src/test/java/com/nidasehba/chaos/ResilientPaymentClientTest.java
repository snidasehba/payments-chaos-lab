package com.nidasehba.chaos;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One fault at a time, so each failure mode has a test that names it. */
class ResilientPaymentClientTest {

    private static FaultPlan plan(double timeoutBefore, double timeoutAfter, double serverError, double decline) {
        return new FaultPlan(timeoutBefore, timeoutAfter, serverError, decline, 0, false);
    }

    @Test
    void happyPathChargesOnceAndMarksPaid() {
        FakeGateway gw = new FakeGateway(FaultPlan.none(), 1);
        ResilientPaymentClient client = new ResilientPaymentClient(gw, 3);
        Order order = client.pay("1", 5_000);
        assertEquals(ChargeStatus.SUCCEEDED, order.status());
        assertEquals(1, gw.charges().size());
        assertTrue(Invariants.check(client, gw).isEmpty());
    }

    @Test
    void lostResponseAndRetryDoesNotChargeTwice() {
        // Every response is lost after the gateway has charged: the classic double-charge setup.
        FakeGateway gw = new FakeGateway(plan(0, 1.0, 0, 0), 2);
        ResilientPaymentClient client = new ResilientPaymentClient(gw, 3);
        Order order = client.pay("7", 2_500);
        assertEquals(3, gw.calls(), "retried every attempt");
        assertEquals(1, gw.charges().size(), "but only one charge exists");
        assertEquals(ChargeStatus.PENDING, order.status(), "no answer yet, so it must not guess");

        gw.drainWebhooks().forEach(client::onWebhook);
        assertEquals(ChargeStatus.SUCCEEDED, order.status());
        assertTrue(Invariants.check(client, gw).isEmpty());
    }

    @Test
    void neverReachedTheGatewayIsReconciledAsFailed() {
        FakeGateway gw = new FakeGateway(plan(1.0, 0, 0, 0), 3);
        ResilientPaymentClient client = new ResilientPaymentClient(gw, 3);
        client.pay("9", 1_000);
        assertTrue(gw.charges().isEmpty());
        client.reconcile();
        assertEquals(ChargeStatus.FAILED, client.orders().iterator().next().status());
        assertTrue(Invariants.check(client, gw).isEmpty());
    }

    @Test
    void serverErrorsAreRetried() {
        FakeGateway gw = new FakeGateway(plan(0, 0, 0.6, 0), 4);
        ResilientPaymentClient client = new ResilientPaymentClient(gw, 10);
        for (int i = 0; i < 20; i++) {
            client.pay(String.valueOf(i), 1_000);
        }
        gw.drainWebhooks().forEach(client::onWebhook);
        client.reconcile();
        assertTrue(Invariants.check(client, gw).isEmpty(), Invariants.check(client, gw).toString());
    }

    @Test
    void declinedPaymentIsFailedNotRetriedIntoASuccess() {
        FakeGateway gw = new FakeGateway(plan(0, 0, 0, 1.0), 5);
        ResilientPaymentClient client = new ResilientPaymentClient(gw, 3);
        assertEquals(ChargeStatus.FAILED, client.pay("3", 4_000).status());
        assertEquals(1, gw.calls(), "a decline is an answer, not an error to retry");
        assertEquals(ChargeStatus.FAILED, client.pay("3", 4_000).status());
        assertEquals(1, gw.calls(), "paying a settled order again does not call the gateway");
    }

    @Test
    void outOfOrderAndDuplicateWebhooksNeverRegressAPaidOrder() {
        FakeGateway gw = new FakeGateway(plan(0, 1.0, 0, 0), 6);
        ResilientPaymentClient client = new ResilientPaymentClient(gw, 1);
        client.pay("5", 3_000);
        List<WebhookEvent> events = gw.drainWebhooks(); // [pending, succeeded]
        WebhookEvent pending = events.get(0);
        WebhookEvent succeeded = events.get(1);

        client.onWebhook(succeeded);
        client.onWebhook(pending);   // late, older event
        client.onWebhook(succeeded); // duplicate
        assertEquals(ChargeStatus.SUCCEEDED, client.orders().iterator().next().status());
    }

    @Test
    void gatewayRejectsAReusedKeyWithADifferentAmount() {
        FakeGateway gw = new FakeGateway(FaultPlan.none(), 7);
        gw.charge("order-1", 1_000);
        assertThrows(IllegalArgumentException.class, () -> gw.charge("order-1", 2_000));
    }
}
