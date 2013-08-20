/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.event;

import java.beans.PropertyChangeEvent;

/**
 * A Listener that is aware of properties changes.
 *
 * @author Luigi R. Viggiano
 */
public interface PropertyChangeListener extends java.beans.PropertyChangeListener {

    /**
     * This method is invoked before the property is changed.
     *
     * @param evt the {@link PropertyChangeEvent event} of property change
     * @throws RollbackOperationException  when the listener wants to rollback the change of this property
     * @throws RollbackBatchException when the listener wants to rollback the entire set of changes if
     *         executed in a batch.
     */
    public void beforePropertyChange(PropertyChangeEvent evt) throws RollbackOperationException, RollbackBatchException;


    public void propertyChange(PropertyChangeEvent evt);
}
