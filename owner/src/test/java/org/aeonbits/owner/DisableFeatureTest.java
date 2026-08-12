/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.DisableFeature;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.aeonbits.owner.Config.DisableableFeature.PARAMETER_FORMATTING;
import static org.aeonbits.owner.Config.DisableableFeature.PREFIX;
import static org.aeonbits.owner.Config.DisableableFeature.VARIABLE_EXPANSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Luigi R. Viggiano
 */
public class DisableFeatureTest {
    public interface ConfigWithSubstitutionDisabledOnMethod extends Config {
        @DefaultValue("Earth")
        String world();

        @DisableFeature(VARIABLE_EXPANSION)
        @DefaultValue("Hello ${world}.")
        String sayHelloDisabled();

        @DefaultValue("Hello ${world}.")
        String sayHelloEnabled();
    }

    @Test
    public void shouldNotExpandWorldWhenDisabledOnMethodLevel() {
        ConfigWithSubstitutionDisabledOnMethod cfg = ConfigFactory.create(ConfigWithSubstitutionDisabledOnMethod.class);
        assertEquals("Hello ${world}.", cfg.sayHelloDisabled());
        assertEquals("Hello Earth.", cfg.sayHelloEnabled());
    }

    @DisableFeature(VARIABLE_EXPANSION)
    public static interface ConfigWithSubstitutionDisabledOnClass extends Config {
        @DefaultValue("Earth")
        String world();

        @DefaultValue("Hello ${world}.")
        String sayHelloDisabled();

        @DefaultValue("Hello ${world}.")
        String sayHelloEnabled();
    }

    @Test
    public void shouldNotExpandWorldWhenDisabledOnClassLevel() {
        ConfigWithSubstitutionDisabledOnClass cfg = ConfigFactory.create(ConfigWithSubstitutionDisabledOnClass.class);
        assertEquals("Hello ${world}.", cfg.sayHelloDisabled());
        assertEquals("Hello ${world}.", cfg.sayHelloEnabled());
    }

    public static interface ConfigWithDisabledFormattingOnMethod extends Config {
        @DisableFeature(PARAMETER_FORMATTING)
        @DefaultValue("Hello %s.")
        public String helloDisabled(String name);
        @DefaultValue("Hello %s.")
        public String helloEnabled(String name);
    }

    @Test
    public void shouldNotFormatOnMethodLevel() {
        ConfigWithDisabledFormattingOnMethod cfg = ConfigFactory.create(ConfigWithDisabledFormattingOnMethod.class);
        assertEquals("Hello %s.", cfg.helloDisabled("world"));
        assertEquals("Hello world.", cfg.helloEnabled("world"));
    }

    @DisableFeature(PARAMETER_FORMATTING)
    public static interface ConfigWithDisabledFormattingOnClass extends Config {
        @DefaultValue("Hello %s.")
        public String helloDisabled(String name);
        @DefaultValue("Hello %s.")
        public String helloEnabled(String name);
    }

    @Test
    public void shouldNotFormatOnClassLevel() {
        ConfigWithDisabledFormattingOnClass cfg = ConfigFactory.create(ConfigWithDisabledFormattingOnClass.class);
        assertEquals("Hello %s.", cfg.helloDisabled("world"));
        assertEquals("Hello %s.", cfg.helloEnabled("world"));
    }

    public static interface ConfigWithDisabledFormattingAndExpansionOnMethod extends Config {
        @DefaultValue("Earth")
        public String planet();

        @DisableFeature({VARIABLE_EXPANSION, PARAMETER_FORMATTING})
        @DefaultValue("Hello %s, welcome on ${planet}!")
        public String helloDisabled(String name);

        @DefaultValue("Hello %s, welcome on ${planet}!")
        public String helloEnabled(String name);
    }

    @Test
    public void shouldNotFormatAndExpandOnMethod() {
        ConfigWithDisabledFormattingAndExpansionOnMethod cfg =
                ConfigFactory.create(ConfigWithDisabledFormattingAndExpansionOnMethod.class);
        assertEquals("Hello Luigi, welcome on Earth!", cfg.helloEnabled("Luigi"));
        assertEquals("Hello %s, welcome on ${planet}!", cfg.helloDisabled("Luigi"));
    }

    @DisableFeature({VARIABLE_EXPANSION, PARAMETER_FORMATTING})
    public static interface ConfigWithDisabledFormattingAndExpansionOnClass extends Config {
        @DefaultValue("Earth")
        public String planet();

        @DisableFeature({VARIABLE_EXPANSION, PARAMETER_FORMATTING})
        @DefaultValue("Hello %s, welcome on ${planet}!")
        public String helloDisabled(String name);

        @DefaultValue("Hello %s, welcome on ${planet}!")
        public String helloEnabled(String name);
    }

    @Test
    public void shouldNotFormatAndExpandOnClass() {
        ConfigWithDisabledFormattingAndExpansionOnClass cfg =
                ConfigFactory.create(ConfigWithDisabledFormattingAndExpansionOnClass.class);
        assertEquals("Hello %s, welcome on ${planet}!", cfg.helloEnabled("Luigi"));
        assertEquals("Hello %s, welcome on ${planet}!", cfg.helloDisabled("Luigi"));
    }

    // DisableableFeature.isDisabledFor is public API since 2.0.0, where it replaced
    // Util.isFeatureDisabled. Every test above reaches it through a configuration object; these
    // read it the way a caller outside the library now can, directly on a Method.

    @Test
    public void isDisabledForReadsTheAnnotationOnTheMethod() throws Exception {
        Method disabled = ConfigWithSubstitutionDisabledOnMethod.class.getMethod("sayHelloDisabled");
        Method enabled = ConfigWithSubstitutionDisabledOnMethod.class.getMethod("sayHelloEnabled");
        assertTrue(VARIABLE_EXPANSION.isDisabledFor(disabled));
        assertFalse(VARIABLE_EXPANSION.isDisabledFor(enabled));
    }

    @Test
    public void isDisabledForReadsTheAnnotationOnTheDeclaringInterface() throws Exception {
        Method notAnnotated = ConfigWithSubstitutionDisabledOnClass.class.getMethod("sayHelloEnabled");
        assertTrue(VARIABLE_EXPANSION.isDisabledFor(notAnnotated));
    }

    @Test
    public void isDisabledForAnswersOnlyForTheFeatureItIsAskedAbout() throws Exception {
        Method disabled = ConfigWithSubstitutionDisabledOnMethod.class.getMethod("sayHelloDisabled");
        assertTrue(VARIABLE_EXPANSION.isDisabledFor(disabled));
        assertFalse(PARAMETER_FORMATTING.isDisabledFor(disabled));
        assertFalse(PREFIX.isDisabledFor(disabled));
    }

    /** Not a {@link Config}: the method reads an annotation, and asks nothing else of the type. */
    public interface NotAConfigAtAll {
        @DisableFeature(PREFIX)
        String annotated();

        String bare();
    }

    @Test
    public void isDisabledForDoesNotRequireAConfigInterface() throws Exception {
        assertTrue(PREFIX.isDisabledFor(NotAConfigAtAll.class.getMethod("annotated")));
        assertFalse(PREFIX.isDisabledFor(NotAConfigAtAll.class.getMethod("bare")));
    }
}
