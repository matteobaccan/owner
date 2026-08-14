/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.examples;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.handlers.AesGcmHandler;
import org.aeonbits.owner.handlers.RsaHandler;
import org.aeonbits.owner.handlers.ValueHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.HashMap;
import java.util.Map;

/**
 * A value that names what resolves it: <code>${$name::payload}</code>.
 * <p>
 * Three things, in the order somebody meets them: a passphrase, a key pair, and a handler of your own -
 * because the mechanism is not about cryptography, it is about a value naming where it comes from.
 * </p>
 * <p>
 * Run it with <code>java -cp ... org.aeonbits.owner.examples.ValueHandlerExample</code>. It generates its
 * own key material, so it needs nothing set up and leaves nothing behind.
 * </p>
 *
 * @author Matteo Baccan
 * @see org.aeonbits.owner.handlers.ValueHandler
 */
public class ValueHandlerExample {

    /** Nothing to build: the example is in {@link #main(String[])}. */
    public ValueHandlerExample() {
    }

    // ---------------------------------------------------------------- one passphrase

    /**
     * The simple case. Nothing on the interface says "encrypted" - these are ordinary <code>String</code>
     * methods, and <code>jdbcUrl()</code> is built out of the decrypted password by the substitution.
     */
    public interface DatabaseConfig extends Config {
        String password();

        @Key("jdbc.url")
        String jdbcUrl();
    }

    private static void withAPassphrase() {
        // the passphrase comes from wherever the application already keeps it - never from the properties,
        // which would be the secret protecting the file, kept in the file
        AesGcmHandler handler = new AesGcmHandler("the passphrase this application already has");
        ConfigFactory.registerValueHandler(handler);

        Map<String, String> properties = new HashMap<>();
        properties.put("password", handler.markerFor("s3cr3t"));
        properties.put("jdbc.url", "jdbc:h2:mem:test?password=${password}");

        DatabaseConfig config = ConfigFactory.create(DatabaseConfig.class, properties);
        System.out.println("  password() = " + config.password());
        System.out.println("  jdbcUrl()  = " + config.jdbcUrl() + "   <- the secret, not the cipher text");
    }

    // ---------------------------------------------------------------- a key pair

    /** The same interface, read through a handler that holds only the private key. */
    public interface ApiConfig extends Config {
        String token();
    }

    /**
     * With a key pair, adding a secret and reading the others stop being the same permission: whoever
     * writes holds the public key alone.
     */
    private static void withAKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        // a developer, or a CI job: the public key is all they need, and all they get
        RsaHandler writer = new RsaHandler(pair.getPublic());
        String marker = writer.markerFor("a token they can write but not read");

        // the deployment: the private key, registered where the configuration is created
        ConfigFactory.registerValueHandler(new RsaHandler(pair.getPrivate()));

        Map<String, String> properties = new HashMap<>();
        properties.put("token", marker);
        System.out.println("  token()    = " + ConfigFactory.create(ApiConfig.class, properties).token());

        try {
            writer.resolve(marker.substring(marker.indexOf("::") + 2, marker.length() - 1));
        } catch (IllegalArgumentException expected) {
            System.out.println("  and the one who wrote it cannot read it back:");
            System.out.println("    " + expected.getMessage());
        }
    }

    // ---------------------------------------------------------------- a handler of your own

    /**
     * Nothing about the mechanism is specific to cryptography: OWNER reads the envelope and hands the rest
     * to the handler as text. This one reads a file, which is how a secret arrives in a container.
     * <p>
     * A static class rather than an anonymous one, deliberately: a handler is held by every configuration
     * the factory creates and travels in its serialized form, so an anonymous class would drag this example
     * into the graph. See {@link ValueHandler}.
     * </p>
     */
    public static class FileHandler implements ValueHandler {
        private static final long serialVersionUID = 1L;

        @Override
        public String name() {
            return "file";
        }

        @Override
        public String resolve(String path) {
            try {
                return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                // throwing is the contract: answering with the empty string would report a failure as a
                // value, and for a password that is the worst answer available
                throw new IllegalArgumentException("could not read the secret at " + path, e);
            }
        }
    }

    /** The mounted-secret shape: <code>db.password=${$file::/run/secrets/db_password}</code>. */
    public interface MountedConfig extends Config {
        String password();
    }

    private static void withAHandlerOfYourOwn() throws IOException {
        Path secret = Files.createTempFile("owner-example", ".secret");
        Files.write(secret, "read from a file\n".getBytes(StandardCharsets.UTF_8));
        try {
            ConfigFactory.registerValueHandler(new FileHandler());

            Map<String, String> properties = new HashMap<>();
            properties.put("password", "${$file::" + secret.toAbsolutePath() + "}");
            System.out.println("  password() = "
                    + ConfigFactory.create(MountedConfig.class, properties).password());
        } finally {
            Files.deleteIfExists(secret);
        }
    }

    /**
     * Runs the three in turn.
     *
     * @param args ignored; the example generates whatever it needs.
     * @throws Exception when the JDK has no RSA or no temporary directory, neither of which is this
     *                   example's business to handle.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("One passphrase, ${$aes-gcm::...}");
        withAPassphrase();

        System.out.println();
        System.out.println("A key pair, ${$rsa-oaep::...}");
        withAKeyPair();

        System.out.println();
        System.out.println("A handler of your own, ${$file::...}");
        withAHandlerOfYourOwn();
    }
}
