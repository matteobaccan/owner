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

    public RollbackOperationException() {
        super();
    }

    public RollbackOperationException(String message) {
        super(message);
    }

    public RollbackOperationException(Throwable cause) {
        super(cause);
    }

    public RollbackOperationException(String message, Throwable cause) {
        super(message, cause);
    }

}
