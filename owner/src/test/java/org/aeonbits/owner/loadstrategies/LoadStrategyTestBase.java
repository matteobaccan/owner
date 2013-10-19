/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loadstrategies;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.net.URL;

/**
 * @author Luigi R. Viggiano
 */
public class LoadStrategyTestBase {
    Matcher<URL> urlMatches(final String path) {
        return new BaseMatcher<URL>(){
            public URL url;

            public boolean matches(Object o) {
                url = (URL)o;
                return url.getPath().endsWith(path);
            }

            public void describeTo(Description description) {
                description.appendText("expected <" + (url != null ? url : "url") + "> ending with " + path);
            }
        };
    }
}
