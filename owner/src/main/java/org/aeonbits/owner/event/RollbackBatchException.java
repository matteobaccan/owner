/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.event;

/**
 * Indicates that whole batch of event must be rolled back.
 * A batch is intended as a group of operations are executed in a row.
 * For instance this happens when the whole list of properties is reloaded or cleared.
 *
 * @author Luigi R. Viggiano
 * @since 1.0.5
 */
public class RollbackBatchException extends RollbackException {

    /** Constructs a new exception with no detail message. */
    public RollbackBatchException() {
        super();
    }

    /**
     * Constructs a new exception with the given detail message.
     *
     * @param message the detail message.
     */
    public RollbackBatchException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the given cause.
     *
     * @param cause the cause of this exception.
     */
    public RollbackBatchException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new exception with the given detail message and cause.
     *
     * @param message the detail message.
     * @param cause   the cause of this exception.
     */
    public RollbackBatchException(String message, Throwable cause) {
        super(message, cause);
    }

}
