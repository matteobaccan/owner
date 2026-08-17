/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Writes the properties file of a mapping interface, from the command line, without running the
 * application it belongs to.
 * <pre>
 *     java -cp app.jar:owner.jar org.aeonbits.owner.TemplateTool com.acme.MyConfig
 *     java -cp app.jar:owner.jar org.aeonbits.owner.TemplateTool --into src/main/resources com.acme.MyConfig com.acme.Other
 * </pre>
 * <p>
 * This is the other half of <a href="https://github.com/matteobaccan/owner/issues/3">#3</a>, open since
 * 2013. The half that needed designing is {@link Accessible#save(File)}, which writes the file <b>keeping
 * the one that is already there</b> - its order, its blank lines, and every comment except the ones the
 * interface owns. This tool is that same writer with a <code>main</code> in front of it, and it exists for
 * a reason worth stating: <code>save</code> is declared on {@link Accessible}, so a configuration that does
 * not extend it - which is most of them, and certainly every one that has no file yet - could not generate
 * anything at all.
 * </p>
 * <p>
 * <b>Only the {@link Config.DefaultValue} of each method is written.</b> No source is read, and that is
 * deliberate: a template is what the code says the configuration is, and a tool that loaded the sources
 * would put the machine it ran on into the file - the environment, a password from a home directory, the
 * host name of somebody's laptop.
 * </p>
 * <p>
 * With <code>--into</code> the file goes where the convention looks for it,
 * <code>&lt;dir&gt;/com/acme/MyConfig.properties</code>, so a directory that is a resources root produces a
 * configuration the library finds with no <code>@Sources</code> at all. Without it, the template goes to
 * standard output and can be redirected - but for one interface only, two of them being two files and not
 * one.
 * </p>
 * <p>
 * Run it twice and the second run keeps what you edited in between: it is the same writer, so a value you
 * changed by hand stays changed, a key you added stays where you put it, and the descriptions come back
 * from the code.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
public final class TemplateTool {

    private static final String INTO = "--into";

    private TemplateTool() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * The tool itself, with its streams given rather than taken, so that it can be tested as anything else
     * is. Kept package-private: what is public here is the command line.
     *
     * @param args the command line arguments.
     * @param out  where a template written to standard output goes.
     * @param err  where everything that is not a template goes.
     * @return the exit code: 0 when every template was written, 1 on a failure, 2 on a misuse.
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        File into = null;
        List<String> names = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (INTO.equals(args[i])) {
                if (++i == args.length) {
                    err.println(INTO + " needs the directory to write into.");
                    return 2;
                }
                into = new File(args[i]);
            } else {
                names.add(args[i]);
            }
        }

        if (names.isEmpty()) {
            usage(err);
            return 2;
        }
        if (into == null && names.size() > 1) {
            err.println("Two configurations are two files: name one, or use " + INTO + " <directory>.");
            return 2;
        }

        for (String name : names) {
            try {
                write(configClass(name), into, out);
            } catch (ClassNotFoundException e) {
                err.println(name + " is not on the classpath. Add the jar or the classes directory of the "
                        + "application to -cp, next to owner's own jar.");
                return 1;
            } catch (IllegalArgumentException e) {
                err.println(e.getMessage());
                return 1;
            } catch (IOException e) {
                err.println("Could not write the template for " + name + ": " + e.getMessage());
                return 1;
            }
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Config> configClass(String name) throws ClassNotFoundException {
        Class<?> found = Class.forName(name, false, Thread.currentThread().getContextClassLoader());
        if (!Config.class.isAssignableFrom(found))
            throw new IllegalArgumentException(name + " does not extend " + Config.class.getName()
                    + ", so it is not a configuration this can write a file for.");
        if (!found.isInterface())
            throw new IllegalArgumentException(name + " is a class; a mapping interface is what carries the "
                    + "keys and the default values.");
        return (Class<? extends Config>) found;
    }

    private static void write(Class<? extends Config> clazz, File into, PrintStream out) throws IOException {
        // the prefix a factory would apply, taken from the system properties so that -Downer.key.prefix
        // reaches the template as it reaches the running configuration
        KeyPrefix prefix = KeyPrefix.from(System.getProperties());

        Properties values = new Properties();
        PropertiesMapper.defaults(values, clazz, prefix);
        Set<String> known = PropertiesFileWriter.keysOf(clazz, prefix);
        PropertiesFileWriter writer = PropertiesFileWriter.describing(clazz, prefix);

        if (into != null) {
            File file = new File(into, clazz.getName().replace('.', '/') + ".properties");
            File directory = file.getParentFile();
            if (directory != null && !directory.isDirectory() && !directory.mkdirs())
                throw new IOException("cannot create " + directory);
            writer.write(file, values, known);
            return;
        }

        // standard output has no file to keep, and no temporary one is made to stand in for it: a
        // configuration written into the system temporary directory is a configuration every local user
        // can read, and a default value is sometimes a password
        out.print(writer.render(values, known));
    }

    private static void usage(PrintStream to) {
        to.println("Writes the properties file of a mapping interface, from its @DefaultValue and");
        to.println("@Description annotations. No source is read: what comes out is what the code says.");
        to.println();
        to.println("  java -cp app.jar:owner.jar " + TemplateTool.class.getName()
                + " [--into <dir>] <interface>...");
        to.println();
        to.println("  --into <dir>   write <dir>/com/acme/MyConfig.properties, which is where the");
        to.println("                 convention looks for it; without it, one template to stdout.");
        to.println();
        to.println("An existing file is kept: its order, its blank lines and the comments that are not");
        to.println("ours survive, exactly as Accessible.save(File) keeps them.");
    }
}
