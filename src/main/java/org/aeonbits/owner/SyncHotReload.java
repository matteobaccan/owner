/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.LoadType;
import org.aeonbits.owner.Config.Sources;

import static org.aeonbits.owner.Config.LoadType.FIRST;
import static org.aeonbits.owner.Util.now;

/**
 * @author Luigi R. Viggiano
 */
class SyncHotReload {
    private final ConfigURLStreamHandler handler;
    private final PropertiesManager manager;
    private final long interval;
    private final Sources sources;
    private final LoadType loadType;
    private volatile long lastCheckTime = 0L;

    SyncHotReload(Class<? extends Config> clazz, ConfigURLStreamHandler handler, PropertiesManager manager) {
        this.handler = handler;
        this.manager = manager;
        sources = clazz.getAnnotation(Sources.class);
        LoadPolicy loadPolicy = clazz.getAnnotation(LoadPolicy.class);
        loadType = (loadPolicy != null) ? loadPolicy.value() : FIRST;

        HotReload hotReload = clazz.getAnnotation(HotReload.class);
        interval = (hotReload != null) ? hotReload.unit().toMillis(hotReload.value()) : 0;
    }

    void init(long lastLoadTime) {
        if (lastCheckTime == 0L)
            lastCheckTime = lastLoadTime;
    }

    void checkAndReload(long lastLoadTime) {
        if (needsReload(lastLoadTime))
            manager.reload();
    }

    private synchronized boolean needsReload(long lastLoadTime) {
        if (manager.loading) return false;

        long now = now();
        if (now < lastCheckTime + interval)
            return false;

        try {
            return loadType.needsReload(sources, handler, lastLoadTime);
        } finally {
            lastCheckTime = now;
        }
    }

}
