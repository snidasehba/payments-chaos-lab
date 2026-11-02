package com.nidasehba.chaos;

/**
 * Usage: java -jar payments-chaos-lab.jar [seeds] [orders-per-run]
 * Runs the chaos experiment against both clients and prints how many runs broke an invariant.
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        int seeds = args.length > 0 ? Integer.parseInt(args[0]) : 500;
        int orders = args.length > 1 ? Integer.parseInt(args[1]) : 25;
        report("resilient", seeds, orders, ResilientPaymentClient::new);
        report("naive", seeds, orders, NaivePaymentClient::new);
    }

    private static void report(String name, int seeds, int orders,
                               java.util.function.BiFunction<FakeGateway, Integer, PaymentClient> factory) {
        int broken = 0;
        ChaosRun.Result firstBad = null;
        for (long seed = 1; seed <= seeds; seed++) {
            ChaosRun.Result r = ChaosRun.run(seed, orders, factory);
            if (!r.clean()) {
                broken++;
                if (firstBad == null) {
                    firstBad = r;
                }
            }
        }
        System.out.printf("%-9s client: %d of %d chaos runs broke an invariant%n", name, broken, seeds);
        if (firstBad != null) {
            System.out.printf("          first failing seed %d: %s%n", firstBad.seed(), firstBad.violations().get(0));
        }
    }
}
