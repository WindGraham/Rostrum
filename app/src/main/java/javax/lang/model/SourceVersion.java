package javax.lang.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Android-compatible SourceVersion shim.
 *
 * The OpenJDK implementation uses Runtime.version(), which is unavailable on
 * Android's java.lang.Runtime. ECJ only needs enum constants and helpers.
 */
public enum SourceVersion {
    RELEASE_0,
    RELEASE_1,
    RELEASE_2,
    RELEASE_3,
    RELEASE_4,
    RELEASE_5,
    RELEASE_6,
    RELEASE_7,
    RELEASE_8,
    RELEASE_9,
    RELEASE_10,
    RELEASE_11,
    RELEASE_12,
    RELEASE_13,
    RELEASE_14,
    RELEASE_15,
    RELEASE_16,
    RELEASE_17;

    private static final SourceVersion LATEST = RELEASE_17;

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch",
        "char", "class", "const", "continue", "default", "do", "double",
        "else", "enum", "extends", "final", "finally", "float", "for",
        "goto", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while",
        "true", "false", "null", "_", "var", "yield", "record", "sealed", "permits"
    ));

    public static SourceVersion latest() {
        return LATEST;
    }

    public static SourceVersion latestSupported() {
        return LATEST;
    }

    public static boolean isIdentifier(CharSequence name) {
        if (name == null || name.length() == 0) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isName(CharSequence name) {
        if (name == null || name.length() == 0) {
            return false;
        }
        String[] parts = name.toString().split("\\.");
        if (parts.length == 0) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || !isIdentifier(part) || isKeyword(part)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isKeyword(CharSequence s) {
        return s != null && KEYWORDS.contains(s.toString());
    }

    public static boolean isName(CharSequence name, SourceVersion version) {
        return isName(name);
    }

    public static boolean isIdentifier(CharSequence name, SourceVersion version) {
        return isIdentifier(name);
    }

    public static boolean isKeyword(CharSequence s, SourceVersion version) {
        return isKeyword(s);
    }
}
