/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.DecryptorClass;
import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.Config.Description;
import org.aeonbits.owner.Config.DisableFeature;
import org.aeonbits.owner.Config.EncryptedValue;
import org.aeonbits.owner.Config.Mandatory;
import org.aeonbits.owner.Config.PreprocessorClasses;
import org.aeonbits.owner.Config.Sensitive;
import org.aeonbits.owner.Config.Separator;
import org.aeonbits.owner.crypto.Decryptor;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.aeonbits.owner.Config.DisableableFeature.VALIDATION;
import static org.aeonbits.owner.Config.DisableableFeature.VARIABLE_EXPANSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Where a class-level annotation reaches, for every annotation that has one - which is a question with two
 * answers, and the point of this class is that the two are told apart on purpose.
 * <p>
 * <b>Some describe the configuration object.</b> Which sources it reads, with which policy, reloaded how,
 * decrypted by whom, described how in a file it writes, with which features switched off. A configuration
 * object is not one interface but the one handed to the {@link ConfigFactory} together with everything it
 * extends, so a statement about the object counts wherever in that hierarchy it is written, nearest first.
 * {@link Annotations} is that walk. {@link AnnotationInheritanceTest} covers the three read by
 * {@link PropertiesManager} at creation; the rest are here.
 * </p>
 * <p>
 * <b>The others describe the methods an interface declares.</b> {@link Config.Prefix}, {@link Mandatory},
 * {@link Sensitive}, {@link Separator}, {@link Config.TokenizerClass}, {@link PreprocessorClasses}: these
 * are read off {@link java.lang.reflect.Method#getDeclaringClass()} and neither climb nor descend. An
 * interface governs what it declares - which is why a sub-interface cannot change the meaning of the keys
 * its parent declared, the rule {@code @Prefix} has always followed and the one the others follow with it.
 * Those tests are here too, because a rule that is never asserted is a coincidence.
 * </p>
 * <p>
 * {@link DisableFeature} is asked both questions, and until 2.0.0 it answered them with one lookup and so
 * contradicted itself - see {@link #theMethodAndTheAccessibleMethodsAgree()}.
 * </p>
 *
 * @author Matteo Baccan
 */
public class ClassLevelAnnotationsTest {

    /** A decryptor with no cryptography in it: what matters here is only whether it was found. */
    public static class Shouting implements Decryptor {
        @Override
        public String decrypt(String value) {
            return value.toUpperCase();
        }

        @Override
        public String decrypt(String value, String defaultValue) {
            return decrypt(value);
        }
    }

    // ------------------------------------------------- the walk itself

    interface Root extends Config {
    }

    interface Left extends Root {
    }

    interface Right extends Root {
    }

    interface Bottom extends Left, Right {
    }

    /**
     * The order the interfaces are visited in is the whole of what "the nearest declaration wins" means, so
     * it is asserted here rather than only through its consequences: breadth first, the {@code extends}
     * clause deciding between siblings, and each interface visited once however many paths reach it.
     */
    @Test
    public void theHierarchyIsWalkedBreadthFirstAndEachInterfaceOnce() {
        assertEquals(Arrays.asList(Bottom.class, Left.class, Right.class, Root.class, Config.class,
                        java.io.Serializable.class),
                new java.util.ArrayList<Class<?>>(Annotations.hierarchyOf(Bottom.class)));
    }

    // ------------------------------------------------- what describes the configuration object

    @DecryptorClass(Shouting.class)
    interface DeclaringTheDecryptor extends Config {
        @EncryptedValue
        @DefaultValue("hidden")
        String secret();
    }

    interface DecryptorOnTheParent extends DeclaringTheDecryptor {
    }

    interface MiddleWithoutDecryptor extends DeclaringTheDecryptor {
    }

    interface DecryptorOnTheGrandParent extends MiddleWithoutDecryptor {
    }

    /**
     * The decryptor of a configuration is a property of the configuration, so it is found wherever it is
     * declared. Until 2.0.0 it was read off the interface handed to the factory and nowhere else - not even
     * its direct super-interfaces, so this was the worst of the family - and the failure was silent: the
     * method answered with the ciphertext as stored, which is a string like any other and breaks, if it
     * breaks at all, wherever it is finally used.
     */
    @Test
    public void theDecryptorIsFoundAnywhereInTheHierarchy() {
        assertEquals("HIDDEN", ConfigFactory.create(DeclaringTheDecryptor.class).secret());
        assertEquals("HIDDEN", ConfigFactory.create(DecryptorOnTheParent.class).secret());
        assertEquals("HIDDEN", ConfigFactory.create(DecryptorOnTheGrandParent.class).secret());
    }

    @Description("What the base interface says the file is for")
    interface DescribedBase extends Config, Accessible {
        @DefaultValue("8080")
        int port();
    }

    interface DescriptionOnTheParent extends DescribedBase {
    }

    @Description("What this one says instead")
    interface DescriptionOnBoth extends DescribedBase {
    }

    /**
     * The header {@link Accessible#save(File)} writes describes the configuration and not one interface of
     * it, so a base interface that says what the file is for says it for everything that extends it, and a
     * nearer statement replaces it rather than being appended to it - one file, one header.
     */
    @Test
    public void theDescriptionOfTheFileIsFoundAnywhereInTheHierarchy() throws IOException {
        assertTrue(saved(DescribedBase.class).contains("What the base interface says the file is for"));
        assertTrue(saved(DescriptionOnTheParent.class).contains("What the base interface says the file is for"));

        String nearest = saved(DescriptionOnBoth.class);
        assertTrue(nearest, nearest.contains("What this one says instead"));
        assertFalse(nearest, nearest.contains("What the base interface says"));
    }

    private static String saved(Class<? extends Accessible> configClass) throws IOException {
        File file = File.createTempFile("owner-class-level", ".properties");
        try {
            ConfigFactory.create(configClass).save(file);
            return new String(Files.readAllBytes(file.toPath()), UTF_8);
        } finally {
            Files.delete(file.toPath());
        }
    }

    @DisableFeature(VARIABLE_EXPANSION)
    interface NotExpanding extends Config, Accessible {
        @DefaultValue("${user.home}")
        String home();
    }

    interface ExpansionDisabledOnTheParent extends NotExpanding {
    }

    /**
     * The case that showed the two questions had to be told apart. {@code home()} is declared on the
     * interface carrying the annotation, so the method path found it on the declaring class and returned the
     * text unexpanded; {@code getProperty("home")} is declared on {@link Accessible}, asks the configuration
     * interface instead - the child, which carries nothing - and expanded. One configuration, one property,
     * two answers depending on how it was read.
     */
    @Test
    public void theMethodAndTheAccessibleMethodsAgree() {
        ExpansionDisabledOnTheParent cfg = ConfigFactory.create(ExpansionDisabledOnTheParent.class);

        assertEquals("${user.home}", cfg.home());
        assertEquals("${user.home}", cfg.getProperty("home"));
    }

    @DisableFeature(VALIDATION)
    interface DisablingSomethingElse extends NotExpanding {
    }

    /**
     * Every declaration in the hierarchy is read, not only the nearest one: this annotation carries a
     * <b>set</b>, and two interfaces of the same hierarchy may switch off one feature each. Stopping at the
     * nearest would answer "expansion is on" here, only because the nearer interface happens to disable
     * something unrelated.
     */
    @Test
    public void twoInterfacesMayDisableOneFeatureEach() {
        DisablingSomethingElse cfg = ConfigFactory.create(DisablingSomethingElse.class);

        assertEquals("${user.home}", cfg.getProperty("home"));
    }

    // ------------------------------------------- what describes the methods an interface declares

    interface DeclaringThePassword extends Config, Accessible {
        @DefaultValue("s3cr3t")
        String password();

        @DefaultValue("matteo")
        String username();
    }

    @Sensitive
    interface SensitiveOnTheChild extends DeclaringThePassword {
    }

    @Sensitive
    interface SensitiveWhereItIsDeclared extends Config, Accessible {
        @DefaultValue("s3cr3t")
        String password();
    }

    /**
     * A class-level {@code @Sensitive} masks the properties of the interface that carries it, and a
     * sub-interface saying so does not reach the keys its parent declared. That is the same rule as
     * {@code @Prefix}, and the reason for it is the same: what an interface declares is described by that
     * interface, or two interfaces would be describing one key. Whoever wants the parent's keys masked
     * writes it where they are declared - or on the methods, which is what the annotation is for.
     */
    @Test
    public void sensitiveGovernsTheInterfaceThatDeclaresTheMethod() throws UnsupportedEncodingException {
        assertTrue(listed(SensitiveWhereItIsDeclared.class).contains("password=" + Sensitive.MASK));
        assertTrue(listed(SensitiveOnTheChild.class).contains("password=s3cr3t"));
    }

    private static String listed(Class<? extends Accessible> configClass) throws UnsupportedEncodingException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ConfigFactory.create(configClass).list(new PrintStream(bytes, true, "UTF-8"));
        return new String(bytes.toByteArray(), UTF_8);
    }

    interface DeclaringAnAbsentProperty extends Config {
        String absent();
    }

    @Mandatory
    interface MandatoryOnTheChild extends DeclaringAnAbsentProperty {
    }

    @Mandatory
    interface MandatoryWhereItIsDeclared extends Config {
        String absent();
    }

    /** The same rule for {@code @Mandatory}: it makes mandatory what the interface carrying it declares. */
    @Test
    public void mandatoryGovernsTheInterfaceThatDeclaresTheMethod() {
        try {
            ConfigFactory.create(MandatoryWhereItIsDeclared.class).absent();
            fail("the property is declared on the annotated interface: it is mandatory");
        } catch (MissingMandatoryPropertyException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("absent"));
        }

        assertNull(ConfigFactory.create(MandatoryOnTheChild.class).absent());
    }

    interface DeclaringAList extends Config {
        @DefaultValue("a,b")
        List<String> items();
    }

    @Separator(";")
    interface SeparatorOnTheChild extends DeclaringAList {
    }

    /**
     * And for {@code @Separator}, where it matters most: a sub-interface changing how the list its parent
     * declared is cut would change the meaning of a value the parent wrote, without either of them saying
     * anything wrong. The comma the parent was written against is the one that cuts it.
     */
    @Test
    public void theSeparatorGovernsTheInterfaceThatDeclaresTheMethod() {
        assertEquals(Arrays.asList("a", "b"), ConfigFactory.create(SeparatorOnTheChild.class).items());
    }

    public static class Shout implements Preprocessor {
        @Override
        public String process(String input) {
            return input.toUpperCase();
        }
    }

    interface DeclaringAValueToProcess extends Config {
        @DefaultValue("quiet")
        String value();
    }

    @PreprocessorClasses(Shout.class)
    interface PreprocessorOnTheChild extends DeclaringAValueToProcess {
    }

    @PreprocessorClasses(Shout.class)
    interface PreprocessorWhereItIsDeclared extends Config {
        @DefaultValue("quiet")
        String value();
    }

    /** And for {@code @PreprocessorClasses}, which rewrites the value the declaring interface described. */
    @Test
    public void thePreprocessorsGovernTheInterfaceThatDeclaresTheMethod() {
        assertEquals("QUIET", ConfigFactory.create(PreprocessorWhereItIsDeclared.class).value());
        assertEquals("quiet", ConfigFactory.create(PreprocessorOnTheChild.class).value());
    }
}
