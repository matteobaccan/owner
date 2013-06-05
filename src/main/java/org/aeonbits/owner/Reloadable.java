/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

/**
 * <p>Allows the Config object to implement reloading the properties.</p> <p/> <p>Example:</p> <p/>
 * <pre>
 *     public interface MyConfig extends Config, Reloadable {
 *         int someProperties();
 *     }
 *
 *     public void doSomething() {
 *
 *         // loads the properties from the files for the first time.
 *         MyConfig cfg = ConfigFactory.create(MyConfig.class);
 *         int before = cfg.someProperties();
 *
 *         // after changing the local files...
 *         cfg.reload();
 *         int after = cfg.someProperties();
 *
 *         // before and after may differ now.
 *         if (before != after) { ... }
 *     }
 * </pre>
 * <p/> <p>The reload method will reload the properties using the same sources used when it was instantiated the first
 * time. This can be useful to programmatically reload the configuration after the configuration files were
 * changed.</p>
 *
 * @author Luigi R. Viggiano
 * @since 1.0.4
 */
public interface Reloadable {
    /**
     * Reloads the properties using the same logic as when the object was instantiated by
     * {@link ConfigFactory#create(Class, java.util.Map[])}.
     *
     * @since 1.0.4
     */
    void reload();
}
