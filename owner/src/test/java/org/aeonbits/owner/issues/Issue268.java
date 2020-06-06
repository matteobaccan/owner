package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import static java.lang.Thread.sleep;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;

// Issue #268
public class Issue268 {
    interface MyConfig extends Config {
        @DefaultValue("Pasha")
        String firstName();
        @DefaultValue("Bairov")
        String lastName();
    }

    @Test
    public void testConcurrentAccess() throws InterruptedException {
        final MyConfig cfg = ConfigFactory.create(MyConfig.class);

        final Object semaphore = new Object();

        final boolean[] exit = {false};
        final boolean[] nameMismatch = { false };
        final boolean[] lastNameMismatch = { false };
        final boolean[] interrupted = {false, false};

        Thread t1 = new Thread() {
            @Override
            public void run() {
                try {
                    synchronized (semaphore) {
                        semaphore.wait();
                    }
                    for (int i = 0; i < 1000 && ! exit[0]; i++) {
                        String name = cfg.firstName();

                        if (!name.equals("Pasha")) {
                            System.out.println("name " + name);
                            nameMismatch[0] = true;
                            exit[0] = true;
                        }
                    }
                } catch (InterruptedException e) {
                    interrupted[0] = true;
                    throw new IllegalStateException();
                }
            }
        };

        Thread t2 = new Thread() {
            @Override
            public void run() {
                try {
                    synchronized (semaphore) {
                        semaphore.wait();
                    }
                    for (int i = 0; i < 1000 &&! exit[0] ; i++) {
                        String lastName = cfg.lastName();
                        if (!lastName.equals("Bairov")) {
                            System.out.println("lastName " + lastName);
                            lastNameMismatch[0] = true;
                            exit[0] = true;
                        }
                    }
                } catch (InterruptedException e) {
                    interrupted[1] = true;
                    throw new IllegalStateException();
                }
            }
        };
        t1.start();
        t2.start();

        sleep(300);

        synchronized (semaphore) {
            semaphore.notifyAll();
        }

        t1.join();
        t2.join();
        assertFalse("mismatch on name", nameMismatch[0]);
        assertFalse("mismatch on lastName", lastNameMismatch[0]);
        assertArrayEquals(new boolean[] {false, false}, interrupted);
    }
}
