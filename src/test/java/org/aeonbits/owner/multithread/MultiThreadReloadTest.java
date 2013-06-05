/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.multithread;

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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Properties;

import static org.aeonbits.owner.UtilTest.newArray;
import static org.aeonbits.owner.UtilTest.save;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Luigi R. Viggiano
 */
public class MultiThreadReloadTest {
    private static final String spec = "file:target/test-resources/ReloadableConfig.properties";
    private static File target;
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

    private void throwErrorIfAny(ThreadBase[]... args) throws Throwable {
        for (ThreadBase[] threads : args)
            for (int i = 0; i < threads.length; i++) {
                ThreadBase thread = threads[i];

                int errorCount = thread.errors.size();

                if (errorCount > 0)
                    System.err.printf("There are %d exception collected by %s#%d\n", errorCount,
                            thread.getClass().getName(), i);

                List<Throwable> errors = thread.errors;
                for (Throwable error : errors) {
                    System.err.printf("%s#%d thrown an exception: %s\n", thread.getClass().getName(), i,
                            error.getMessage());
                    error.printStackTrace(System.err);
                    throw error;
                }
            }
    }

    private class ReaderThread extends ThreadBase<ReloadableConfig> {
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

    private class WriterThread extends ThreadBase<ReloadableConfig> {
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
