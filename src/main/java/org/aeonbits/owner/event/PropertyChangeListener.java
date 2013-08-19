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
 * @author Luigi R. Viggiano
 */
public interface PropertyChangeListener extends java.beans.PropertyChangeListener {

    public void beforePropertyChange(PropertyChangeEvent evt) throws RollbackPropertyChangeException,
            RollbackReloadException;


    public void propertyChange(PropertyChangeEvent evt);
}
