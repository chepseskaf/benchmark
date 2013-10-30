package org.goinfre.java;


import org.goinfre.java.api.Implementation;
import org.goinfre.java.api.Listener;
import org.goinfre.java.api.ListenerManager;
import org.goinfre.java.core.DefaultListener;
import org.goinfre.java.core.HashSetImplementation;
import org.goinfre.java.core.SynchronizedHashSetImplementation;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class BenchmarkRunner {
    private static final long WARMUP_MSECS = TimeUnit.SECONDS.toMillis(5);
    private static final int TRIALS = 10;
    private static final int ITERATIONS = 1000;
    private final Set<Implementation<Listener>> tests = new LinkedHashSet<Implementation<Listener>>();

    public static void main(String[] args) throws Exception {
        final BenchmarkRunner benchmarkRunner = new BenchmarkRunner();

        benchmarkRunner.addHashsetTest(new HashSetImplementation());
        benchmarkRunner.addHashsetTest(new SynchronizedHashSetImplementation());

        benchmarkRunner.run();
    }

    private void addHashsetTest(Implementation<Listener> implementation) {
        tests.add(implementation);
    }

    /**
     * JVM is not required to honor GC requests, but adding bit of sleep around request is
     * most likely to give it a chance to do it.
     */
    private void doGc() {
        try {
            Thread.sleep(50L);
        } catch (InterruptedException ie) { /* nothing to do */ }
        System.gc();
        try { // longer sleep afterwards (not needed by GC, but may help with scheduling)
            Thread.sleep(200L);
        } catch (InterruptedException ie) { /* nothing to do */ }
    }

    private void run() throws Exception {
        final EnumMap<measurements, Map<String, Double>> values = new EnumMap<measurements, Map<String, Double>>(measurements.class);
        for (measurements m : measurements.values()) {
            values.put(m, new HashMap<String, Double>());
        }


        for (Implementation<Listener> implementation : tests) {
            /**
             * Should only warm things for the serializer that we test next: HotSpot JIT will
             * otherwise spent most of its time optimizing slower ones... Use
             * -XX:CompileThreshold=1 to hint the JIT to start immediately
             *
             * Actually: 1 is often not a good value -- threshold is the number
             * of samples needed to trigger inlining, and there's no point in
             * inlining everything. Default value is in thousands, so lowering
             * it to, say, 1000 is usually better.
             */
            warmup_add_remove(implementation);
            doGc();

            double simpleTime = Double.MAX_VALUE;
            // do more iteration for object creation because of its short time
            for (int i = 0; i < TRIALS; i++) {
                simpleTime = Math.min(simpleTime, add_remove(implementation, ITERATIONS));
            }


            warmup_concurrent_add_remove(implementation);
            doGc();

            double useCaseTime = Double.MAX_VALUE;
            // do more iteration for object creation because of its short time
            for (int i = 0; i < TRIALS; i++) {
                useCaseTime = Math.min(useCaseTime, concurrent_add_remove(implementation, ITERATIONS));
            }


            final String name = implementation.name();
            values.get(measurements.simple).put(name, simpleTime);


            print(implementation, simpleTime, useCaseTime);
        }

    }

    private void print(Implementation<Listener> implementation,
                       double simpleTime,
                       double useCaseTime) {
        System.out.printf("%-24s: %15.5f, %15.5f, %10d\n",
                implementation.name(),
                simpleTime,
                useCaseTime, 0
        );
    }

    private void warmup_concurrent_add_remove(Implementation<Listener> implementation) throws ExecutionException, InterruptedException {
        for (int i = 0; i < 5; i++) {
            concurrent_add_remove(implementation, 1);
        }
    }

    private double concurrent_add_remove(Implementation<Listener> implementation, final int iterations) throws ExecutionException, InterruptedException {
        final Queue<Listener> listeners = new ConcurrentLinkedQueue<Listener>();

        final ListenerManager<Listener> manager = implementation.init();

        final String name = implementation.name();
        final ThreadGroup group = new ThreadGroup(name + ":" + iterations);
        final ThreadFactory factory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(group, r, "Thread-" + name);
            }
        };

        final ExecutorService pool = Executors.newFixedThreadPool(1, factory);

        /*
        final Thread registering = factory.newThread(new Runnable() {
            @Override
            public void run() {
                final Listener listener = createListener();
                implementation.register(listener);
                listeners.offer(listener);
                Thread.yield();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        registering.start();

        final Thread unregistering = factory.newThread(new Runnable() {
            @Override
            public void run() {
                final Listener listener = listeners.poll();
                implementation.unregister(listener);
                Thread.yield();
            }
        });
        unregistering.start(); */

        final Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                final Listener listener = createListener();
                manager.register(listener);
                System.out.println("register listener = " + listener);
                listeners.offer(listener);
                Thread.yield();
            }
        }, 1000, 1000);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                final Listener listener = listeners.poll();
                System.out.println("unregister listener = " + listener);
                manager.unregister(listener);
                Thread.yield();
            }
        }, 1000, 5000);


        timer.cancel();


        return pool.submit(new Callable<Double>() {
            @Override
            public Double call() throws Exception {
                try {
                    final long start = System.nanoTime();
                    for (int i = 0; i < iterations; i++) {
                        manager.fireNotification();
                    }
                    return meanTime(System.nanoTime() - start, iterations);
                } finally {
                    //registering.cancel(true);
                    //unregistering.scancel(true);
                    timer.cancel();
                    listeners.clear();
                }
            }
        }).get();

    }

    private void warmup_add_remove(Implementation<Listener> implementation) throws Exception {
        // Instead of fixed counts, let's try to prime by running for N seconds
        long endTime = System.currentTimeMillis() + WARMUP_MSECS;
        do {
            add_remove(implementation, 1);
        }
        while (System.currentTimeMillis() < endTime);
    }

    private double add_remove(Implementation<Listener> implementation, int iterations) {
        final long start = System.nanoTime();
        final ListenerManager<Listener> manager = implementation.init();
        for (int i = 0; i < iterations; i++) {
            final Listener listener = createListener();
            manager.register(listener);
            manager.fireNotification();
            manager.unregister(listener);
        }
        return meanTime(System.nanoTime() - start, iterations);
    }

    private Listener createListener() {
        return new DefaultListener();
    }

    private double meanTime(long delta, int iterations) {
        return (double) delta / (double) (iterations);
    }

    enum measurements {
        simple,
    }
}
