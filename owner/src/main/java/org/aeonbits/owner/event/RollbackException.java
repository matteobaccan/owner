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

    /** Constructs a new exception with no detail message. */
    public RollbackException() {
        super();
    }

    /**
     * Constructs a new exception with the given detail message.
     *
     * @param msg the detail message.
     */
    public RollbackException(String msg) {
        super(msg);
    }

    /**
     * Constructs a new exception with the given cause.
     *
     * @param cause the cause of this exception.
     */
    public RollbackException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new exception with the given detail message and cause.
     *
     * @param message the detail message.
     * @param cause   the cause of this exception.
     */
    public RollbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
