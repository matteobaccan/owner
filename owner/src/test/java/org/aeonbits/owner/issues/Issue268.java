package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import static java.lang.Thread.sleep;
import static org.junit.Assert.assertFalse;

/**
 * See: https://github.com/lviggiano/owner/issues/268
 */
public class Issue268 {

    interface MyConfig extends Config {
        @DefaultValue("Pasha")
        String firstName();

        @DefaultValue("Bairov")
        String lastName();
    }

    // A starter flag 🏁 to make sure a group of threads start at the same time
    static class Starter {
        private final int players;
        private volatile int count = 0;

        Starter(int players) {
            this.players = players;
        }

        synchronized void ready() {
            try {
                count++;
                wait();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        void set() throws InterruptedException {
            while (count != players) {
                sleep(20);
            }
        }

        synchronized void go() {
            notifyAll();
        }

    }

    @Test
    public void testConcurrentAccess() throws InterruptedException {
        final MyConfig cfg = ConfigFactory.create(MyConfig.class);

        final Starter starter = new Starter(2);

        final boolean[] nameMismatch = {false};
        final boolean[] lastNameMismatch = {false};
        final int iterations = 1000;

        Thread t1 = new Thread() {
            @Override
            public void run() {
                starter.ready();
                for (int i = 0; i < iterations && !nameMismatch[0] && !lastNameMismatch[0]; i++)
                    if (!cfg.firstName().equals("Pasha"))
                        nameMismatch[0] = true;
            }
        };

        Thread t2 = new Thread() {
            @Override
            public void run() {
                starter.ready();
                for (int i = 0; i < iterations && !nameMismatch[0] && !lastNameMismatch[0]; i++)
                    if (!cfg.lastName().equals("Bairov"))
                        lastNameMismatch[0] = true;
            }
        };

        t1.start();
        t2.start();

        starter.set();
        starter.go();

        t1.join();
        t2.join();

        assertFalse("mismatch on name", nameMismatch[0]);
        assertFalse("mismatch on lastName", lastNameMismatch[0]);
    }
}
