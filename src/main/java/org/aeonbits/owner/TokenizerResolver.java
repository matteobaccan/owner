/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.Separator;
import org.aeonbits.owner.Config.Tokenizer;
import org.aeonbits.owner.Config.TokenizerClass;

import java.lang.reflect.Method;

import static org.aeonbits.owner.Util.prohibitInstantiation;
import static org.aeonbits.owner.Util.unsupported;

/**
 * @author Luigi R. Viggiano
 */
abstract class TokenizerResolver {

    TokenizerResolver() {
        prohibitInstantiation();
    }

    private static Tokenizer defaultTokenizer = new SplitAndTrimTokenizer(",");

    static Tokenizer resolveTokenizer(Method targetMethod) {
        Tokenizer methodLevelTokenizer = resolveTokenizerOnMethodLevel(targetMethod);
        if (methodLevelTokenizer != null)
            return methodLevelTokenizer;

        Tokenizer classLevelTokenizer = resolveTokenizerOnClassLevel(targetMethod.getDeclaringClass());
        if (classLevelTokenizer != null)
            return classLevelTokenizer;

        return defaultTokenizer;
    }

    private static Tokenizer resolveTokenizerOnClassLevel(Class<?> declaringClass) {
        Separator separatorAnnotationOnClassLevel = declaringClass.getAnnotation(Separator.class);
        TokenizerClass tokenizerClassAnnotationOnClassLevel = declaringClass.getAnnotation(TokenizerClass.class);

        if (separatorAnnotationOnClassLevel != null && tokenizerClassAnnotationOnClassLevel != null)
            throw unsupported(
                    "You cannot specify @Separator and @TokenizerClass both together on class level for '%s'",
                    declaringClass.getCanonicalName());

        if (separatorAnnotationOnClassLevel != null)
            return new SplitAndTrimTokenizer(separatorAnnotationOnClassLevel.value());

        if (tokenizerClassAnnotationOnClassLevel != null)
            return createTokenizer(tokenizerClassAnnotationOnClassLevel.value());

        return null;
    }

    private static Tokenizer resolveTokenizerOnMethodLevel(Method targetMethod) {
        Separator separatorAnnotationOnMethodLevel = targetMethod.getAnnotation(Separator.class);
        TokenizerClass tokenizerClassAnnotationOnMethodLevel = targetMethod.getAnnotation(TokenizerClass.class);

        if (separatorAnnotationOnMethodLevel != null && tokenizerClassAnnotationOnMethodLevel != null)
            throw unsupported(
                    "You cannot specify @Separator and @TokenizerClass both together on method level for '%s'",
                    targetMethod);

        if (separatorAnnotationOnMethodLevel != null)
            return new SplitAndTrimTokenizer(separatorAnnotationOnMethodLevel.value());

        if (tokenizerClassAnnotationOnMethodLevel != null)
            return createTokenizer(tokenizerClassAnnotationOnMethodLevel.value());

        return null;
    }

    private static Tokenizer createTokenizer(Class<? extends Tokenizer> tokenizerClass) {
        try {
            return tokenizerClass.newInstance();
        } catch (Exception e) {
            throw unsupported(e,
                    "Tokenizer class '%s' cannot be instantiated; see the cause below in the stack trace",
                    tokenizerClass.getCanonicalName());
        }
    }
}
