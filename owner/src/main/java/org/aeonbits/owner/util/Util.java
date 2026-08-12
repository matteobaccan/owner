/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.util;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;

import static java.lang.String.format;
import static java.net.URLDecoder.decode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;

/**
 * This class contains utility methods used all over the library.
 *
 * @author Luigi R. Viggiano
 */
public abstract class Util {

    /** Abstraction over the source of the current time, to allow overriding it in tests. */
    @FunctionalInterface
    public interface TimeProvider {
        /**
         * Returns the current time in milliseconds.
         *
         * @return the current time in milliseconds.
         */
        long getTime();
    }

    /** Abstraction over system properties and environment access, to allow overriding it in tests. */
    public interface SystemProvider {
        /**
         * Returns the system property for the given key.
         *
         * @param key the system property name.
         * @return the property value, or <code>null</code> if not set.
         */
        String getProperty(String key);

        /**
         * Returns the environment variables.
         *
         * @return a map of the environment variables.
         */
        Map<String, String> getenv();

        /**
         * Returns a copy of the system properties.
         *
         * @return the system properties.
         */
        Properties getProperties();
    }

    static TimeProvider timeProvider = System::currentTimeMillis;

    static SystemProvider system = new SystemProvider() {
        @Override
        public String getProperty(String key) {
            return System.getProperty(key);
        }

        @Override
        public Map<String, String> getenv() {
            return System.getenv();
        }

        @Override
        public Properties getProperties() {
            return (Properties) System.getProperties().clone();
        }
    };

    /** Don't let anyone instantiate this class */
    private Util() {}

    /**
     * Returns a reversed copy of the given list, leaving the original untouched.
     *
     * @param src the list to reverse.
     * @param <T> the type of the elements.
     * @return a new list with the elements in reverse order.
     */
    public static <T> List<T> reverse(List<T> src) {
        List<T> copy = new ArrayList<>(src);
        Collections.reverse(copy);
        return copy;
    }

    /**
     * Returns a reversed copy of the given array, leaving the original untouched.
     *
     * @param array the array to reverse.
     * @param <T>   the type of the elements.
     * @return a new array with the elements in reverse order.
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] reverse(T[] array) {
        T[] copy = array.clone();
        Collections.reverse(asList(copy));
        return copy;
    }

    /**
     * Expands a leading <code>~</code> in the given text to the user home directory, supporting
     * plain, <code>file:</code> and <code>jar:file:</code> prefixes.
     *
     * @param text the text possibly starting with <code>~</code>.
     * @return the text with the user home expanded, or the original text if no expansion applies.
     */
    public static String expandUserHome(String text) {
        if (text.equals("~"))
            return getUserHome();
        if (text.indexOf("~/") == 0 || text.indexOf("file:~/") == 0 || text.indexOf("jar:file:~/") == 0)
            return text.replaceFirst("~/", Matcher.quoteReplacement(getUserHome()) + "/");
        if (text.indexOf("~\\") == 0 || text.indexOf("file:~\\") == 0 || text.indexOf("jar:file:~\\") == 0)
            return text.replaceFirst("~\\\\", Matcher.quoteReplacement(getUserHome()) + "\\\\");
        return text;
    }

    private static String getUserHome() {
        return system.getProperty("user.home");
    }

    /**
     * Replaces backslashes with forward slashes in the given path.
     *
     * @param path the path to fix.
     * @return the path with backslashes replaced by forward slashes.
     */
    public static String fixBackslashesToSlashes(String path) {
        return path.replace('\\', '/');
    }

    /**
     * Replaces spaces with <code>%20</code> in the given path.
     *
     * @param path the path to fix.
     * @return the path with spaces replaced by <code>%20</code>.
     */
    public static String fixSpacesToPercentTwenty(String path) {
        return path.replace(" ", "%20");
    }

    /** ZERO WIDTH SPACE: not a separator at all as far as Java is concerned, and shows nothing on screen. */
    private static final int ZERO_WIDTH_SPACE = 0x200B;

    /** ZERO WIDTH NO-BREAK SPACE, better known as the byte order mark when it leads a file. */
    private static final int ZERO_WIDTH_NO_BREAK_SPACE = 0xFEFF;

    /**
     * Describes the first character of the text that the eye reads as a blank but the parser does not: a
     * no-break space, a narrow one, a zero-width one.
     * <p>
     * They arrive by copying out of a word processor, a web page or a chat window; they survive
     * {@link String#trim()}, which only removes characters up to <code>U+0020</code>; and they are invisible
     * in every editor. A value that fails over one therefore gives the reader nothing whatever to look at,
     * which is why it is worth naming the character rather than only reporting that the value was refused.
     * </p>
     *
     * @param text the text to examine.
     * @return a description naming the character and where it sits, or <code>null</code> if there is none.
     */
    public static String blankLookingCharacterIn(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (looksBlankButIsNot(c))
                return format("%s (U+%04X) at position %d", nameOf(c), (int) c, i);
        }
        return null;
    }

    /**
     * {@link Character#isSpaceChar} covers the Unicode space separators and {@link Character#isWhitespace}
     * excludes the non-breaking ones, so the difference between the two is exactly the set that
     * {@link String#trim()} leaves behind. The zero-width pair are not separators at all, so they have to be
     * named on their own.
     */
    private static boolean looksBlankButIsNot(char c) {
        return (Character.isSpaceChar(c) && !Character.isWhitespace(c))
                || c == ZERO_WIDTH_SPACE
                || c == ZERO_WIDTH_NO_BREAK_SPACE;
    }

    private static String nameOf(char c) {
        String name = Character.getName(c);
        return name == null ? "an unnamed character" : name;
    }

    /**
     * Splits a string into a numeric part and a character part. The input string should conform to the format
     * <code>[numeric_part][char_part]</code> with an optional whitespace between the two parts.
     *
     * The <code>char_part</code> should only contain letters as defined by {@link Character#isLetter(char)} while
     * the <code>numeric_part</code> will be parsed regardless of content.
     *
     * Any whitespace will be trimmed from the beginning and end of both parts, however, the <code>numeric_part</code>
     * can contain whitespaces within it.
     *
     * @param input the string to split.
     *
     * @return an array of two strings.
     */
    public static String[] splitNumericAndChar(String input) {
        // ATTN: String.trim() may not trim all UTF-8 whitespace characters properly.
        // The original implementation used its own unicodeTrim() method that I decided not to include until the need
        // arises. For more information, see:
        // https://github.com/typesafehub/config/blob/v1.3.0/config/src/main/java/com/typesafe/config/impl/ConfigImplUtil.java#L118-L164

        int i = input.length() - 1;
        while (i >= 0) {
            char c = input.charAt(i);
            if (!Character.isLetter(c))
                break;
            i -= 1;
        }
        return new String[]{input.substring(0, i + 1).trim(), input.substring(i + 1).trim()};
    }

    /**
     * Does nothing and returns <code>null</code>; used to silence reporting tools complaining about
     * empty catch blocks while making the intent explicit.
     *
     * @param <T> the expected return type.
     * @return always <code>null</code>.
     */
    public static <T> T ignoreAndReturnNull() {
        // the ignoreAndReturnNull method does absolutely nothing, but it helps to shut up warnings by pmd and other reporting tools
        // complaining about empty catch methods.
        return null;
    }

    /**
     * no operation
     */
    public static void ignore() {
    }

    /**
     * Builds an {@link UnsupportedOperationException} with a formatted message and a cause.
     *
     * @param cause the cause of the exception.
     * @param msg   the message format, as per {@link String#format(String, Object...)}.
     * @param args  the arguments for the message format.
     * @return the built exception.
     */
    public static UnsupportedOperationException unsupported(Throwable cause, String msg, Object... args) {
        return new UnsupportedOperationException(format(msg, withoutCredentials(args)), cause);
    }

    /**
     * Builds an {@link UnsupportedOperationException} with a formatted message.
     *
     * @param msg  the message format, as per {@link String#format(String, Object...)}.
     * @param args the arguments for the message format.
     * @return the built exception.
     */
    public static UnsupportedOperationException unsupported(String msg, Object... args) {
        return new UnsupportedOperationException(format(msg, withoutCredentials(args)));
    }

    /**
     * The text of a source, with any credentials in it replaced.
     * <p>
     * A URI may carry them — <code>https://user:secret@config/app.properties</code> is legal and used — and
     * the source is exactly what a message about a source has to name. The rule that a value never reaches
     * a log does not cover this, the secret being in the <b>source</b> rather than in a value, so it is
     * covered here instead: everything between the scheme and the host becomes <code>***</code>.
     * </p>
     *
     * @param uri the source, may be <code>null</code>.
     * @return the source as text, with no credentials in it.
     */
    public static String hideCredentials(URI uri) {
        if (uri == null) return "null";
        String text = uri.toString();
        // read from the text rather than from getUserInfo(), which is null on the opaque URIs a jar or a
        // relative file path produce - the very ones a message is most likely to be about
        int host = text.indexOf("//");
        if (host < 0) return text;
        int at = text.indexOf('@', host);
        if (at < 0) return text;
        return text.substring(0, host + 2) + "***" + text.substring(at);
    }

    /**
     * Replaces every {@link URI} among the arguments of a message with its credential-free text.
     * <p>
     * Done here, once, rather than at each of the twenty-odd places that name a source in a message: it
     * covers the ones written since as well, which is the property worth having.
     * </p>
     */
    private static Object[] withoutCredentials(Object... args) {
        if (args == null) return null;
        Object[] safe = null;
        for (int i = 0; i < args.length; i++) {
            if (!(args[i] instanceof URI)) continue;
            if (safe == null) safe = args.clone();
            safe[i] = hideCredentials((URI) args[i]);
        }
        return safe == null ? args : safe;
    }

    /**
     * Always throws an {@link AssertionError}; used to satisfy the compiler on branches that can
     * never be reached.
     *
     * @param <T> the expected return type.
     * @return never returns normally.
     */
    public static <T> T unreachableButCompilerNeedsThis() {
        throw new AssertionError("this code should never be reached");
    }

    /**
     * Returns the string representation of the given object, or <code>null</code> if it is <code>null</code>.
     *
     * @param result the object to convert.
     * @return the string representation, or <code>null</code>.
     */
    public static String asString(Object result) {
        if (result == null) return null;
        return String.valueOf(result);
    }

    /**
     * Returns the current time in milliseconds, as provided by the configured {@link TimeProvider}.
     *
     * @return the current time in milliseconds.
     */
    public static long now() {
        return timeProvider.getTime();
    }

    /**
     * Resolves a {@link File} from the given URI, supporting <code>file:</code> and <code>jar:</code>
     * schemes.
     *
     * @param uri the URI to resolve.
     * @return the resolved file, or <code>null</code> if the scheme is not supported.
     */
    public static File fileFromURI(URI uri) {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getSchemeSpecificPart();
            try {
                path = decode(path, UTF_8.name());
                return new File(path);
            } catch (UnsupportedEncodingException cannotHappen) {
                // URLDecoder.decode(String, Charset) does not declare this and would remove the whole
                // try, but it arrived in Java 10 and this library is built for Java 8. Until the baseline
                // moves, the name of a charset that is always present is the closest we get - and it is
                // taken from StandardCharsets rather than written out, so it cannot be misspelt
                return unreachableButCompilerNeedsThis();
            }
        } else if ("jar".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getSchemeSpecificPart();
            try {
                return fileFromURI(path.substring(0, path.indexOf('!')));
            } catch (URISyntaxException e) {
                return ignoreAndReturnNull(/* non critical */);
            }
        }
        return null;
    }

    /**
     * Resolves a {@link File} from the given URI string, retrying with backslashes converted to
     * forward slashes if the initial parse fails.
     *
     * @param uriSpec the URI string to resolve.
     * @return the resolved file, or <code>null</code> if the scheme is not supported.
     * @throws URISyntaxException if the string cannot be parsed as a URI.
     */
    public static File fileFromURI(String uriSpec) throws URISyntaxException {
        try {
            return fileFromURI(new URI(uriSpec));
        } catch (URISyntaxException e) {
            // Perhaps the path contains backslashes
            uriSpec = uriSpec.replace('\\', '/');
            return fileFromURI(new URI(uriSpec));
        }
    }

    /**
     * Returns the configured {@link SystemProvider}.
     *
     * @return the system provider.
     */
    public static SystemProvider system() {
        return system;
    }

    /**
     * Instantiates the given class using its no-argument constructor, whatever its visibility and
     * whatever the visibility of the class itself.
     * <p>
     * A {@link org.aeonbits.owner.Preprocessor}, a {@link org.aeonbits.owner.Converter}, a
     * {@link org.aeonbits.owner.Tokenizer} or a decryptor is an implementation detail of the
     * configuration that names it, and until 2.0.0 all four had to be <code>public</code> — which meant
     * a library using OWNER had to widen its own API to satisfy ours. The class was not being asked to
     * be visible <em>to us</em>, which would be fair: it was being asked to be visible to everybody,
     * since this method lives in a package of its own and "the same package" is therefore never true of
     * anyone else's code. Even a package-private class next to the interface that named it was refused.
     * </p>
     * <p>
     * The access check is lifted for the length of the call. Where it cannot be — a security manager
     * that says no, or a module that does not open the package — the constructor is called as it is and
     * the refusal that follows is the one that was raised before.
     * </p>
     *
     * @param clazz the class to instantiate.
     * @param <T>   the type of the instance.
     * @return a new instance of the given class.
     * @throws UnsupportedOperationException if the class cannot be instantiated.
     */
    public static <T> T newInstance(Class<T> clazz) {
        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor();
            makeAccessible(constructor);
            return constructor.newInstance();
        } catch (Exception e) {
            throw unsupported(e,
                    "Class '%s' cannot be instantiated; see the cause below in the stack trace",
                    clazz.getCanonicalName());
        }
    }

    /**
     * Lifts the access check on a constructor, and says nothing when it cannot: the refusal that
     * matters is the one the caller gets when it goes on to use it, which names the class and carries
     * the original cause. Failing here instead would replace a message about the class somebody wrote
     * with one about a reflection call they did not.
     */
    private static void makeAccessible(Constructor<?> constructor) {
        try {
            constructor.setAccessible(true);
        } catch (RuntimeException notAllowedToLiftIt) {
            // a security manager, or a module that does not open its package to us
        }
    }

    /**
     * Instantiates each of the given classes and appends the instances to the given list.
     *
     * @param classes the classes to instantiate.
     * @param result  the list the new instances are added to.
     * @param <T>     the common type of the instances.
     * @return the same list passed as <code>result</code>, with the new instances appended.
     */
    public static <T> List<T> newInstance(Class<? extends T>[] classes, List<T> result) {
        for (Class<? extends T> clazz : classes)
            result.add(newInstance(clazz));
        return result;
    }
}
