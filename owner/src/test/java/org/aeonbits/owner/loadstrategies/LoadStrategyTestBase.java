/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loadstrategies;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.net.URI;

/**
 * @author Luigi R. Viggiano
 */
public class LoadStrategyTestBase {
    Matcher<URI> uriMatches(final String path) {
        return new BaseMatcher<URI>(){
            public URI uri;

            public boolean matches(Object o) {
                uri = (URI)o;
                return uri.getPath().endsWith(path);
            }

            public void describeTo(Description description) {
                description.appendText("expected <" + (uri != null ? uri : "uri") + "> ending with " + path);
            }
        };
    }
}
