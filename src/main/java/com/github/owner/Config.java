/*
 * Copyright (c) 2012, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package com.github.owner;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker interface that must be implemented by all Config sub-interfaces.
 *
 * @author Luigi R. Viggiano
 */
public interface Config {

    /**
     * Specifies the source from which to load the properties file.
     * It has to be specified in a URL string format.
     * Allowed protocols are the ones allowed by {@link java.net.URL} plus
     * <tt>classpath:path/to/resource.properties</tt>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Documented
    public @interface Sources {
        String[] value();
    }

    /**
     * Default value to be used if no property is found.
     * No quoting (other than normal Java string quoting)
     * is done.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface DefaultValue {
        String value();
    }

    /**
     * The key used for lookup for the property.  If not present, the
     * key will be generated based on the unqualified method name.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface Key {
        String value();
    }
}
