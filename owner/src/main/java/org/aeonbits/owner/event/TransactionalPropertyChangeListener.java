/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.event;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * A Listener that is aware of properties changes, with transactional capability.
 *
 * @since 1.0.5
 * @author Luigi R. Viggiano
 */
public interface TransactionalPropertyChangeListener extends PropertyChangeListener {

    /**
     * This method is invoked before the property is changed. When this method is invoked we cannot assume that the
     * change is effective, since some listener can ask to roll back the change operation.
     *
     * @param event the {@link PropertyChangeEvent event} of property change.
     * @throws RollbackOperationException when the listener wants to rollback the change on the property intercepted
     * @throws RollbackBatchException     when the listener wants to rollback the entire set of changes if executed in
     *                                    the batch.
     */
    void beforePropertyChange(PropertyChangeEvent event) throws RollbackOperationException, RollbackBatchException;

}
