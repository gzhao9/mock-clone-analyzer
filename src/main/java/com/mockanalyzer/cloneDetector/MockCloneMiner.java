package com.mockanalyzer.cloneDetector;

import com.mockanalyzer.model.MockCloneInstance;
import com.mockanalyzer.model.MockSequence;

import java.util.*;
import java.util.stream.Collectors;

public class MockCloneMiner {

    private static final int MIN_SUPPORT = 2;

    /**
     * Execute full detection pipeline for a single (mockedClass, packageName)
     * group.
     */
    public List<MockCloneInstance> runMutipStubbing(String mockedClass,
            String packageName,
            List<MockSequence> group,
            List<List<String>> abstractedSequences) {

        // Step 2: Run Apriori to mine frequent unordered itemsets
        AprioriMiner miner = new AprioriMiner();
        Map<Set<String>, Set<Integer>> frequentPatterns = miner.mine(abstractedSequences, MIN_SUPPORT);

        // Step 3: Sort patterns by priority score: |Ej| * (|C(Ej)| - 1)
        List<Map.Entry<Set<String>, Set<Integer>>> sortedPatterns = new ArrayList<>(frequentPatterns.entrySet());
        sortedPatterns.sort((a, b) -> {
            int scoreA = a.getKey().size() * (a.getValue().size() - 1);
            int scoreB = b.getKey().size() * (b.getValue().size() - 1);
            return Integer.compare(scoreB, scoreA); // descending
        });

        // Step 4: Greedy assignment of sequences
        Map<Integer, Set<String>> assigned = new HashMap<>(); // sequence index -> assigned pattern
        Map<Set<String>, Set<Integer>> finalAssignments = new LinkedHashMap<>();

        for (Map.Entry<Set<String>, Set<Integer>> entry : sortedPatterns) {
            Set<String> pattern = entry.getKey();
            Set<Integer> candidates = entry.getValue();

            Set<Integer> accepted = new HashSet<>();
            for (Integer idx : candidates) {
                if (!assigned.containsKey(idx)) {
                    assigned.put(idx, pattern);
                    accepted.add(idx);
                }
            }
            if (!accepted.isEmpty()) {
                finalAssignments.put(pattern, accepted);
            }
        }
        // Step 5: Cleanup of low-coverage patterns
        // Step 5: Cleanup low-coverage patterns, reassign freed sequences, with max
        // iteration limit
        int maxIterations = group.size() * 2;
        int iterationCount = 0;
        boolean changed = true;

        while (changed && iterationCount < maxIterations) {
            iterationCount++;
            changed = false;

            // 5.1 找出覆盖数 < 2 的子序列模式
            List<Set<String>> toRemove = new ArrayList<>();
            for (Map.Entry<Set<String>, Set<Integer>> e : finalAssignments.entrySet()) {
                if (e.getValue().size() < 2) {
                    toRemove.add(e.getKey());
                }
            }

            // 如果有模式需要移除，就移除之，并释放它所覆盖的 sequence
            if (!toRemove.isEmpty()) {
                changed = true;
                for (Set<String> pattern : toRemove) {
                    Set<Integer> seqIdxs = finalAssignments.get(pattern);
                    // 释放
                    for (Integer idx : seqIdxs) {
                        assigned.remove(idx);
                    }
                    finalAssignments.remove(pattern);
                }
            }

            // 5.2 尝试对 freed sequences 重新分配
            // 只要 assigned 中不包含 idx，就可分给 sortedPatterns 的模式
            boolean reassigned = false;
            for (Map.Entry<Set<String>, Set<Integer>> entry : sortedPatterns) {
                Set<String> pattern = entry.getKey();
                // 如果这个 pattern 已经在 finalAssignments 中被清理掉，不再重新启用它
                // 如果你想允许被移除的模式再次尝试，也可以删除下面的 if
                if (!finalAssignments.containsKey(pattern)) {
                    continue;
                }

                Set<Integer> candidates = entry.getValue();
                Set<Integer> accepted = finalAssignments.getOrDefault(pattern, new HashSet<>());
                boolean updated = false;
                for (Integer idx : candidates) {
                    if (!assigned.containsKey(idx)) {
                        assigned.put(idx, pattern);
                        accepted.add(idx);
                        updated = true;
                    }
                }
                if (updated) {
                    finalAssignments.put(pattern, accepted);
                    reassigned = true;
                }
            }

            if (reassigned) {
                changed = true;
            }
        }

        // Step 6: Build MockCloneInstance results
        List<MockCloneInstance> results = new ArrayList<>();

        for (Map.Entry<Set<String>, Set<Integer>> entry : finalAssignments.entrySet()) {
            Set<String> sharedStatements = entry.getKey();
            Set<Integer> seqIndices = entry.getValue();

            MockCloneInstance instance = new MockCloneInstance();
            instance.mockedClass = mockedClass;
            instance.packageName = packageName;
            instance.sharedStatements = new ArrayList<>(sharedStatements);
            instance.sequenceCount = seqIndices.size();
            instance.testCaseCount = (int) seqIndices.stream()
                    .map(idx -> group.get(idx).testMethodName + "::" + group.get(idx).className)
                    .distinct().count();
            instance.sharedStatementLineCount = sharedStatements.size();
            instance.locReduced = sharedStatements.size() * (seqIndices.size() - 1);
            instance.sequences = seqIndices.stream().map(group::get).collect(Collectors.toList());

            results.add(instance);
        }

        return results;
    }
}
