/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.plugin;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import org.aeonbits.owner.creator.PropertiesFileCreator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
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
     * Project Name.
     */
    @Parameter
    private String projectName;
    
    /**
     * Project description.
     */
    @Parameter
    private String projectDesription;
    
    @Component
    private MavenProject project;
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {

            List<URL> urls = new ArrayList();
            
            if(jarPath != null && !jarPath.isEmpty()) {
                System.out.println("Use passed jar [" + jarPath + "]");
                File jar = new File(jarPath);
                urls.add(jar.toURI().toURL());
            } else {
                System.out.println("Use classpath");
                // Loading project classpath to retrieve class
                for (Object ele : project.getRuntimeClasspathElements()) {
                    File jar = new File(ele.toString());
                    urls.add(jar.toURI().toURL());
                }
            }
            
            URLClassLoader jarPack = new URLClassLoader(urls.toArray(new URL[urls.size()]), this.getClass().getClassLoader());
            Class classToLoad = jarPack.loadClass(packageClass);
            
            // Parse class and create property file
            PropertiesFileCreator creator = new PropertiesFileCreator();
            creator.parse(classToLoad, outputDirectory, projectName, projectDesription);
            System.out.println("Conversion succeded, properties saved [" + outputDirectory + "]");
        } catch (Throwable ex) {
            throw new MojoExecutionException(ex.getMessage());
        }
    }
    
}
