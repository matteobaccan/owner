/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.ConfigCache;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Mutable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CyclicBarrier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * See: https://github.com/matteobaccan/owner/issues/283
 * <p>
 * suankan asked in 2021 whether {@link ConfigCache#getOrCreate} could be made thread-isolated: wrapping it
 * in a {@link ThreadLocal} handed every thread the same object, and one thread's
 * {@link Mutable#setProperty} was visible to the other.
 * </p>
 * <p>
 * It is by design, and the word that misleads is <b>thread safe</b>: the cache may be read and written
 * from several threads at once, which is not the same as holding one instance per thread. The default
 * {@code getOrCreate(MyConfig.class)} keys the cache <b>by the class</b>, so there is one instance for the
 * JVM &mdash; and a {@code ThreadLocal} whose supplier returns that shared instance hands the same object
 * to everybody, whatever the {@code ThreadLocal} does.
 * </p>
 * <p>
 * What was asked for needs no new API and has been there since 1.0.6: <b>the id is any object</b>, so an
 * id per thread is a cached instance per thread. The one thing it does not do is forget &mdash; the cache
 * outlives the thread that filled it, which is measured below, and is why a {@code ThreadLocal} over
 * {@link ConfigFactory#create} remains the right shape when the threads come and go.
 * </p>
 *
 * @author Matteo Baccan
 */
public class Issue283Test {

    public interface MyConfig extends Mutable {
        @DefaultValue("origValue")
        String prop();
    }

    /** A {@link ThreadLocal} over the cache: suankan's code, as a field, which is the fair version of it. */
    private static final ThreadLocal<MyConfig> OVER_THE_CACHE =
            ThreadLocal.withInitial(() -> ConfigCache.getOrCreate(MyConfig.class));

    /** A {@link ThreadLocal} over the factory: one instance per thread, and nothing kept anywhere else. */
    private static final ThreadLocal<MyConfig> OVER_THE_FACTORY =
            ThreadLocal.withInitial(() -> ConfigFactory.create(MyConfig.class));

    @Before
    public void emptyTheCache() {
        ConfigCache.clear();
    }

    @After
    public void emptyItAgain() {
        ConfigCache.clear();
    }

    /** What the two threads do, in step with each other so that nothing below has to race to be true. */
    private interface Body {
        Object run(CyclicBarrier barrier) throws Exception;
    }

    /** Runs the body in two threads named worker-0 and worker-1, and returns what each of them saw. */
    private List<Object> inTwoThreads(final Body body) throws Exception {
        final CyclicBarrier barrier = new CyclicBarrier(2);
        final Object[] seen = new Object[2];
        final Throwable[] failed = new Throwable[2];
        Thread[] threads = new Thread[2];
        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    seen[index] = body.run(barrier);
                } catch (Throwable uncaught) {
                    failed[index] = uncaught;
                }
            }, "worker-" + i);
        }
        for (Thread thread : threads) thread.start();
        for (Thread thread : threads) thread.join(30000L);
        for (Throwable uncaught : failed)
            if (uncaught != null) throw new AssertionError("the worker threw", uncaught);
        return Arrays.asList(seen);
    }

    /** The report itself: the same object in both threads, because the id is the class. */
    @Test
    public void theCacheIsKeyedByTheClassSoBothThreadsGetTheSameObject() throws Exception {
        List<Object> instances = inTwoThreads(barrier -> ConfigCache.getOrCreate(MyConfig.class));

        assertSame(instances.get(0), instances.get(1));
    }

    /** And a ThreadLocal over it changes nothing: what it caches per thread is the one shared instance. */
    @Test
    public void aThreadLocalOverTheCacheChangesNothing() throws Exception {
        List<Object> instances = inTwoThreads(barrier -> OVER_THE_CACHE.get());

        assertSame(instances.get(0), instances.get(1));
        assertSame(ConfigCache.getOrCreate(MyConfig.class), instances.get(0));
    }

    /** Which is why one thread's write was the other thread's read. */
    @Test
    public void soWhatOneThreadWritesTheOtherReads() throws Exception {
        List<Object> seen = inTwoThreads(barrier -> {
            MyConfig config = ConfigCache.getOrCreate(MyConfig.class);
            if ("worker-0".equals(Thread.currentThread().getName()))
                config.setProperty("prop", "written by worker-0");
            barrier.await();
            return config.prop();
        });

        assertEquals("written by worker-0", seen.get(0));
        assertEquals("the other thread reads the same instance", "written by worker-0", seen.get(1));
    }

    /**
     * The answer, and it needs no new API: the id is any object, so an id per thread is an instance per
     * thread. Each worker writes its own name and reads its own name back, after both have written.
     */
    @Test
    public void anIdPerThreadIsAnInstancePerThread() throws Exception {
        List<Object> seen = inTwoThreads(barrier -> {
            String id = Thread.currentThread().getName();
            MyConfig config = ConfigCache.getOrCreate(id, MyConfig.class);
            config.setProperty("prop", id);
            barrier.await();
            return config.prop();
        });

        assertEquals("worker-0", seen.get(0));
        assertEquals("worker-1", seen.get(1));
        assertNotSame(ConfigCache.get("worker-0"), ConfigCache.get("worker-1"));
    }

    /**
     * The catch, said out loud: the cache is a cache, not a thread-local. Both instances are still in it
     * once the threads that asked for them are dead, and only the caller can decide when they may go.
     */
    @Test
    public void butTheCacheOutlivesTheThreadsThatFilledIt() throws Exception {
        inTwoThreads(barrier -> ConfigCache.getOrCreate(Thread.currentThread().getName(), MyConfig.class));

        assertTrue(ConfigCache.list().toString(), ConfigCache.list().contains("worker-0"));
        assertTrue(ConfigCache.list().toString(), ConfigCache.list().contains("worker-1"));

        ConfigCache.remove("worker-0");
        ConfigCache.remove("worker-1");
        assertTrue(ConfigCache.list().toString(), ConfigCache.list().isEmpty());
    }

    /**
     * So when the threads come and go, put the ThreadLocal over the factory instead of over the cache:
     * isolated the same way, and it is collected with the thread rather than kept for the JVM's life.
     */
    @Test
    public void aThreadLocalOverTheFactoryIsTheOtherShape() throws Exception {
        List<Object> seen = inTwoThreads(barrier -> {
            MyConfig config = OVER_THE_FACTORY.get();
            config.setProperty("prop", Thread.currentThread().getName());
            barrier.await();
            return config.prop() + " from " + System.identityHashCode(config);
        });

        assertTrue(seen.get(0).toString(), seen.get(0).toString().startsWith("worker-0 from "));
        assertTrue(seen.get(1).toString(), seen.get(1).toString().startsWith("worker-1 from "));
        assertTrue("nothing was cached", ConfigCache.list().isEmpty());
    }

    /**
     * And the trap underneath the report, which no library can help with: the ThreadLocal in the issue was
     * built <b>inside</b> the method, so it was a new ThreadLocal on every call. Over the factory &mdash;
     * where the supplier does return a fresh object &mdash; that is a new instance per call, in one single
     * thread.
     */
    @Test
    public void aThreadLocalBuiltInsideTheMethodIsPerCallAndNotPerThread() {
        assertNotSame(perCall(), perCall());
    }

    private MyConfig perCall() {
        ThreadLocal<MyConfig> local = ThreadLocal.withInitial(() -> ConfigFactory.create(MyConfig.class));
        return local.get();
    }
}
