/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
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

    <T extends Config> void assertNoErrors(ThreadBase<T>[] threads) throws Throwable {
        for (int i = 0; i < threads.length; i++) {
            ThreadBase<T> thread = threads[i];

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

    void notifyAll(Object lock) {
        synchronized (lock) {
            lock.notifyAll();
        }
    }
}
