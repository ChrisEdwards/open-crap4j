package com.architester.crap4j.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Exclusions applied before methods are scored. */
public final class Exclusions {
    private static final List<String> DEFAULT_PATH_GLOBS = List.of("**/generated/**");
    private static final List<String> DEFAULT_CLASS_NAME_REGEXES =
            List.of(".*MapperImpl$", "^Dagger.*", "^Hilt_.*", "^AutoValue_.*");
    private static final Exclusions NONE = new Exclusions(List.of(), List.of(), false);
    private static final Exclusions DEFAULTS = new Exclusions(List.of(), List.of(), true);

    private final List<Pattern> pathPatterns;
    private final List<Pattern> classNamePatterns;

    public Exclusions(
            List<String> pathGlobs,
            List<String> classNameRegexes,
            boolean useDefaultExclusions) {
        Objects.requireNonNull(pathGlobs, "pathGlobs");
        Objects.requireNonNull(classNameRegexes, "classNameRegexes");
        List<String> effectiveGlobs = new ArrayList<>(pathGlobs);
        List<String> effectiveRegexes = new ArrayList<>(classNameRegexes);
        if (useDefaultExclusions) {
            effectiveGlobs.addAll(DEFAULT_PATH_GLOBS);
            effectiveRegexes.addAll(DEFAULT_CLASS_NAME_REGEXES);
        }
        pathPatterns = effectiveGlobs.stream()
                .map(Exclusions::compileGlob)
                .toList();
        classNamePatterns = effectiveRegexes.stream().map(Pattern::compile).toList();
    }

    public static Exclusions none() {
        return NONE;
    }

    public static Exclusions defaults() {
        return DEFAULTS;
    }

    boolean excludes(String packageName, JacocoClass jacocoClass) {
        String className = jacocoClass.name();
        int separator = className.lastIndexOf('/');
        String simpleClassName = separator < 0 ? className : className.substring(separator + 1);
        if (classNamePatterns.stream()
                .anyMatch(pattern -> pattern.matcher(simpleClassName).matches())) {
            return true;
        }
        Optional<String> sourcePath = jacocoClass.sourceFile().map(sourceFile ->
                packageName.isEmpty() ? sourceFile : packageName + "/" + sourceFile);
        return sourcePath.isPresent()
                && pathPatterns.stream()
                        .anyMatch(pattern -> pattern.matcher(sourcePath.orElseThrow()).matches());
    }

    private static Pattern compileGlob(String glob) {
        Objects.requireNonNull(glob, "path glob");
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < glob.length(); index++) {
            char current = glob.charAt(index);
            if (current == '*') {
                boolean doubleStar = index + 1 < glob.length() && glob.charAt(index + 1) == '*';
                if (doubleStar) {
                    index++;
                    if (index + 1 < glob.length() && glob.charAt(index + 1) == '/') {
                        regex.append("(?:.*/)?");
                        index++;
                    } else {
                        regex.append(".*");
                    }
                } else {
                    regex.append("[^/]*");
                }
            } else if (current == '?') {
                regex.append("[^/]");
            } else {
                if ("\\.^$|()[]{}+".indexOf(current) >= 0) {
                    regex.append('\\');
                }
                regex.append(current);
            }
        }
        return Pattern.compile(regex.append('$').toString());
    }
}
