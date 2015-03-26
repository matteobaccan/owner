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

    public RollbackBatchException() {
        super();
    }

    public RollbackBatchException(String message) {
        super(message);
    }

    public RollbackBatchException(Throwable cause) {
        super(cause);
    }

    public RollbackBatchException(String message, Throwable cause) {
        super(message, cause);
    }

}
