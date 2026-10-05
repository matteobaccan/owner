/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.handlers;

import org.junit.Test;

import java.io.Console;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConsolePassphraseTest {

    @Test
    public void passphraseIsZeroedIfSecondPromptThrowsException() {
        Console console = mock(Console.class);
        char[] firstPass = "secretPass123".toCharArray();
        when(console.readPassword("Passphrase: ")).thenReturn(firstPass);
        when(console.readPassword("Again: ")).thenThrow(new RuntimeException("Terminal error"));

        try {
            ConsolePassphrase.ask(console);
            fail("Expected exception when reading second prompt fails");
        } catch (RuntimeException expected) {
            char[] expectedZeroes = new char[firstPass.length];
            Arrays.fill(expectedZeroes, '\u0000');
            assertArrayEquals("The first passphrase array must be zeroed out in memory", expectedZeroes, firstPass);
        }
    }

    @Test
    public void passphraseIsReturnedWhenPromptsSucceed() {
        Console console = mock(Console.class);
        char[] firstPass = "secretPass123".toCharArray();
        char[] secondPass = "secretPass123".toCharArray();
        when(console.readPassword("Passphrase: ")).thenReturn(firstPass);
        when(console.readPassword("Again: ")).thenReturn(secondPass);

        char[] result = ConsolePassphrase.ask(console);
        assertArrayEquals("secretPass123".toCharArray(), result);
    }
}
