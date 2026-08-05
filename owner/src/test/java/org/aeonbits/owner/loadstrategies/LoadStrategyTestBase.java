/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.loadstrategies;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.mockito.ArgumentMatcher;
import org.mockito.internal.hamcrest.HamcrestArgumentMatcher;

import java.net.URI;

/**
 * @author Luigi R. Viggiano
 */
public class LoadStrategyTestBase {
    ArgumentMatcher<URI> uriMatches(final String path) {
        return new HamcrestArgumentMatcher<>(
            new BaseMatcher<URI>(){
                public URI uri;

                @Override
                public boolean matches(Object o) {
                    uri = (URI)o;
                    return uri.toString().endsWith(path);
                }

                @Override
                public void describeTo(Description description) {
                    description.appendText("expected <" + (uri != null ? uri : "uri") + "> ending with " + path);
                }
            });
    }
}
