/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A <code>default</code> method works on a configuration interface that is not <code>public</code>.
 *
 * <p>
 * It did not until 2.0.0, and nothing said so: the invocation looked the method up with a lookup taken in
 * <code>org.aeonbits.owner.util</code>, the package the helper lives in, which may not see an interface
 * that is package-private somewhere else. The failure was an <code>IllegalAccessException</code> reading
 * <em>symbolic reference class is not accessible</em>, at the first call rather than at creation. The one
 * test there was used a public top-level interface, which is the one shape that could not catch it.
 * </p>
 * <p>
 * A configuration interface is not required to be public — it is an implementation detail of the class
 * that reads it more often than not — so this is the ordinary case, not the exotic one.
 * </p>
 *
 * @author Matteo Baccan
 */
public class DefaultMethodOnNonPublicInterfaceTest {

    interface PackagePrivateConfig extends Config {
        @DefaultValue("8080")
        int port();

        @DefaultValue("localhost")
        String host();

        default String address() {
            return host() + ":" + port();
        }
    }

    private interface PrivateNestedConfig extends Config {
        @DefaultValue("2")
        int factor();

        default int twiceTheFactor() {
            return factor() * 2;
        }
    }

    @Test
    public void aDefaultMethodIsInvokedOnAPackagePrivateInterface() {
        PackagePrivateConfig config = ConfigFactory.create(PackagePrivateConfig.class);
        assertEquals("localhost:8080", config.address());
    }

    @Test
    public void aDefaultMethodIsInvokedOnAPrivateNestedInterface() {
        PrivateNestedConfig config = ConfigFactory.create(PrivateNestedConfig.class);
        assertEquals(4, config.twiceTheFactor());
    }
}
