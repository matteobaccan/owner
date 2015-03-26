/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.typeconversion.editor;

import org.aeonbits.owner.typeconversion.PrimitiveTypesTest;
import org.junit.After;
import org.junit.Before;

import static org.aeonbits.owner.typeconversion.editor.PropertyEditorTestUtil.PROPERTY_EDITOR_DISABLED;

/**
 * @author Luigi R. Viggiano
 */
public class PropertyEditorNotAvailable extends PrimitiveTypesTest {

    private String save;

    @Before
    public void before() {
        save = System.getProperty(PROPERTY_EDITOR_DISABLED);
        System.setProperty(PROPERTY_EDITOR_DISABLED, "true");
    }

    @After
    public void after() {
        if (save == null)
            System.getProperties().remove(PROPERTY_EDITOR_DISABLED);
        else
            System.setProperty(PROPERTY_EDITOR_DISABLED, save);
    }
}
