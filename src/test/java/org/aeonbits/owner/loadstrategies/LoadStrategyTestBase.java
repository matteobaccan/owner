package org.aeonbits.owner.loadstrategies;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.net.URL;

/**
 * @author Luigi R. Viggiano
 */
public class LoadStrategyTestBase {
    protected Matcher<URL> urlMatches(final String path) {
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
