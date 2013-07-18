/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Util.Time;

import java.util.concurrent.TimeUnit;

/**
 * @author Luigi R. Viggiano
 */
public class TimeForTest implements Time {

    private Time backup;

    private long time;

    public TimeForTest() {
        this.time = System.currentTimeMillis();
    }

    public void elapse(long interval, TimeUnit unit) {
        time += unit.toMillis(interval);
    }

    public long getTime() {
        return time;
    }

    public void setup() {
        backup = Util.time;
        Util.time = this;
    }

    public void tearDown() {
        Util.time = backup;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
