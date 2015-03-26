/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.event;

import org.junit.Test;

/**
 * @author Luigi R. Viggiano
 */
public class ExceptionTests {

    @Test
    public void testRollbackBatchExceptionConstruction() {
        // just ensure all constructors are available on the class and the hierarchy is correct
        RollbackException ex = new RollbackBatchException();
        new RollbackBatchException("message");
        new RollbackBatchException(new Exception());
        new RollbackBatchException("message", new Exception());
    }

    @Test
    public void testRollbackOperationExceptionConstruction() {
        // just ensure all constructors are available on the class and the hierarchy is correct
        RollbackException ex = new RollbackOperationException();
        new RollbackOperationException("message");
        new RollbackOperationException(new Exception());
        new RollbackOperationException("message", new Exception());
    }

}
