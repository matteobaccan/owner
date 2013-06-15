/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.examples;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * This spike is a learning test to understand how the asynchronous reload can be implemented.
 * @author luigi
 */
public class ScheduledExecutorServiceSpike {
    public static void main(String[] args) throws InterruptedException {
        ThreadFactory tf = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                try {
                    Thread result =  new Thread(r);
                    result.setDaemon(true);
                    return result;
                } finally {
                    System.out.println("new thread created");
                }
            }
        };
        ScheduledExecutorService stp = Executors.newScheduledThreadPool(1, tf);
        stp.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                System.out.printf(".");
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
        stp.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                System.out.printf("*");
            }
        }, 0, 500, TimeUnit.MILLISECONDS);

        Thread.sleep(10000L);
    }
}
