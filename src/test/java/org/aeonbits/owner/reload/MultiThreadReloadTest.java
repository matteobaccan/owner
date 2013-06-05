/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.reload;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Reloadable;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.lang.Thread.State;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.aeonbits.owner.UtilTest.save;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Luigi R. Viggiano
 */
public class MultiThreadReloadTest {
    private static final String spec = "file:target/test-resources/ReloadableConfig.properties";
    private static File target;
    private int uniqueThreadId = 0;
    private boolean debug = false;
    private ReloadableConfig reloadableConfig;

    @BeforeClass
    public static void beforeClass() throws MalformedURLException {
        target = new File(new URL(spec).getFile());
    }

    @Before
    public void before() throws Throwable {
        synchronized (target) {
            save(target, new Properties() {{
                setProperty("someValue", "10");
            }});

            reloadableConfig = ConfigFactory.create(ReloadableConfig.class);
        }
    }

    @Sources(spec)
    public interface ReloadableConfig extends Config, Reloadable {
        Integer someValue();
    }

    @Test
    public void multiThreadedReloadTest() throws Throwable {
        Object lock = new Object();

        ReaderThread[] readers = newArray(20, new ReaderThread(reloadableConfig, lock, 100));
        WriterThread[] writers = newArray(5, new WriterThread(reloadableConfig, lock, 70));

        start(readers, writers);

        synchronized (lock) {
            lock.notifyAll();
        }

        join(readers, writers);

        throwErrorIfAny(readers, writers);
    }

    private void join(ThreadBase[]... args) throws InterruptedException {
        for (ThreadBase[] threads : args)
            for (Thread thread : threads)
                thread.join();
    }

    private void start(ThreadBase[]... args) throws InterruptedException {
        for (ThreadBase[] threads : args)
            for (Thread thread : threads) {
                thread.start();
                while (thread.getState() != State.WAITING)
                    // waits for all threads to be started and ready to rush
                    // when lock.notifyAll() is issued
                    thread.join(1);
            }
    }

    interface MyCloneable extends Cloneable {
        // for some stupid reason java.lang.Cloneable doesn't define this method...
        public Object clone() throws CloneNotSupportedException;
    }

    public <T extends MyCloneable> T[] newArray(int size, T cloneable) throws CloneNotSupportedException {
        Object array = Array.newInstance(cloneable.getClass(), size);
        Array.set(array, 0, cloneable);
        for (int i = 1; i < size; i++)
            Array.set(array, i, cloneable.clone());
        return (T[]) array;
    }

    private void throwErrorIfAny(ThreadBase[]... args) throws Throwable {
        for (ThreadBase[] threads : args)
            for (int i = 0; i < threads.length; i++) {
                ThreadBase thread = threads[i];

                int errorCount = thread.errors.size();

                if (errorCount > 0)
                    System.err.printf("There are %d exception collected by %s#%d\n", errorCount,
                            thread.getClass().getName(), i);

                for (Throwable error : thread.errors) {
                    System.err.printf("%s#%d thrown an exception: %s\n", thread.getClass().getName(), i,
                            error.getMessage());
                    error.printStackTrace(System.err);
                    throw error;
                }
            }
    }

    private abstract class ThreadBase extends Thread implements MyCloneable {
        final int uniqueThreadId = ++MultiThreadReloadTest.this.uniqueThreadId;
        final ReloadableConfig cfg;
        final Object lock;
        final int loops;
        final List<Throwable> errors;

        ThreadBase(ReloadableConfig cfg, Object lock, int loops) {
            this.cfg = cfg;
            this.lock = lock;
            this.loops = loops;
            this.errors = new ArrayList<Throwable>();
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }

        @Override
        public void run() {
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            for (int i = 0; i < loops; i++) {
                debug("%s[%d] started loop #%d.\n", getClass().getName(), uniqueThreadId, i);
                try {
                    execute();
                } catch (Throwable throwable) {
                    debug("%s[%d] thrown an error in loop #%d.\n", getClass().getName(), uniqueThreadId, i);
                    errors.add(throwable);
                }
                yield();
                debug("%s[%d] completed loop #%d.\n", getClass().getName(), uniqueThreadId, i);
            }
        }

        abstract void execute() throws Throwable;
    }

    void debug(String format, Object... args) {
        if (debug)
            System.out.printf(format, args);
    }


    private class ReaderThread extends ThreadBase {
        ReaderThread(ReloadableConfig cfg, Object lock, int loops) {
            super(cfg, lock, loops);
        }

        @Override
        void execute() throws Throwable {
            yield();
            Integer value = cfg.someValue();
            assertNotNull(value);
            assertTrue(value == 10 || value == 20);
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return new ReaderThread(cfg, lock, loops);
        }
    }

    private class WriterThread extends ThreadBase {
        public WriterThread(ReloadableConfig cfg, Object lock, int loops) {
            super(cfg, lock, loops);
        }

        @Override
        void execute() throws Throwable {
            synchronized (target) {
                save(target, new Properties() {{
                    setProperty("someValue", "20");
                }});

                cfg.reload();
            }
            yield();

            synchronized (target) {
                save(target, new Properties() {{
                    setProperty("someValue", "10");
                }});

                cfg.reload();
            }
            yield();
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return new WriterThread(cfg, lock, loops);
        }
    }

    @After
    public void after() throws Throwable {
        synchronized (target) {
            target.delete();
        }
    }
}
