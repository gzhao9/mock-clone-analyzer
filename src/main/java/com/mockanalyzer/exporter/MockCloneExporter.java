package com.mockanalyzer.exporter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mockanalyzer.cloneDetector.MockCloneDetector;
import com.mockanalyzer.model.MockCloneInstance;
import com.mockanalyzer.model.MockInfo;
import com.mockanalyzer.model.MockSequence;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public class MockCloneExporter {

    /**
     * Analyze a project, extract mock sequences, detect mock clones, and write to JSON.
     *
     * @param projectRoot the root of the Java project
     * @param outputPath path to the output JSON file
     * @param runCommand whether to re-run maven/gradle build
     */
    public static void exportClones(Path projectRoot, String outputPath, boolean runCommand) throws Exception {
        // Step 1: Analyze
        List<MockInfo> combinedResults = MockInfoExporter.analyzeProject(projectRoot, runCommand);

        // Step 2: Flatten all sequences
        List<MockSequence> allSequences = new ArrayList<>();
        for (MockInfo mockInfo : combinedResults) {
            allSequences.addAll(mockInfo.toMockSequences());
        }

        // Step 3: Detect Clones
        Map<String, List<MockCloneInstance>> cloneMap = new MockCloneDetector().detect(allSequences);

        // Step 4: Write JSON
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(outputPath), StandardCharsets.UTF_8)) {
            gson.toJson(cloneMap, writer);
        }

        System.out.println("Mock clone detection completed. Result -> " + outputPath);
    }
}
