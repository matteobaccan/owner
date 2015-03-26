/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
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
 * @author Luigi R. Viggiano
 */
public class ScheduledExecutorServiceSpike {
    public static void main(String[] args) throws InterruptedException {
        ThreadFactory tf = new ThreadFactory() {
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
        ScheduledExecutorService stp = Executors.newSingleThreadScheduledExecutor(tf);
        stp.scheduleAtFixedRate(new Runnable() {
            int count = 0;
            public void run() {
                if ( count++ % 2 == 0)
                    System.out.printf("*");
            }
        }, 500, 500, TimeUnit.MILLISECONDS);

        stp.scheduleAtFixedRate(new Runnable() {
            int count = 0;
            public void run() {
                ++count;
                if (count != 5 && count != 10)
                System.out.printf(".");
                if (count == 10) count = 0;
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
        stp.scheduleAtFixedRate(new Runnable() {
            int count = 0;
            public void run() {
                System.out.print(++count);
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);

        Thread.sleep(10000L);
    }
}
