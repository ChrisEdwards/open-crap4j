package com.architester.crap4j.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LambdaFolder {
    private static final Pattern LAMBDA_NAME = Pattern.compile("^lambda\\$(.+)\\$(\\d+)$");

    private LambdaFolder() {}

    static List<JacocoMethod> fold(List<JacocoMethod> methods) {
        Map<JacocoMethod, JacocoMethod> replacements = new HashMap<>();
        Set<JacocoMethod> removed = new HashSet<>();
        for (JacocoMethod lambda : methods) {
            JacocoMethod target = resolveTarget(lambda, methods, new HashSet<>());
            if (target == null) {
                continue;
            }
            replacements.put(target, merge(replacements.getOrDefault(target, target), lambda));
            removed.add(lambda);
        }
        List<JacocoMethod> folded = new ArrayList<>();
        for (JacocoMethod method : methods) {
            if (!removed.contains(method) && !method.name().equals("<clinit>")) {
                folded.add(replacements.getOrDefault(method, method));
            }
        }
        return folded;
    }

    private static JacocoMethod resolveTarget(
            JacocoMethod lambda, List<JacocoMethod> methods, Set<JacocoMethod> visited) {
        Matcher matcher = LAMBDA_NAME.matcher(lambda.name());
        if (!matcher.matches() || !visited.add(lambda)) {
            return null;
        }
        String targetName = switch (matcher.group(1)) {
            case "new" -> "<init>";
            case "static" -> "<clinit>";
            default -> matcher.group(1);
        };
        List<JacocoMethod> candidates = methods.stream()
                .filter(method -> method.name().equals(targetName))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        List<JacocoMethod> sourceCandidates = candidates.stream()
                .filter(candidate -> !isLambda(candidate))
                .toList();
        if (!sourceCandidates.isEmpty()) {
            return resolveCandidate(lambda, sourceCandidates);
        }
        JacocoMethod transitiveTarget = resolveCandidate(lambda, candidates);
        return resolveTarget(transitiveTarget, methods, visited);
    }

    private static boolean isLambda(JacocoMethod method) {
        return LAMBDA_NAME.matcher(method.name()).matches();
    }

    private static JacocoMethod resolveCandidate(
            JacocoMethod lambda, List<JacocoMethod> candidates) {
        if (candidates.size() == 1
                || lambda.line().isEmpty()
                || candidates.stream().anyMatch(candidate -> candidate.line().isEmpty())) {
            return candidates.get(0);
        }
        JacocoMethod best = null;
        for (JacocoMethod candidate : candidates) {
            if (candidate.line().getAsInt() <= lambda.line().getAsInt()
                    && (best == null
                            || candidate.line().getAsInt() > best.line().getAsInt())) {
                best = candidate;
            }
        }
        return best == null ? candidates.get(0) : best;
    }

    private static JacocoMethod merge(JacocoMethod target, JacocoMethod lambda) {
        Map<CounterType, Counter> counters = new EnumMap<>(CounterType.class);
        for (CounterType type : CounterType.values()) {
            if (type == CounterType.METHOD) {
                Counter targetMethod = target.counters().get(type);
                if (targetMethod != null) {
                    counters.put(type, targetMethod);
                }
                continue;
            }
            Counter targetCounter = target.counters().get(type);
            Counter lambdaCounter = lambda.counters().get(type);
            if (targetCounter != null || lambdaCounter != null) {
                counters.put(type, add(targetCounter, lambdaCounter));
            }
        }
        return new JacocoMethod(target.name(), target.descriptor(), target.line(), counters);
    }

    private static Counter add(Counter left, Counter right) {
        int missed = left == null ? 0 : left.missed();
        int covered = left == null ? 0 : left.covered();
        if (right != null) {
            missed += right.missed();
            covered += right.covered();
        }
        return new Counter(missed, covered);
    }
}
