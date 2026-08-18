/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * See: https://github.com/matteobaccan/owner/issues/66
 * <p>
 * gembin asked in 2014 for <code>@Config</code> on a plain interface instead of <code>extends Config</code>,
 * for a concrete reason he gave four days later: he was building a configuration API <b>on top of</b> this
 * library and did not want the library's own methods — <code>list(PrintStream)</code> and the rest of
 * {@link org.aeonbits.owner.Accessible}, <i>"especially some designed for debugging"</i> — on the type his
 * own users would see.
 * </p>
 * <p>
 * That half needs nothing: {@link Config} declares <b>no methods at all</b>, and
 * {@link org.aeonbits.owner.Accessible} and {@link org.aeonbits.owner.Mutable} are extended only by
 * whoever wants them. The type you publish carries what you put on it, which is what the first test
 * measures — and if you do want <code>Accessible</code> without it showing the whole file, that is
 * {@code @DeclaredOnly}, added for #150.
 * </p>
 * <p>
 * The rest is the recipe for keeping this library out of the API you publish altogether: the interface
 * your users compile against, and an internal one that maps it. <b>The wrinkle is where the annotations
 * go</b>, since an annotation of ours on the published interface would put us back on their compile
 * classpath — measured below rather than assumed.
 * </p>
 *
 * @author Matteo Baccan
 */
public class Issue66Test {

    /** What a library built on OWNER publishes: no supertype of ours, no annotation of ours. */
    public interface AppSettings {

        String host();

        int port();
    }

    /**
     * And what it keeps to itself. The methods are <b>redeclared</b> here so that the annotations have
     * somewhere to live that its users never see.
     */
    public interface AppSettingsMapping extends AppSettings, Config {

        @Override
        @Key("app.host")
        @DefaultValue("localhost")
        String host();

        @Override
        @Key("app.port")
        @DefaultValue("8080")
        int port();
    }

    /**
     * The published type carries nothing of ours: no supertype, no inherited method, no annotation.
     * <p>
     * The library's own types are the ones in the <code>org.aeonbits.owner</code> package itself, which is
     * what is asked here — this test lives in <code>org.aeonbits.owner.issues</code>, so a check on the
     * name starting with the package prefix would accuse the very interface it is measuring.
     * </p>
     */
    @Test
    public void whatIsPublishedCarriesNothingOfThisLibrary() {
        assertEquals("no supertype of ours", 0, AppSettings.class.getInterfaces().length);

        for (Method method : AppSettings.class.getMethods()) {
            assertEquals("everything on it is its own", AppSettings.class, method.getDeclaringClass());
            for (java.lang.annotation.Annotation annotation : method.getAnnotations())
                assertFalse(annotation.toString(), isOurs(annotation.annotationType()));
        }
    }

    private static boolean isOurs(Class<?> type) {
        return "org.aeonbits.owner".equals(type.getPackage().getName());
    }

    /** And it is still what the factory hands back, because the interface it maps extends both. */
    @Test
    public void andTheConfigurationIsStillOfThePublishedType() {
        AppSettings settings = ConfigFactory.create(AppSettingsMapping.class);

        assertEquals("localhost", settings.host());
        assertEquals(8080, settings.port());
        assertTrue(settings instanceof AppSettings);
    }

    /**
     * The wrinkle, measured: a method redeclared in the mapping interface is the one whose annotations
     * are read, although <code>getMethods()</code> hands back both declarations. Were it the other way
     * round the recipe would not work at all — the published interface would have to carry
     * <code>@Key</code> and <code>@DefaultValue</code>, and with them a compile-time dependency on this
     * library, which is the whole thing being avoided.
     */
    @Test
    public void theAnnotationsOnTheInterfaceThatMapsAreTheOnesThatCount() {
        AppSettingsMapping mapping = ConfigFactory.create(AppSettingsMapping.class);

        assertEquals("read under app.host, which only the mapping interface knows about",
                "localhost", mapping.host());
    }

    /** A plain interface is not a configuration, and the compiler is what says so. */
    @Test
    public void theMarkerIsWhatMakesTheFactoryTypeSafe() {
        // ConfigFactory.create(AppSettings.class) does not compile: create is <T extends Config>, and
        // that bound is this library's compile-time check. The three libraries that dropped the marker
        // replaced it with something else doing the checking — an annotation processor, or a container.
        assertFalse("Config is not assignable from a plain interface",
                Config.class.isAssignableFrom(AppSettings.class));
    }
}
