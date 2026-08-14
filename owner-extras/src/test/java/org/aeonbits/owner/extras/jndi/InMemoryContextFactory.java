/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.jndi;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A JNDI provider that lives in a map, so that this loader can be tested without a container.
 * <p>
 * Registered by setting {@link Context#INITIAL_CONTEXT_FACTORY} to this class, which is the standard way
 * in and the reason no dependency is needed to test JNDI. Only the three methods the loader and the
 * handler use are implemented — <code>lookup</code>, <code>listBindings</code> and <code>close</code>;
 * everything else throws, so a test that starts relying on more fails loudly instead of quietly reading
 * something this class invented.
 * </p>
 *
 * @author Matteo Baccan
 */
public class InMemoryContextFactory implements InitialContextFactory {

    /** What is bound, by full name. A value that is a map is a subcontext. */
    static final Map<String, Object> BINDINGS = new LinkedHashMap<>();

    /** The environment the last {@link #getInitialContext(Hashtable)} was given, for the tests about it. */
    static Hashtable<?, ?> lastEnvironment;

    static void reset() {
        BINDINGS.clear();
        lastEnvironment = null;
    }

    static void bind(String name, Object value) {
        BINDINGS.put(name, value);
    }

    @Override
    public Context getInitialContext(Hashtable<?, ?> environment) {
        lastEnvironment = environment;
        return new MapContext(BINDINGS);
    }

    /** A context over a map: names are looked up whole, and a nested map is a subcontext. */
    static class MapContext extends UnsupportedContext {
        private final Map<String, Object> entries;

        MapContext(Map<String, Object> entries) {
            this.entries = entries;
        }

        @Override
        public Object lookup(String name) throws NamingException {
            if (name == null || name.isEmpty())
                return this;
            if (!entries.containsKey(name))
                throw new NameNotFoundException(name);
            return wrap(entries.get(name));
        }

        @Override
        @SuppressWarnings("unchecked")
        public NamingEnumeration<Binding> listBindings(String name) throws NamingException {
            Map<String, Object> target = entries;
            if (name != null && !name.isEmpty()) {
                Object bound = lookup(name);
                if (!(bound instanceof MapContext))
                    throw new NamingException(name + " is not a context");
                target = ((MapContext) bound).entries;
            }
            List<Binding> bindings = new ArrayList<>();
            for (Map.Entry<String, Object> entry : target.entrySet())
                bindings.add(new Binding(entry.getKey(), wrap(entry.getValue())));
            return new ListEnumeration(bindings);
        }

        @Override
        public void close() {
            // nothing to release
        }

        @SuppressWarnings("unchecked")
        private static Object wrap(Object value) {
            return value instanceof Map ? new MapContext((Map<String, Object>) value) : value;
        }
    }

    /** The little of {@link NamingEnumeration} the loader walks. */
    static class ListEnumeration implements NamingEnumeration<Binding> {
        private final Iterator<Binding> iterator;

        ListEnumeration(List<Binding> bindings) {
            this.iterator = bindings.iterator();
        }

        @Override
        public boolean hasMore() {
            return iterator.hasNext();
        }

        @Override
        public Binding next() {
            return iterator.next();
        }

        @Override
        public boolean hasMoreElements() {
            return hasMore();
        }

        @Override
        public Binding nextElement() {
            return next();
        }

        @Override
        public void close() {
            // nothing to release
        }
    }

    /**
     * Everything a {@link Context} declares and this provider does not do. Kept apart so that
     * {@link MapContext} is only the three methods that matter, and so that anything else fails.
     */
    abstract static class UnsupportedContext implements Context {
        private static final String UNSUPPORTED = "the in-memory test provider does not implement this";

        @Override public Object lookup(Name name) throws NamingException { return lookup(name.toString()); }
        @Override public void bind(Name name, Object obj) { throw unsupported(); }
        @Override public void bind(String name, Object obj) { throw unsupported(); }
        @Override public void rebind(Name name, Object obj) { throw unsupported(); }
        @Override public void rebind(String name, Object obj) { throw unsupported(); }
        @Override public void unbind(Name name) { throw unsupported(); }
        @Override public void unbind(String name) { throw unsupported(); }
        @Override public void rename(Name oldName, Name newName) { throw unsupported(); }
        @Override public void rename(String oldName, String newName) { throw unsupported(); }
        @Override public NamingEnumeration<NameClassPair> list(Name name) { throw unsupported(); }
        @Override public NamingEnumeration<NameClassPair> list(String name) { throw unsupported(); }
        @Override public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
            return listBindings(name.toString());
        }
        @Override public void destroySubcontext(Name name) { throw unsupported(); }
        @Override public void destroySubcontext(String name) { throw unsupported(); }
        @Override public Context createSubcontext(Name name) { throw unsupported(); }
        @Override public Context createSubcontext(String name) { throw unsupported(); }
        @Override public Object lookupLink(Name name) { throw unsupported(); }
        @Override public Object lookupLink(String name) { throw unsupported(); }
        @Override public javax.naming.NameParser getNameParser(Name name) { throw unsupported(); }
        @Override public javax.naming.NameParser getNameParser(String name) { throw unsupported(); }
        @Override public Name composeName(Name name, Name prefix) { throw unsupported(); }
        @Override public String composeName(String name, String prefix) { throw unsupported(); }
        @Override public Object addToEnvironment(String propName, Object propVal) { throw unsupported(); }
        @Override public Object removeFromEnvironment(String propName) { throw unsupported(); }
        @Override public Hashtable<?, ?> getEnvironment() { return new Hashtable<>(); }
        @Override public String getNameInNamespace() { return ""; }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException(UNSUPPORTED);
        }
    }
}
