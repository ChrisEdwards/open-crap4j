package com.architester.crap4j.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Restricts a parsed report to classes whose source file appears in a supplied path list. */
public final class ChangedFileSelector {
    public ChangedFileSelection select(JacocoReport report, List<String> changedFiles) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(changedFiles, "changedFiles");
        List<String> normalized = changedFiles.stream()
                .map(path -> Objects.requireNonNull(path, "changed file").replace('\\', '/'))
                .toList();
        boolean[] matched = new boolean[normalized.size()];
        List<JacocoPackage> selectedPackages = new ArrayList<>();

        for (JacocoPackage jacocoPackage : report.packages()) {
            List<JacocoClass> selectedClasses = new ArrayList<>();
            for (JacocoClass jacocoClass : jacocoPackage.classes()) {
                if (matchesAny(jacocoPackage, jacocoClass, normalized, matched)) {
                    selectedClasses.add(jacocoClass);
                }
            }
            if (!selectedClasses.isEmpty()) {
                selectedPackages.add(new JacocoPackage(jacocoPackage.name(), selectedClasses));
            }
        }

        int skipped = 0;
        for (boolean pathMatched : matched) {
            if (!pathMatched) {
                skipped++;
            }
        }
        return new ChangedFileSelection(
                new JacocoReport(report.name(), selectedPackages), skipped);
    }

    private static boolean matchesAny(
            JacocoPackage jacocoPackage,
            JacocoClass jacocoClass,
            List<String> changedFiles,
            boolean[] matched) {
        if (jacocoClass.sourceFile().isEmpty()) {
            return false;
        }
        String sourceFile = jacocoClass.sourceFile().orElseThrow();
        String suffix = jacocoPackage.name().isEmpty()
                ? sourceFile
                : jacocoPackage.name() + "/" + sourceFile;
        boolean classSelected = false;
        for (int index = 0; index < changedFiles.size(); index++) {
            String changedFile = changedFiles.get(index);
            boolean pathMatches = jacocoPackage.name().isEmpty()
                    ? changedFile.equals(suffix)
                    : changedFile.equals(suffix) || changedFile.endsWith("/" + suffix);
            if (pathMatches) {
                matched[index] = true;
                classSelected = true;
            }
        }
        return classSelected;
    }
}
