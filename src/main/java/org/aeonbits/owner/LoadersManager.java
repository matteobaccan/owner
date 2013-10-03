/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.loaders.Loader;
import org.aeonbits.owner.loaders.PropertiesLoader;
import org.aeonbits.owner.loaders.XMLLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.aeonbits.owner.Util.unsupported;


/**
 * This class is responsible of locating an appropriate Loader for a given URL (based the extension in the resource
 * name)and load the properties from it.
 *
 * @author Luigi R. Viggiano
 * @since 1.0.5
 */
class LoadersManager implements Serializable {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<Loader> loaders = new LinkedList<Loader>();

    LoadersManager() {
        registerLoader(new PropertiesLoader());
        registerLoader(new XMLLoader());
    }

    void load(Properties result, URL url) throws IOException {
        InputStream stream = url.openStream();
        try {
            Loader loader = findLoader(url);
            loader.load(result, stream);
        } finally {
            stream.close();
        }
    }

    Loader findLoader(URL url) {
        lock.readLock().lock();
        try {
            for (Loader loader : loaders)
                if (loader.accept(url))
                    return loader;
            throw unsupported("Can't resolve a Loader for the URL %s.", url.toString());
        } finally {
            lock.readLock().unlock();
        }
    }

    final void registerLoader(Loader loader) {
        if (loader == null)
            throw new NullPointerException("loader can't be null");
        lock.writeLock().lock();
        try {
            loaders.add(0, loader);
        } finally {
            lock.writeLock().unlock();
        }
    }

    void clear() {
        lock.writeLock().lock();
        try{
            loaders.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    String[] defaultSpecs(String prefix) {
        lock.readLock().lock();
        try {
            List<String> defaultSpecs = new ArrayList<String>(loaders.size());
            for (Loader loader : loaders) {
                String spec = loader.defaultSpecFor(prefix);
                if (spec != null)
                    defaultSpecs.add(spec);
            }
            return defaultSpecs.toArray(new String[0]);
        } finally {
            lock.readLock().unlock();
        }
    }

}
