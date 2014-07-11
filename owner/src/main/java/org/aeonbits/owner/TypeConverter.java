package org.aeonbits.owner;

import static com.github.drapostolos.typeparser.DynamicParser.TRY_NEXT;
import static org.aeonbits.owner.Converters.METHOD_WITH_CONVERTER_CLASS_ANNOTATION;
import static org.aeonbits.owner.Converters.NULL;
import static org.aeonbits.owner.Converters.PROPERTY_EDITOR;
import static org.aeonbits.owner.Converters.SKIP;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

import com.github.drapostolos.typeparser.DynamicParser;
import com.github.drapostolos.typeparser.NoSuchRegisteredParserException;
import com.github.drapostolos.typeparser.NullStringStrategy;
import com.github.drapostolos.typeparser.NullStringStrategyHelper;
import com.github.drapostolos.typeparser.Parser;
import com.github.drapostolos.typeparser.ParserHelper;
import com.github.drapostolos.typeparser.SplitStrategy;
import com.github.drapostolos.typeparser.SplitStrategyHelper;
import com.github.drapostolos.typeparser.TypeParser;
import com.github.drapostolos.typeparser.TypeParserException;

class TypeConverter {

    private final static Parser<File> fileParser = new Parser<File>() {

        public File parse(String input, ParserHelper helper) {
            return new File(Util.expandUserHome(input));
        }
    };
    private final static NullStringStrategy nullStringStrategy = new NullStringStrategy() {

        public boolean isNullString(String input, NullStringStrategyHelper helper) {
            return input.trim().isEmpty();
        }
    };
    private final static DynamicParser propertyEditorParser = new DynamicParser() {

        public Object parse(String input, ParserHelper helper) {
            Object result = PROPERTY_EDITOR.tryConvert(null, helper.getRawTargetClass(), input);
            return adaptResult(result);
        }
    };

    static Object parseType(String input, Method method) {
        Type targetType = method.getGenericReturnType();
        try {
            return TypeParser.newBuilder()
                    .setNullStringStrategy(nullStringStrategy)
                    .registerParser(File.class, fileParser)
                    .setSplitStrategy(new OwnerSplitStrategyAdapter(method))
                    .registerDynamicParser(new ConverterAnnotationParserAdapter(method))
                    .registerDynamicParser(propertyEditorParser)
                    .build()
                    .parseType(input, targetType);
        } catch (TypeParserException e) {
            String targetTypeName = getTargetTypeName(targetType);
            throw Util.unsupported(e, "Cannot convert '%s' to %s", input, targetTypeName);
        } catch (NoSuchRegisteredParserException e) {
        	String targetTypeName = getTargetTypeName(targetType);
        	throw Util.unsupported(e, "Cannot convert '%s' to %s", input, targetTypeName);
        }
    }

    private static String getTargetTypeName(Type targetType) {
        if (targetType instanceof Class) {
            return ((Class<?>) targetType).getCanonicalName();
        }
        return targetType.toString();
    }

    private static class OwnerSplitStrategyAdapter implements SplitStrategy {

        private final Method targetMethod;

        public OwnerSplitStrategyAdapter(Method targetMethod) {
            this.targetMethod = targetMethod;
        }

        public List<String> split(String input, SplitStrategyHelper helper) {
            Tokenizer t = TokenizerResolver.resolveTokenizer(targetMethod);
            return Arrays.asList(t.tokens(input));
        }

    }

    private static class ConverterAnnotationParserAdapter implements DynamicParser {

        private final Method targetMethod;

        public ConverterAnnotationParserAdapter(Method targetMethod) {
            this.targetMethod = targetMethod;
        }

        public Object parse(String input, ParserHelper helper) {
            Object result = METHOD_WITH_CONVERTER_CLASS_ANNOTATION
                    .tryConvert(targetMethod, null, input);
            return adaptResult(result);
        }

    }

    private static Object adaptResult(Object result) {
        if (result == SKIP) {
            return TRY_NEXT;
        }
        if (result == NULL) {
            return null;
        }
        return result;
    }
}
