/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.event;

/**
 * A Listener that is aware of properties reloads, with transactional capability.
 *
 * @since 1.0.5
 * @author Luigi R. Viggiano
 */
public interface TransactionalReloadListener extends ReloadListener {

    /**
     * This method is invoked before the property are reloaded. When this method is invoked we cannot assume that the
     * changes are effective, since some listener can ask to roll back the change.
     *
     * @param event the {@link ReloadEvent event} of property reload.
     * @throws RollbackBatchException     when the listener wants to rollback the entire reload.
     */
    void beforeReload(ReloadEvent event) throws RollbackBatchException;

}
