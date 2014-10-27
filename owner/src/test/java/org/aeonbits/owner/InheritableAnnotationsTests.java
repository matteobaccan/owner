/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.aeonbits.owner.Config.Sources;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class InheritableAnnotationsTests {
    
    private static final String DADDY_FAMILY_PROPERTIES_FILE = "daddy-family.properties";
    private static final String MOMMY_FAMILY_PROPERTIES_FILE = "mommy-family.properties";
    private static final String SPOUSE_FAMILY_PROPERTIES_FILE = "spouse-family.properties";
    private static final String DADDY_PROPERTIES_FILE = "daddy.properties";
    private static final String MOMMY_PROPERTIES_FILE = "mommy.properties";
    private static final String SISTER_PROPERTIES_FILE = "sister.properties";
    private static final String SPOUSE_PROPERTIES_FILE = "spouse.properties";
    
    private static Path configDir;
    
    @BeforeClass
    public static void setUpOnce()
        throws IOException
    {
        configDir = Files.createTempDirectory(null);
        ConfigFactory.setProperty("config.dir", configDir.toAbsolutePath().toString() );
        createConfigFiles(configDir);
    }

    @AfterClass
    public static void tearDownOnce()
        throws IOException
    {
        configDir.toFile().deleteOnExit();
    }

    private static void createConfigFiles( Path rootDir )
            throws IOException
    {
        Files.write(Paths.get(configDir.toAbsolutePath() + File.separator + DADDY_FAMILY_PROPERTIES_FILE ),
            new String("sources="+DADDY_FAMILY_PROPERTIES_FILE).getBytes());
        Files.write(Paths.get(configDir.toAbsolutePath() + File.separator + MOMMY_FAMILY_PROPERTIES_FILE ),
            new String("sources="+MOMMY_FAMILY_PROPERTIES_FILE).getBytes());
        Files.write(Paths.get(configDir.toAbsolutePath() + File.separator + SPOUSE_FAMILY_PROPERTIES_FILE ),
            new String("sources="+SPOUSE_FAMILY_PROPERTIES_FILE).getBytes());
        Files.write(Paths.get(configDir.toAbsolutePath() + File.separator + DADDY_PROPERTIES_FILE ),
            new String("sources="+DADDY_PROPERTIES_FILE).getBytes());
        Files.write(Paths.get(configDir.toAbsolutePath() + File.separator + MOMMY_PROPERTIES_FILE ),
            new String("sources="+MOMMY_PROPERTIES_FILE).getBytes());
        Files.write(Paths.get(configDir.toAbsolutePath() + File.separator + SISTER_PROPERTIES_FILE ),
            new String("sources="+SISTER_PROPERTIES_FILE).getBytes());
        Files.write(Paths.get(configDir.toAbsolutePath() + File.separator + SPOUSE_PROPERTIES_FILE ),
            new String("sources="+SPOUSE_PROPERTIES_FILE).getBytes());
    }

    /**
     * setup config classes for multiple inheritance represented in three generations from three families
     */
    static interface TestConfig extends Config {
        String sources();
    }
    
    // setup families
    @Sources({ "file:${config.dir}/"+MOMMY_FAMILY_PROPERTIES_FILE })
    static interface MommyFamilyConfig extends TestConfig {}
    @Sources({ "file:${config.dir}/"+DADDY_FAMILY_PROPERTIES_FILE })
    static interface DaddyFamilyConfig extends TestConfig {}
    @Sources({ "file:${config.dir}/"+SPOUSE_FAMILY_PROPERTIES_FILE })
    static interface SpouseFamilyConfig extends TestConfig {}
    
    // setup parents as daddy and mommy
    // Daddy inheritance: he has its own properties
    // Mommy inheritance: MommyFamilyConfig
    @Sources({ "file:${config.dir}/"+DADDY_PROPERTIES_FILE })
    static interface DaddyConfig extends DaddyFamilyConfig {}
    static interface MommyConfig extends MommyFamilyConfig {}

    // setup sibling, Brother and Sister are siblings
    // Brother inheritance: MommyChildConfig -> MommyConfig -> MommyFamilyConfig
    // Spouse inheritance: SpouseFamilyConfig
    // Sister1 inheritance: she has her own properties
    // Sister2 inheritance: DaddyChildConfig -> DaddyConfig
    static interface DaddyChildConfig extends DaddyConfig, MommyConfig {} 
    static interface MommyChildConfig extends MommyConfig, DaddyConfig {}
    static interface BrotherConfig extends MommyChildConfig {}
    static interface SpouseConfig extends SpouseFamilyConfig {}
    @Sources({ "file:${config.dir}/"+SISTER_PROPERTIES_FILE })
    static interface Sister1Config extends DaddyChildConfig {}
    static interface Sister2Config extends DaddyChildConfig {}
    
    // Brother and Spouse are married having the GrandChild as their child
    // Grand brother child inheritance: BrotherConfig -> MommyChildConfig -> MommyConfig -> MommyFamilyConfig
    // Grand Spouse child inheritance: SpouseConfig -> SpouseFamilyConfig
    static interface GrandBrotherChildConfig extends BrotherConfig, SpouseConfig {} 
    static interface GrandSpouseChildConfig extends SpouseConfig, BrotherConfig {} 

     @Test
     public void testTestConfig() {
         TestConfig config = ConfigFactory.create(TestConfig.class);
         assertNull(config.sources());
     }

     @Test
     public void testFamilyConfig() {
         assertEquals(DADDY_FAMILY_PROPERTIES_FILE, ConfigFactory.create(DaddyFamilyConfig.class).sources());
         assertEquals(MOMMY_FAMILY_PROPERTIES_FILE, ConfigFactory.create(MommyFamilyConfig.class).sources());
         assertEquals(SPOUSE_FAMILY_PROPERTIES_FILE, ConfigFactory.create(SpouseFamilyConfig.class).sources());
     }

     @Test
     public void testParentConfig() {
         assertEquals(DADDY_PROPERTIES_FILE, ConfigFactory.create(DaddyConfig.class).sources());
         assertEquals(MOMMY_FAMILY_PROPERTIES_FILE, ConfigFactory.create(MommyConfig.class).sources());
     }

     @Test
     public void testSiblingConfig() {
         assertEquals(MOMMY_FAMILY_PROPERTIES_FILE, ConfigFactory.create(BrotherConfig.class).sources());
         assertEquals(SISTER_PROPERTIES_FILE, ConfigFactory.create(Sister1Config.class).sources());
         assertEquals(DADDY_PROPERTIES_FILE, ConfigFactory.create(Sister2Config.class).sources());
         assertEquals(SPOUSE_FAMILY_PROPERTIES_FILE, ConfigFactory.create(SpouseConfig.class).sources());
     }

     @Test
     public void testGrandChildConfig() {
         assertEquals(MOMMY_FAMILY_PROPERTIES_FILE, ConfigFactory.create(GrandBrotherChildConfig.class).sources());
         assertEquals(SPOUSE_FAMILY_PROPERTIES_FILE, ConfigFactory.create(GrandSpouseChildConfig.class).sources());
     }
}