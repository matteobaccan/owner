/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package sun.net.www.protocol.zookeeper;

import org.apache.curator.RetryPolicy;
import org.apache.curator.RetrySleeper;

/**
 * Zookeper retry policy that doesn't do any retry.
 *
 * @author Koray Sariteke
 * @author Luigi R. Viggiano
 */
public class NoRetryPolicy implements RetryPolicy {
    public boolean allowRetry(int retryCount, long elapsedTimeMs, RetrySleeper sleeper) {
        return false;
    }
}
