/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.plugin;

import java.io.File;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.aeonbits.owner.creator.PropertiesFileCreator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author Luca Taddeo
 */

@Mojo(name = "create", requiresDependencyResolution = ResolutionScope.RUNTIME)
public class PropertiesFileCreatorMojo 
    extends AbstractMojo {

    /**
     * Location of the output properties file.
     */
    @Parameter(required=true)
    private String outputDirectory;
    
    /**
     * Config class to parse.
     */
    @Parameter(required=true)
    private String packageClass;
    
    /**
     * Jar to include.
     */
    @Parameter
    private String jarPath;

    /**
     * External jars dependency folder.
     */
    @Parameter
    public String jarsDependencyFolder;
    
    /**
     * Project Name.
     */
    @Parameter
    private String projectName;
    
    /**
     * TODO
     * Template for properties file.
     */
    @Parameter
    private String propertiesTemplate;
    
    @Parameter( defaultValue = "${project}", readonly = true )
    private MavenProject project;
    
    @Override
    public void execute() throws MojoExecutionException {

        List<URL> urls = new ArrayList();

        try {
            if (jarPath != null && !jarPath.isEmpty()) {
                System.out.println("Use passed jar [" + jarPath + "]");
                File jar = new File(jarPath);
                if (jar.exists()) {
                    urls.add(jar.toURI().toURL());
                } else {
                    logError("Jar doesn't exist [%s]", null, jarPath);
                }
            } else {
                System.out.println("Use classpath");
                // Loading project classpath to retrieve class
                for (Object ele : project.getRuntimeClasspathElements()) {
                    File jar = new File(ele.toString());
                    urls.add(jar.toURI().toURL());
                }
            }

            // If a classpath 
            if (jarsDependencyFolder != null && !jarsDependencyFolder.isEmpty()) {
                logInfo("Use jars dependency folder [%s]", jarsDependencyFolder);

                File path = new File(jarsDependencyFolder);

                if (path.isDirectory()) {
                    File[] filteredJars = path.listFiles(
                        new FilenameFilter() {
                            @Override
                            public boolean accept(File dir, String name) {
                                return name.endsWith(".jar");
                            }
                        });
                    for (File fileJar : filteredJars) {
                        urls.add(fileJar.toURI().toURL());
                    }
                }
            }

            String template = null;
            if (propertiesTemplate != null && !propertiesTemplate.isEmpty()) {
                byte[] encoded = Files.readAllBytes(Paths.get(propertiesTemplate));
                template = new String(encoded, "UTF-8");
            }
            
            URLClassLoader jarPack = new URLClassLoader(urls.toArray(new URL[urls.size()]), this.getClass().getClassLoader());
            try {
                Class classToLoad = jarPack.loadClass(packageClass);

                // Parse class and create property file
                PropertiesFileCreator creator = new PropertiesFileCreator();
                
                // If there is a custom properties template we change it in creator
                if (template != null) {
                    creator.changeTemplate(template);
                }
                
                PrintWriter output = new PrintWriter(outputDirectory);
                try {
                    creator.parse(classToLoad, output, projectName);
                } finally {
                    output.close();
                }
            } finally {
                jarPack.close();
            }
        } catch (Exception ex){
            throw new MojoExecutionException(ex.getMessage(), ex);
        }
        System.out.println("Conversion succeded, properties saved [" + outputDirectory + "]");
    }
    
    
    // Log utils.
    private Logger getLogger() {
        return Logger.getLogger(PropertiesFileCreatorMojo.class.getName());
    }
    
    private void logError(String error, Throwable ex, Object... args) {
        getLogger().log(Level.SEVERE, String.format(error, args), ex);
    }

    private void logInfo(String info, Object... args) {
        getLogger().log(Level.INFO, String.format(info, args));
    }
}
