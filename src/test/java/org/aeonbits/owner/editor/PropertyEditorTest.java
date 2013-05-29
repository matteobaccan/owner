/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.editor;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.beans.PropertyEditorManager;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class PropertyEditorTest {

    public interface MyAppConfig extends Config {
        @DefaultValue("admin")
        User user();

        @DefaultValue("admin,root")
        List<User> users();
    }

    @Test
    public void testPropertyEditor() {
        PropertyEditorManager.registerEditor(User.class, UserPropertyEditor.class);

        MyAppConfig cfg = ConfigFactory.create(MyAppConfig.class);

        assertEquals("admin", cfg.user().getUsername());
    }


    @Test
    public void testPropertyEditorWithList() {
        PropertyEditorManager.registerEditor(User.class, UserPropertyEditor.class);

        MyAppConfig cfg = ConfigFactory.create(MyAppConfig.class);
        List<User> users = cfg.users();
        assertEquals("admin", users.get(0).getUsername());
        assertEquals("root", users.get(1).getUsername());
    }

}
