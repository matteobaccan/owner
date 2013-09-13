/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.event;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.mockito.ArgumentMatcher;

import java.beans.PropertyChangeEvent;

/**
 * @author Luigi R. Viggiano
 */
class PropertyChangeMatcher {
    static Matcher<PropertyChangeEvent> matches(final PropertyChangeEvent expectedEvent) {
        return new ArgumentMatcher<PropertyChangeEvent>() {
            @Override
            public boolean matches(Object argument) {
                PropertyChangeEvent arg = (PropertyChangeEvent) argument;
                return expectedEvent.getSource() == arg.getSource() &&
                        eq(expectedEvent.getOldValue(), arg.getOldValue()) &&
                        eq(expectedEvent.getNewValue(), arg.getNewValue()) &&
                        eq(expectedEvent.getPropertyName(), arg.getPropertyName());
            }

            private boolean eq(Object expected, Object actual) {
                return expected == actual || expected != null && expected.equals(actual);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(String.valueOf(expectedEvent));
            }
        };
    }
}
