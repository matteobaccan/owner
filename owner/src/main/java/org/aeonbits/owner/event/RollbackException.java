/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.event;

/**
 * Superclass for event rollback.
 *
 * @author Luigi R. Viggiano
 * @since 1.0.5
 */
public abstract class RollbackException extends Exception {

    public RollbackException() {
        super();
    }

    public RollbackException(String msg) {
        super(msg);
    }

    public RollbackException(Throwable cause) {
        super(cause);
    }

    public RollbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
