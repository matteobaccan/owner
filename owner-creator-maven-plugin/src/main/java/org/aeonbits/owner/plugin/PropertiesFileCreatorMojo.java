/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
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
    private String configurationClass;
    
    /**
     * Jar to include.
     */
    @Parameter
    private String jarPath;

    /**
     * External jars dependency folder.
     */
    @Parameter
    public String librariesFolder;
    
    /**
     * Project Name.
     */
    @Parameter
    private String projectName;
    
    /**
     * Template for properties file.
     */
    @Parameter
    private String propertiesTemplate;
    
    @Parameter( defaultValue = "${project}", readonly = true )
    private MavenProject project;
    
    /**
     *
     * @throws MojoExecutionException
     */
    @Override
    public void execute() throws MojoExecutionException {

        List<URL> urls = new ArrayList();

        try {
            if (jarPath != null && !jarPath.isEmpty()) {
                logInfo("Use passed jar [" + jarPath + "]");
                File jar = new File(jarPath);
                if (jar.exists()) {
                    urls.add(jar.toURI().toURL());
                } else {
                    logError("Jar doesn't exist [%s]", null, jarPath);
                }
            } else {
                logInfo("Use classpath");
                // Loading project classpath to retrieve class
                for (Object ele : project.getRuntimeClasspathElements()) {
                    File jar = new File(ele.toString());
                    urls.add(jar.toURI().toURL());
                }
            }

            // If a libraries folder is valorized we load all classes
            if (librariesFolder != null && !librariesFolder.isEmpty()) {
                logInfo("Use jars dependency folder [%s]", librariesFolder);

                File path = new File(librariesFolder);

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
                if (new File(propertiesTemplate).isFile()) {
                    template = readFileToString(propertiesTemplate);
                } else {
                    logError("PropertiesTemplate file not exists [{}], default template will be used", null, propertiesTemplate);
                }
            }
            
            URLClassLoader jarPack = new URLClassLoader(urls.toArray(new URL[urls.size()]), this.getClass().getClassLoader());
            try {
                Class classToLoad = jarPack.loadClass(configurationClass);

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
        logInfo("Conversion succeded, properties saved [" + outputDirectory + "]");
    }
    
    private String readFileToString(String fileName) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(fileName));
        String output = null;
        try {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            output = sb.toString();
        } finally {
            br.close();
        }
        
        return output;
    }
    
    private void logError(String error, Throwable ex, Object... args) {
        System.out.println(String.format(error + "  [%s]", args, ex.getMessage()));
    }

    private void logInfo(String info, Object... args) {
        System.out.println(String.format(info, args));
    }
}
