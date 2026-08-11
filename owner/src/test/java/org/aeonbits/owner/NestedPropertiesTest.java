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
import org.aeonbits.owner.Config.DisableFeature;
import org.aeonbits.owner.Config.EncryptedValue;
import org.aeonbits.owner.Config.Key;
import org.aeonbits.owner.Config.Mandatory;
import org.aeonbits.owner.Config.Prefix;
import org.aeonbits.owner.Config.Sensitive;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.crypto.AbstractDecryptor;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.aeonbits.owner.Config.DisableableFeature.PREFIX;
import static org.aeonbits.owner.TestConstants.RESOURCES_DIR;
import static org.aeonbits.owner.util.UtilTest.fileFromURI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A group of related properties read as a configuration object of its own: see {@link NestedProperties} for
 * why the segment of the key is the accessor and not the type, and why the nested object loads nothing.
 *
 * @author Matteo Baccan
 */
public class NestedPropertiesTest {

    public interface ServerConfig extends Config {
        String host();

        @DefaultValue("8080")
        int port();
    }

    public interface AppConfig extends Config {
        String name();

        ServerConfig server();
    }

    /**
     * @param pairs key and value alternating, so an even number of them - miscounting is a mistake in the
     *              test rather than in what it tests
     */
    private static <T extends Config> T create(Class<T> type, String... pairs) {
        if (pairs.length % 2 != 0)
            throw new IllegalArgumentException("expected key and value alternating, got " + pairs.length
                    + " arguments: " + Arrays.toString(pairs));

        Properties props = new Properties();
        for (int i = 0; i + 1 < pairs.length; i += 2)
            props.setProperty(pairs[i], pairs[i + 1]);
        return ConfigFactory.create(type, props);
    }

    // -------------------------------------------------------------------------------------------------
    // the key
    // -------------------------------------------------------------------------------------------------

    @Test
    public void theAccessorNamesTheSection() {
        AppConfig cfg = create(AppConfig.class, "name", "app", "server.host", "localhost", "server.port", "9090");
        assertEquals("app", cfg.name());
        assertEquals("localhost", cfg.server().host());
        assertEquals(9090, cfg.server().port());
    }

    @Test
    public void aDefaultValueInsideTheSectionIsRegistered() {
        AppConfig cfg = create(AppConfig.class, "server.host", "localhost");
        assertEquals(8080, cfg.server().port());
    }

    @Test
    public void aPropertyOfTheSectionThatIsNotThereIsNull() {
        assertNull(create(AppConfig.class).server().host());
    }

    public interface RenamedConfig extends Config {
        @Key("http")
        ServerConfig server();
    }

    @Test
    public void theKeyAnnotationRenamesTheSection() {
        RenamedConfig cfg = create(RenamedConfig.class, "http.host", "localhost");
        assertEquals("localhost", cfg.server().host());
    }

    public interface InlinedConfig extends Config {
        @Key("")
        ServerConfig server();
    }

    @Test
    public void anEmptyKeyReadsTheKeysOfTheObjectHoldingIt() {
        InlinedConfig cfg = create(InlinedConfig.class, "host", "localhost");
        assertEquals("localhost", cfg.server().host());
    }

    public interface TwoServersConfig extends Config {
        ServerConfig primary();

        ServerConfig backup();
    }

    @Test
    public void twoAccessorsOfTheSameTypeDoNotCollide() {
        TwoServersConfig cfg = create(TwoServersConfig.class,
                "primary.host", "one", "backup.host", "two");
        assertEquals("one", cfg.primary().host());
        assertEquals("two", cfg.backup().host());
    }

    public interface DeepConfig extends Config {
        AppConfig app();
    }

    @Test
    public void theSectionsNestToAnyDepth() {
        DeepConfig cfg = create(DeepConfig.class, "app.server.host", "localhost");
        assertEquals("localhost", cfg.app().server().host());
    }

    // -------------------------------------------------------------------------------------------------
    // the prefixes it meets
    // -------------------------------------------------------------------------------------------------

    @Prefix("http.")
    public interface PrefixedServerConfig extends Config {
        String host();
    }

    public interface HoldingPrefixedConfig extends Config {
        PrefixedServerConfig server();
    }

    @Test
    public void aPrefixOnTheNestedInterfaceComposesWithThePath() {
        HoldingPrefixedConfig cfg = create(HoldingPrefixedConfig.class, "server.http.host", "localhost");
        assertEquals("localhost", cfg.server().host());
    }

    public interface EscapingConfig extends Config {
        @DisableFeature(PREFIX)
        String host();
    }

    public interface HoldingEscapingConfig extends Config {
        EscapingConfig server();
    }

    @Test
    public void disablingThePrefixInsideASectionReachesTheKeyItself() {
        HoldingEscapingConfig cfg = create(HoldingEscapingConfig.class, "host", "localhost");
        assertEquals("localhost", cfg.server().host());
    }

    @Test
    public void theFactoryPrefixIsAppliedOnceAndNotAgainBelow() {
        Factory factory = ConfigFactory.newInstance();
        factory.setProperty(KeyPrefix.LITERAL, "myapp.");
        Properties props = new Properties();
        props.setProperty("myapp.server.host", "localhost");

        AppConfig cfg = factory.create(AppConfig.class, props);
        assertEquals("localhost", cfg.server().host());
    }

    // -------------------------------------------------------------------------------------------------
    // the object
    // -------------------------------------------------------------------------------------------------

    @Test
    public void theSameAccessorAnswersWithTheSameObject() {
        AppConfig cfg = create(AppConfig.class, "server.host", "localhost");
        assertSame(cfg.server(), cfg.server());
    }

    @Test
    public void twoSectionsOfTheSameConfigurationAreNotEqual() {
        TwoServersConfig cfg = create(TwoServersConfig.class, "primary.host", "one", "backup.host", "two");
        assertNotEquals(cfg.primary(), cfg.backup());
    }

    @Test
    public void aConfigurationIsNotEqualToOneOfItsSections() {
        AppConfig cfg = create(AppConfig.class, "server.host", "localhost");
        assertNotEquals(cfg, cfg.server());
        assertNotEquals(cfg.server(), cfg);
    }

    @Test
    public void theSameSectionOfTwoEqualConfigurationsIsEqual() {
        AppConfig one = create(AppConfig.class, "server.host", "localhost");
        AppConfig two = create(AppConfig.class, "server.host", "localhost");
        assertEquals(one.server(), two.server());
        assertEquals(one.server().hashCode(), two.server().hashCode());
    }

    public interface MutableAppConfig extends Config, Mutable {
        ServerConfig server();
    }

    @Test
    public void theSectionIsAViewAndNotACopy() {
        MutableAppConfig cfg = create(MutableAppConfig.class, "server.host", "localhost");
        ServerConfig server = cfg.server();
        cfg.setProperty("server.host", "elsewhere");
        assertEquals("elsewhere", server.host());
    }

    @Test
    public void theSectionSurvivesSerialization() throws Exception {
        AppConfig cfg = create(AppConfig.class, "server.host", "localhost");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(cfg);
        }
        AppConfig read;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            read = (AppConfig) in.readObject();
        }

        assertEquals("localhost", read.server().host());
        assertSame(read.server(), read.server());
    }

    // -------------------------------------------------------------------------------------------------
    // absent, optional, mandatory
    // -------------------------------------------------------------------------------------------------

    /** No default value anywhere inside it: see {@link #aDefaultInsideAnOptionalSectionMakesItAlwaysPresent}. */
    public interface PlainServerConfig extends Config {
        String host();
    }

    public interface OptionalSectionConfig extends Config {
        Optional<PlainServerConfig> server();
    }

    @Test
    public void anOptionalSectionIsEmptyWhenNothingBelowItWasWritten() {
        assertFalse(create(OptionalSectionConfig.class, "name", "app").server().isPresent());
    }

    @Test
    public void anOptionalSectionIsPresentAsSoonAsOnePropertyIsThere() {
        Optional<PlainServerConfig> server =
                create(OptionalSectionConfig.class, "server.host", "localhost").server();
        assertTrue(server.isPresent());
        assertEquals("localhost", server.get().host());
    }

    public interface OptionalDefaultedConfig extends Config {
        Optional<ServerConfig> server();
    }

    /**
     * The rule chosen for a default inside an optional section, and the reason it is a test rather than a
     * remark: <code>ServerConfig</code> defaults its port, that default is merged into the properties like
     * any other value, and from then on the section is there. An Optional and a default written inside it
     * say the opposite of each other, and the default wins.
     */
    @Test
    public void aDefaultInsideAnOptionalSectionMakesItAlwaysPresent() {
        Optional<ServerConfig> server = create(OptionalDefaultedConfig.class, "name", "app").server();
        assertTrue(server.isPresent());
        assertEquals(8080, server.get().port());
        assertNull(server.get().host());
    }

    public interface MandatoryInsideConfig extends Config {
        MandatoryServerConfig server();
    }

    public interface MandatoryServerConfig extends Config {
        @Mandatory
        String host();
    }

    @Test
    public void aMandatoryPropertyInsideASectionIsCheckedWhenTheConfigurationIsCreated() {
        try {
            create(MandatoryInsideConfig.class, "server.port", "8080");
            fail("a mandatory property one level down was not checked");
        } catch (MissingMandatoryPropertyException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("server.host"));
        }
        assertEquals("localhost", create(MandatoryInsideConfig.class, "server.host", "localhost")
                .server().host());
    }

    public interface MandatorySectionConfig extends Config {
        @Mandatory
        ServerConfig server();
    }

    /**
     * A check that cannot fail is refused rather than shipped: a section counts as present as soon as any
     * key below it exists, and a default inside it is one, so this could only ever pass.
     */
    @Test
    public void mandatoryOnTheAccessorOfASectionIsRefused() {
        try {
            create(MandatorySectionConfig.class, "name", "app");
            fail("@Mandatory on a nested accessor was accepted");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("server"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("@Mandatory"));
        }
    }

    @Mandatory
    public interface AllMandatoryConfig extends Config {
        String name();

        ServerConfig server();
    }

    /**
     * On the interface the annotation goes on meaning "these are all required", and leaves the nested
     * accessor alone — as it already leaves an Optional alone. Only the property is required here.
     */
    @Test
    public void mandatoryOnTheInterfaceLeavesTheSectionAlone() {
        assertEquals("app", create(AllMandatoryConfig.class, "name", "app").name());
        try {
            create(AllMandatoryConfig.class, "server.host", "localhost");
            fail("the mandatory property of the interface was not checked");
        } catch (MissingMandatoryPropertyException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("name"));
        }
    }

    public interface OptionalMandatoryConfig extends Config {
        Optional<MandatoryServerConfig> server();
    }

    @Test
    public void anAbsentOptionalSectionDoesNotRequireItsMandatoryProperties() {
        assertFalse(create(OptionalMandatoryConfig.class, "name", "app").server().isPresent());
    }

    @Test
    public void anOptionalSectionThatIsThereRequiresThemAllTheSame() {
        try {
            create(OptionalMandatoryConfig.class, "server.port", "8080");
            fail("a section that was written was not checked");
        } catch (MissingMandatoryPropertyException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("server.host"));
        }
    }

    // -------------------------------------------------------------------------------------------------
    // a list of them, one section per index: what a JSON or a YAML source flattens to
    // -------------------------------------------------------------------------------------------------

    public interface ClusterConfig extends Config {
        List<ServerConfig> servers();
    }

    @Test
    public void aListOfSectionsIsReadFromItsIndexedPaths() {
        ClusterConfig cfg = create(ClusterConfig.class,
                "servers[0].host", "alpha", "servers[0].port", "1",
                "servers[1].host", "beta", "servers[1].port", "2");

        List<ServerConfig> servers = cfg.servers();
        assertEquals(2, servers.size());
        assertEquals("alpha", servers.get(0).host());
        assertEquals(1, servers.get(0).port());
        assertEquals("beta", servers.get(1).host());
        assertEquals(2, servers.get(1).port());
    }

    @Test
    public void theOrderIsTheIndexAndNotTheOrderOfTheFile() {
        ClusterConfig cfg = create(ClusterConfig.class,
                "servers[1].host", "beta", "servers[0].host", "alpha", "servers[2].host", "gamma");
        assertEquals(Arrays.asList("alpha", "beta", "gamma"),
                Arrays.asList(cfg.servers().get(0).host(), cfg.servers().get(1).host(),
                        cfg.servers().get(2).host()));
    }

    /** The defaults of an element cannot be registered in advance: nobody knows how many elements there are. */
    @Test
    public void anElementDefaultsLikeAnythingElse() {
        ClusterConfig cfg = create(ClusterConfig.class, "servers[0].host", "alpha");
        assertEquals(8080, cfg.servers().get(0).port());
    }

    @Test
    public void theSameElementIsTheSameObject() {
        ClusterConfig cfg = create(ClusterConfig.class, "servers[0].host", "alpha");
        assertSame(cfg.servers().get(0), cfg.servers().get(0));
        assertNotEquals(cfg.servers().get(0),
                create(ClusterConfig.class, "servers[0].host", "beta").servers().get(0));
    }

    @Test
    public void aListOfSectionsThatWasNotWrittenIsNull() {
        assertNull(create(ClusterConfig.class, "name", "app").servers());
    }

    /**
     * The boundary between the two readings of an index: <code>servers[0]</code> is an element that
     * <b>is</b> a value, <code>servers[0].host</code> an element that <b>holds</b> some. A list of sections
     * does not collect the first, exactly as a list of values does not collect the second.
     */
    @Test
    public void aBareIndexIsNotASection() {
        assertNull(create(ClusterConfig.class, "servers[0]", "alpha").servers());
    }

    public interface ArrayClusterConfig extends Config {
        ServerConfig[] servers();
    }

    @Test
    public void anArrayOfSectionsWorksTheSameWay() {
        ServerConfig[] servers = create(ArrayClusterConfig.class,
                "servers[0].host", "alpha", "servers[1].host", "beta").servers();
        assertEquals(2, servers.length);
        assertEquals("beta", servers[1].host());
    }

    @Test
    public void aGapInTheSectionsIsRefusedJustAsInAListOfValues() {
        try {
            create(ClusterConfig.class, "servers[0].host", "alpha", "servers[2].host", "gamma").servers();
            fail("a gap was read across");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("servers[2].host"));
        }
    }

    @Test
    public void sectionsMustStartAtZeroJustAsValuesDo() {
        try {
            create(ClusterConfig.class, "servers[1].host", "beta").servers();
            fail("a list starting at one was accepted");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("servers[0]"));
        }
    }

    public interface DeepClusterConfig extends Config {
        List<AppConfig> apps();
    }

    @Test
    public void aSectionInsideAnElementIsReachedToo() {
        DeepClusterConfig cfg = create(DeepClusterConfig.class,
                "apps[0].name", "one", "apps[0].server.host", "alpha");
        assertEquals("one", cfg.apps().get(0).name());
        assertEquals("alpha", cfg.apps().get(0).server().host());
    }

    private static final String XML_SPEC = "file:" + RESOURCES_DIR + "/NestedPropertiesConfig.xml";

    @Sources(XML_SPEC)
    @Prefix("cluster.")
    public interface XmlClusterConfig extends Config {
        List<ServerConfig> servers();
    }

    /**
     * The two halves meeting, which is the whole reason for this feature. A tree-shaped source flattens to
     * <code>cluster.servers[0].host</code> — the loaders have done that since the flattening convention
     * landed — and until now nothing in the library could read a key of that shape.
     */
    @Test
    public void aListOfSectionsReadsWhatATreeShapedSourceFlattenedTo() throws Exception {
        File target = fileFromURI(XML_SPEC);
        target.getParentFile().mkdirs();
        try (Writer out = new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8)) {
            out.write("<cluster>"
                    + "<servers><host>alpha</host><port>1</port></servers>"
                    + "<servers><host>beta</host><port>2</port></servers>"
                    + "</cluster>");
        }

        List<ServerConfig> servers = ConfigFactory.create(XmlClusterConfig.class).servers();

        assertEquals(2, servers.size());
        assertEquals("alpha", servers.get(0).host());
        assertEquals(1, servers.get(0).port());
        assertEquals("beta", servers.get(1).host());
        assertEquals(2, servers.get(1).port());
    }

    public interface TreeConfig extends Config {
        String name();

        List<TreeConfig> children();
    }

    /**
     * A type holding a list of itself is a tree, and is allowed where a type <i>holding</i> itself is
     * refused: the properties say how deep it goes, and it ends where the keys end.
     */
    @Test
    public void aTypeHoldingAListOfItselfIsATreeAndIsAllowed() {
        TreeConfig root = create(TreeConfig.class,
                "name", "root",
                "children[0].name", "first",
                "children[0].children[0].name", "grandchild",
                "children[1].name", "second");

        assertEquals("root", root.name());
        assertEquals(2, root.children().size());
        assertEquals("grandchild", root.children().get(0).children().get(0).name());
        assertNull("the leaf has no children of its own", root.children().get(1).children());
    }

    // -------------------------------------------------------------------------------------------------
    // objects whose names are only known at run time: issues #126 and #209
    // -------------------------------------------------------------------------------------------------

    public interface NamedServersConfig extends Config {
        Map<String, ServerConfig> servers();
    }

    @Test
    public void aGroupOfSectionsIsReadAsAMapOfObjects() {
        NamedServersConfig cfg = create(NamedServersConfig.class,
                "servers.alpha.host", "one", "servers.alpha.port", "1",
                "servers.beta.host", "two");

        Map<String, ServerConfig> servers = cfg.servers();
        assertEquals(new LinkedHashSet<>(Arrays.asList("alpha", "beta")), servers.keySet());
        assertEquals("one", servers.get("alpha").host());
        assertEquals(1, servers.get("alpha").port());
        assertEquals("two", servers.get("beta").host());
        assertEquals("the element defaults like any other section", 8080, servers.get("beta").port());
    }

    @Test
    public void aGroupOfSectionsIsEmptyRatherThanNullWhenThereIsNone() {
        assertTrue(create(NamedServersConfig.class, "name", "app").servers().isEmpty());
    }

    /** The same boundary as an indexed list: a key with nothing below it names no section. */
    @Test
    public void aValueUnderTheGroupIsNotASection() {
        assertTrue(create(NamedServersConfig.class, "servers.alpha", "one").servers().isEmpty());
    }

    public interface TypedKeysConfig extends Config {
        @Key("servers")
        Map<Integer, ServerConfig> byNumber();
    }

    @Test
    public void theNameOfASectionIsConvertedLikeAnyOtherKey() {
        Map<Integer, ServerConfig> servers = create(TypedKeysConfig.class,
                "servers.1.host", "one", "servers.2.host", "two").byNumber();
        assertEquals("one", servers.get(1).host());
        assertEquals("two", servers.get(2).host());
    }

    public interface ByNameConfig extends Config {
        @Key("servers.%s")
        ServerConfig server(String name);
    }

    /**
     * The other half of the same question, and the recipe that until now had to be written by hand: the name
     * is not in the interface at all, it is an argument.
     */
    @Test
    public void aSectionCanBeAskedForByName() {
        ByNameConfig cfg = create(ByNameConfig.class,
                "servers.alpha.host", "one", "servers.beta.host", "two");

        assertEquals("one", cfg.server("alpha").host());
        assertEquals("two", cfg.server("beta").host());
        assertSame("asked twice, the same object", cfg.server("alpha"), cfg.server("alpha"));
        assertNotEquals(cfg.server("alpha"), cfg.server("beta"));
    }

    @Test
    public void aSectionAskedForByANameNobodyWroteAnswersWithItsDefaults() {
        ByNameConfig cfg = create(ByNameConfig.class, "servers.alpha.host", "one");
        assertNull(cfg.server("nowhere").host());
        assertEquals(8080, cfg.server("nowhere").port());
    }

    // -------------------------------------------------------------------------------------------------
    // the annotations that are read once, when the configuration is created, and have to reach the sections
    // -------------------------------------------------------------------------------------------------

    public interface SecretiveServerConfig extends Config {
        String host();

        @Sensitive
        String password();
    }

    public interface HoldingSecretsConfig extends Config, Accessible {
        SecretiveServerConfig server();
    }

    @Test
    public void aSensitivePropertyInsideASectionIsMaskedUnderItsWholeKey() {
        HoldingSecretsConfig cfg = create(HoldingSecretsConfig.class,
                "server.host", "localhost", "server.password", "hunter2");

        String printed = cfg.toString();
        assertFalse("the password was printed", printed.contains("hunter2"));
        assertTrue(printed.contains("server.password=" + Sensitive.MASK));
        assertTrue("everything else is printed as it is", printed.contains("server.host=localhost"));
        assertEquals("masking is not encryption", "hunter2", cfg.server().password());
    }

    public interface SensitiveSectionConfig extends Config, Accessible {
        String name();

        @Sensitive
        PlainServerConfig server();
    }

    @Test
    public void aSensitiveSectionMasksEverythingBelowIt() {
        SensitiveSectionConfig cfg = create(SensitiveSectionConfig.class,
                "name", "app", "server.host", "localhost");

        String printed = cfg.toString();
        assertFalse("the host was printed", printed.contains("localhost"));
        assertTrue(printed.contains("server.host=" + Sensitive.MASK));
        assertTrue(printed.contains("name=app"));
        assertEquals("localhost", cfg.server().host());
    }

    /** Reads a value backwards, which is decryption enough to tell whether the decryptor was found. */
    public static class Reverser extends AbstractDecryptor {
        @Override
        public String decrypt(String value) {
            return new StringBuilder(value).reverse().toString();
        }
    }

    public interface EncryptedServerConfig extends Config {
        @EncryptedValue(Reverser.class)
        String password();
    }

    public interface HoldingEncryptedConfig extends Config {
        EncryptedServerConfig server();
    }

    @Test
    public void anEncryptedValueInsideASectionIsDecrypted() {
        HoldingEncryptedConfig cfg = create(HoldingEncryptedConfig.class, "server.password", "2retnuh");
        assertEquals("hunter2", cfg.server().password());
    }

    @DecryptorClass(Reverser.class)
    public interface DecryptedHolderConfig extends Config {
        PlainEncryptedConfig server();
    }

    public interface PlainEncryptedConfig extends Config {
        @EncryptedValue
        String password();
    }

    /** A section that names no decryptor of its own uses the one of the configuration holding it. */
    @Test
    public void aSectionInheritsTheDecryptorOfTheConfigurationHoldingIt() {
        DecryptedHolderConfig cfg = create(DecryptedHolderConfig.class, "server.password", "2retnuh");
        assertEquals("hunter2", cfg.server().password());
    }

    public interface SecretiveElementConfig extends Config, Accessible {
        String name();

        List<SecretiveServerConfig> servers();
    }

    /**
     * An element of a list has no key anybody could name when the configuration is created, so there is
     * nothing to put in the list of keys to mask. What is masked is the whole group — the same answer this
     * library already gives where a group and a key inside it disagree, and the same reasoning: a secret
     * printed because nobody could name it in advance is the mistake that costs something.
     */
    @Test
    public void aSensitivePropertyInsideAnElementMasksTheWholeGroup() {
        SecretiveElementConfig cfg = create(SecretiveElementConfig.class,
                "name", "app", "servers[0].host", "localhost", "servers[0].password", "hunter2");

        String printed = cfg.toString();
        assertFalse("the password was printed", printed.contains("hunter2"));
        assertTrue(printed.contains("servers[0].password=" + Sensitive.MASK));
        assertTrue("the whole group goes, host included", printed.contains("servers[0].host=" + Sensitive.MASK));
        assertTrue("and nothing outside it", printed.contains("name=app"));
        assertEquals("hunter2", cfg.servers().get(0).password());
    }

    public interface EncryptedElementsConfig extends Config {
        List<EncryptedServerConfig> servers();

        Map<String, EncryptedServerConfig> named();

        @Key("byName.%s")
        EncryptedServerConfig byName(String name);
    }

    /**
     * A decryptor is registered against the method, so an element of a group needs no key to be known: not
     * decrypting would hand back the encrypted text as though it were the value, which is the kind of
     * silence this project refuses.
     */
    @Test
    public void anEncryptedValueIsDecryptedInEveryKindOfGroup() {
        EncryptedElementsConfig cfg = create(EncryptedElementsConfig.class,
                "servers[0].password", "2retnuh",
                "named.alpha.password", "2retnuh",
                "byName.beta.password", "2retnuh");

        assertEquals("hunter2", cfg.servers().get(0).password());
        assertEquals("hunter2", cfg.named().get("alpha").password());
        assertEquals("hunter2", cfg.byName("beta").password());
    }

    // -------------------------------------------------------------------------------------------------
    // what cannot be built
    // -------------------------------------------------------------------------------------------------

    public interface LoopingConfig extends Config {
        LoopingConfig itself();
    }

    public interface IndirectLoopConfig extends Config {
        SecondHalfConfig second();
    }

    public interface SecondHalfConfig extends Config {
        IndirectLoopConfig first();
    }

    @Test
    public void anInterfaceHoldingItselfIsRefused() {
        try {
            create(LoopingConfig.class);
            fail("a cycle was accepted");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("cycle"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("itself()"));
        }
    }

    @Test
    public void aCycleGoingRoundTwoInterfacesIsRefusedToo() {
        try {
            create(IndirectLoopConfig.class);
            fail("a cycle was accepted");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("IndirectLoopConfig -> SecondHalfConfig -> IndirectLoopConfig"));
        }
    }
}
