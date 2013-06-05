package org.aeonbits.owner.multithread;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.UtilTest.MyCloneable;

import java.util.ArrayList;
import java.util.List;

import static org.aeonbits.owner.UtilTest.debug;

abstract class ThreadBase<T extends Config> extends Thread implements MyCloneable {
    private static long counter = 0;
    final long uniqueThreadId = ++counter;
    final T cfg;
    final Object lock;
    final int loops;
    final List<Throwable> errors;

    ThreadBase(T cfg, Object lock, int loops) {
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
