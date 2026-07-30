/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import org.junit.Assume;
import org.junit.Test;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URI;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author Luigi R. Viggiano
 */
public class ReflectionTest {

    @Test
    public void testAvailableWithNonExistentClass() {
        boolean available = Reflection.isClassAvailable("foo.bar.baz.FooBar");
        assertFalse(available);
    }

    @Test
    public void testAvailableWithExistentClass(){
        boolean available = Reflection.isClassAvailable("java.lang.String");
        assertTrue(available);
    }

    @Test
    public void testForNameWithExistentClass() {
        assertSame(String.class, Reflection.forName("java.lang.String"));
    }

    @Test
    public void testForNameWithNonExistentClass() {
        assertNull(Reflection.forName("foo.bar.baz.FooBar"));
    }

    /**
     * In this module Java8SupportImpl is not on the classpath (it lives in the
     * owner-java8 module), so the no-op fallback applies and must return null.
     */
    @Test
    public void testInvokeDefaultMethodWithoutJava8Support() throws Throwable {
        Method method = Runnable.class.getMethod("run");
        assertNull(Reflection.invokeDefaultMethod(new Object(), method, new Object[0]));
    }

    public interface WithDefaultMethod {
        String abstractMethod();

        default String defaultMethod() {
            return "hello";
        }
    }

    /**
     * Simulates the classpath of the owner-java8 module, where a Java8SupportImpl class is
     * available: {@link Reflection} must load it reflectively and delegate to it, instead of
     * using the no-op fallback exercised by the rest of this suite. A minimal stub is compiled
     * in memory and made visible to an isolated copy of the Reflection class.
     */
    @Test
    public void testJava8SupportIsLoadedReflectivelyWhenAvailable() throws Exception {
        String stubName = "org.aeonbits.owner.util.Java8SupportImpl";
        IsolatedClassLoader isolated = new IsolatedClassLoader(ReflectionTest.class.getClassLoader());
        isolated.addClass(stubName, compileInMemory(stubName,
                "package org.aeonbits.owner.util;\n" +
                "import java.lang.reflect.Method;\n" +
                "public class Java8SupportImpl implements Reflection.Java8Support {\n" +
                "    public boolean isDefault(Method method) { return method.isDefault(); }\n" +
                "    public Object invokeDefaultMethod(Object proxy, Method method, Object[] args) {\n" +
                "        return \"invoked \" + method.getName();\n" +
                "    }\n" +
                "}\n"));

        Class<?> reflection = Class.forName("org.aeonbits.owner.util.Reflection", true, isolated);
        assertNotSame(Reflection.class, reflection);

        Method isDefault = reflection.getMethod("isDefault", Method.class);
        assertTrue((Boolean) isDefault.invoke(null, WithDefaultMethod.class.getMethod("defaultMethod")));
        assertFalse((Boolean) isDefault.invoke(null, WithDefaultMethod.class.getMethod("abstractMethod")));

        Method invokeDefaultMethod = reflection.getMethod(
                "invokeDefaultMethod", Object.class, Method.class, Object[].class);
        assertEquals("invoked defaultMethod", invokeDefaultMethod.invoke(
                null, new Object(), WithDefaultMethod.class.getMethod("defaultMethod"), new Object[0]));
    }

    private static byte[] compileInMemory(String className, final String sourceCode) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Assume.assumeTrue("tests are running on a JRE without the java compiler", compiler != null);

        JavaFileObject source = new SimpleJavaFileObject(
                URI.create("string:///" + className.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return sourceCode;
            }
        };

        final ByteArrayOutputStream compiled = new ByteArrayOutputStream();
        JavaFileManager fileManager = new ForwardingJavaFileManager<JavaFileManager>(
                compiler.getStandardFileManager(null, null, null)) {
            @Override
            public JavaFileObject getJavaFileForOutput(
                    Location location, String name, JavaFileObject.Kind kind, FileObject sibling) {
                return new SimpleJavaFileObject(URI.create("mem:///" + name + ".class"), kind) {
                    @Override
                    public OutputStream openOutputStream() {
                        return compiled;
                    }
                };
            }
        };

        // the classpath makes the Reflection.Java8Support interface visible to the compiler
        Boolean success = compiler.getTask(null, fileManager, null,
                asList("-classpath", System.getProperty("java.class.path")), null,
                singletonList(source)).call();
        assertTrue("compilation of the stub failed", success);
        return compiled.toByteArray();
    }

}
