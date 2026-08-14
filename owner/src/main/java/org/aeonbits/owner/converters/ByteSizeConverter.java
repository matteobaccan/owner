/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.converters;

import org.aeonbits.owner.util.bytesize.ByteSize;
import org.aeonbits.owner.util.bytesize.ByteSizeUnit;
import org.aeonbits.owner.Converter;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.aeonbits.owner.util.Util.blankLookingCharacterIn;
import static org.aeonbits.owner.util.Util.splitNumericAndChar;

/**
 * Reads a {@link org.aeonbits.owner.util.bytesize.ByteSize} from a value such as <code>10 MB</code>.
 * <p>
 * Named with {@link org.aeonbits.owner.Config.ConverterClass} rather than applied automatically:
 * <code>ByteSize</code> is this library's own type, not the JDK's, so a bare number in an existing
 * configuration keeps meaning what it did.
 * </p>
 *
 * @author Stefan Freyr Stefansson
 */
public class ByteSizeConverter implements Converter<ByteSize> {
    /**
     * Built with no arguments: a converter named by {@link org.aeonbits.owner.Config.ConverterClass}
     * is instantiated reflectively, so this constructor is part of the contract rather than an
     * accident of there being no state.
     */
    public ByteSizeConverter() {
    }


    @Override
    public ByteSize convert(Method method, String input) {
        return parse(input);
    }

    private static ByteSize parse(String input){
        String[] parts = splitNumericAndChar(input);
        String value = parts[0];
        String unit = parts[1];

        BigDecimal bdValue = number(input, value);
        ByteSizeUnit bsuUnit = ByteSizeUnit.parse(unit);

        if (bsuUnit == null){
            throw new IllegalArgumentException("Invalid unit string: '" + unit + "'");
        }

        return new ByteSize(bdValue, bsuUnit);
    }

    /**
     * Reads the amount, naming the character responsible when an invisible one is what stops it. A no-break
     * space between the digits and the unit survives trimming and shows nothing on screen, so the message
     * {@link BigDecimal} raises quotes a value that looks entirely correct.
     */
    private static BigDecimal number(String input, String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            String blank = blankLookingCharacterIn(value);
            if (blank != null)
                throw new IllegalArgumentException(String.format(
                        "Could not read the amount in byte size '%s': it contains %s, which reads as a space "
                                + "and is not one. It usually arrives by copying out of a word processor or a "
                                + "web page; replace it with an ordinary space.", input, blank), e);
            throw new IllegalArgumentException(
                    String.format("Could not read the amount in byte size '%s'", input), e);
        }
    }
}
