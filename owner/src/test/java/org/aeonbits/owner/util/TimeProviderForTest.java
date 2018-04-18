/*
 * Copyright (c) 2012-2018, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import org.aeonbits.owner.util.Util.TimeProvider;

import java.util.concurrent.TimeUnit;

/**
 * @author Luigi R. Viggiano
 */
public class TimeProviderForTest implements TimeProvider {

    private TimeProvider backup;

    private long time;

    public TimeProviderForTest() {
        this.time = System.currentTimeMillis();
    }

    public void elapse(long interval, TimeUnit unit) {
        time += unit.toMillis(interval);
    }

    public long getTime() {
        return time;
    }

    public void setup() {
        backup = Util.timeProvider;
        Util.timeProvider = this;
    }

    public void tearDown() {
        Util.timeProvider = backup;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
