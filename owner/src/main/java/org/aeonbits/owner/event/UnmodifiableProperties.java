/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.event;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableSet;

/**
 * @author Luigi R. Viggiano
 */
class UnmodifiableProperties extends Properties {

    public UnmodifiableProperties(Properties properties) {
        fill(properties);
    }

    private void fill(Map<?, ?> t) {
        for (Map.Entry<?, ?> e : t.entrySet())
            super.put(e.getKey(), e.getValue());
    }

    @Override
    public synchronized Object put(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized Object remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Object> keySet() {
        return unmodifiableSet(super.keySet());
    }

    @Override
    public Set<Entry<Object, Object>> entrySet() {
        return unmodifiableSet(super.entrySet());
    }

    @Override
    public Collection<Object> values() {
        return unmodifiableCollection(super.values());
    }

}
