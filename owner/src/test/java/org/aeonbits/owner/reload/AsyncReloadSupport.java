/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.reload;


/**
 * @author luigi
 */
class AsyncReloadSupport {
    private final Object reloadLock = new Object();

    void waitForReload(final long timeout) throws InterruptedException {
        synchronized (reloadLock) {
            reloadLock.wait(timeout);
        }
    }

    void notifyReload() {
        synchronized (reloadLock) {
            reloadLock.notifyAll();
        }
    }

}
