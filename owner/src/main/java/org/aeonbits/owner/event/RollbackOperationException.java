/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.event;

/**
 * Indicates that operation must be rolled back.
 *
 * @author Luigi R. Viggiano
 * @since 1.0.5
 */
public class RollbackOperationException extends RollbackException {

    /** Constructs a new exception with no detail message. */
    public RollbackOperationException() {
        super();
    }

    /**
     * Constructs a new exception with the given detail message.
     *
     * @param message the detail message.
     */
    public RollbackOperationException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the given cause.
     *
     * @param cause the cause of this exception.
     */
    public RollbackOperationException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new exception with the given detail message and cause.
     *
     * @param message the detail message.
     * @param cause   the cause of this exception.
     */
    public RollbackOperationException(String message, Throwable cause) {
        super(message, cause);
    }

}
