/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.multithread;

import org.aeonbits.owner.Config;

import java.lang.Thread.State;
import java.util.List;

/**
 * @author Luigi R. Viggiano
 */
abstract class MultiThreadTestBase {
    void join(ThreadBase[]... args) throws InterruptedException {
        for (ThreadBase[] threads : args)
            for (Thread thread : threads)
                thread.join();
    }

    void start(ThreadBase[]... args) throws InterruptedException {
        for (ThreadBase[] threads : args)
            for (Thread thread : threads) {
                thread.start();
                while (thread.getState() != State.WAITING)
                    // waits for all threads to be started and ready to rush
                    // when lock.notifyAll() is issued
                    thread.join(1);
            }
    }

    /**
     * Fails with what the threads collected, all of it.
     * <p>
     * Only one exception can be thrown, and a run that goes wrong under concurrency usually collects
     * several: the ones that cannot be thrown are attached to the one that can. They then travel into the
     * test report along with it, which is where somebody reading a build that failed overnight will look —
     * printing them to the console instead leaves the evidence in a place nothing keeps.
     * </p>
     */
    <T extends Config> void assertNoErrors(ThreadBase<T>[] threads) throws Throwable {
        for (ThreadBase<T> thread : threads) {
            List<Throwable> errors = thread.errors;
            if (errors.isEmpty())
                continue;

            Throwable failure = errors.get(0);
            for (Throwable other : errors.subList(1, errors.size()))
                // the same instance twice would make addSuppressed throw, and that exception would then be
                // the one reported instead of the failure it was meant to describe
                if (other != failure)
                    failure.addSuppressed(other);
            throw failure;
        }
    }

    void notifyAll(Object lock) {
        synchronized (lock) {
            lock.notifyAll();
        }
    }
}
