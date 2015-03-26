/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.typeconversion;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.sql.Driver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Luigi R. Viggiano
 */
public class SpecialTypesTest {

    public static class InvalidCustomType {
    }

    public static class InvalidValueOf {
        private final String text;

        private InvalidValueOf(String text) {
            this.text = text;
        }

        public InvalidValueOf valueOf(String text) {
            return new InvalidValueOf(text);
        }
    }

    public static class ValueOf {
        private String text;

        private ValueOf() {
        }

        public static ValueOf valueOf(String text) {
            ValueOf result = new ValueOf();
            result.text = text;
            return result;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    public static interface SpecialTypes extends Config {
        @DefaultValue("foobar.txt")
        File sampleFile();

        @DefaultValue("~")
        File home();

        @DefaultValue("http://owner.aeonbits.org")
        URL sampleURL();

        @DefaultValue("test")
        CustomType customType();

        @DefaultValue("Hello %s!")
        CustomType salutation(String name);

        @DefaultValue("this should raise an exception")
        InvalidCustomType invalid();

        @DefaultValue("FOO")
        EnumType enumType();

        @DefaultValue("java.sql.Driver")
        Class<?> jdbcDriver();

        @DefaultValue("foo.bar.UnexistentClass")
        Class<?> nonExistentClass();

        @DefaultValue("foobar")
        Reference reference();

        @DefaultValue("invalidValueOf")
        InvalidValueOf invalidValueOf();

        @DefaultValue("valueOf")
        ValueOf valueOf();

        @DefaultValue("obj")
        Object object();
    }

    @Test
    public void testFileReturnType() throws Throwable {
        SpecialTypes config = ConfigFactory.create(SpecialTypes.class);
        File f = config.sampleFile();
        assertNotNull(f);
        assertEquals("foobar.txt", f.getName());
    }

    @Test
    public void testURLReturnType() throws Throwable {
        SpecialTypes config = ConfigFactory.create(SpecialTypes.class);
        URL u = config.sampleURL();
        assertNotNull(u);
        assertEquals("http://owner.aeonbits.org", u.toString());
    }

    public static class CustomType {
        private final String text;

        public CustomType(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    @Test
    public void testClassWithStringConstructor() throws Throwable {
        SpecialTypes config = ConfigFactory.create(SpecialTypes.class);
        CustomType custom = config.customType();
        assertNotNull(custom);
        assertEquals("test", custom.getText());
    }

    public static class Reference {
        private final Object object;

        public Reference(Object object) {
            this.object = object;
        }

        @Override
        public String toString() {
            return object.toString();
        }
    }

    @Test
    public void testClassWithObjectConstructor() throws Throwable {
        SpecialTypes config = ConfigFactory.create(SpecialTypes.class);
        Reference reference = config.reference();
        assertNotNull(reference);
        assertEquals("foobar", reference.toString());
    }

    @Test
    public void testCustomTypeWithParameter() throws Throwable {
        SpecialTypes config = ConfigFactory.create(SpecialTypes.class);
        CustomType custom = config.salutation("Luigi");
        assertNotNull(custom);
        assertEquals("Hello Luigi!", custom.getText());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testInvalidCustomType() throws Throwable {
        SpecialTypes config = ConfigFactory.create(SpecialTypes.class);
        config.invalid();
    }

    public static enum EnumType {
        FOO, BAR, BAZ
    }

    @Test
    public void testEnumType() throws Throwable {
        SpecialTypes config = ConfigFactory.create(SpecialTypes.class);
        EnumType enumType = config.enumType();
        assertNotNull(enumType);
        assertEquals(EnumType.FOO, enumType);
    }

    @Test
    public void testClassType() throws Throwable {
        SpecialTypes config = ConfigFactory.create(SpecialTypes.class);
        Class<?> driver = config.jdbcDriver();
        assertNotNull(driver);
        assertEquals(Driver.class, driver);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testClassTypeWhenClassIsNotFound() throws Throwable {
        SpecialTypes config = ConfigFactory.create(SpecialTypes.class);
        Class<?> nonExistent = config.nonExistentClass();
        assertNull(nonExistent);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testClassWithInvalidValueOfMethod() throws Throwable {
        SpecialTypes cfg = ConfigFactory.create(SpecialTypes.class);
        cfg.invalidValueOf();
    }

    @Test
    public void testClassWithValueOfMethod() throws Throwable {
        SpecialTypes cfg = ConfigFactory.create(SpecialTypes.class);
        ValueOf valueOf = cfg.valueOf();
        assertNotNull(valueOf);
        assertEquals("valueOf", valueOf.toString());
    }

    @Test
    public void testHome() throws Throwable {
        SpecialTypes cfg = ConfigFactory.create(SpecialTypes.class);
        File home = cfg.home();
        assertNotNull(home);
        assertEquals(new File(System.getProperty("user.home")), home);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testObject() throws Throwable {
        SpecialTypes cfg = ConfigFactory.create(SpecialTypes.class);
        cfg.object();
    }

}
