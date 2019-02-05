package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class Issue203 {
    interface SomeConfig extends Config {
        //
    }

    @Test
    public void testCreateConfigInConcurrentSysPropModifying() throws Exception {


        ExecutorService exe = Executors.newFixedThreadPool(2);
        try {
            final AtomicBoolean running = new AtomicBoolean(true);
            Future<Void> createConfigFuture = exe.submit(new Callable<Void>() {
                @Override
                public Void call() {
                    for (int cnt = 0; cnt < 100; cnt++) {
                        System.out.println(cnt);
                        ConfigFactory.create(SomeConfig.class);
                    }
                    return null;
                }
            });

            Future<Void> changeSystemPropertyFuture = exe.submit(new Callable<Void>() {
                @Override
                public Void call() {
                    while (running.get()) {
                        System.setProperty("Foo", "Bar");
                        System.getProperties().remove("Foo");
                    }
                    return null;
                }
            });
            createConfigFuture.get();
            running.set(false);
            changeSystemPropertyFuture.get();
        }
        finally {
            exe.shutdown();
        }
    }



}
