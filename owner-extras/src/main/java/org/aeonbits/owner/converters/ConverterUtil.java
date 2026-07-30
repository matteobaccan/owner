/*
 * Copyright (c) 2012-2016, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.converters;

/**
 * A utility class for converters.
 *
 * @author Stefan Freyr Stefansson
 */
class ConverterUtil {

    // Suppresses default constructor, ensuring no one instantiate this class.
    private ConverterUtil() {}

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
    static String[] splitNumericAndChar(String input){
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
        return new String[]{input.substring(0,i+1).trim(), input.substring(i + 1).trim()};
    }

}
