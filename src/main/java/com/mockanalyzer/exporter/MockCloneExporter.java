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
        int mockId = 0;
        List<MockSequence> allSequences = new ArrayList<>();
        for (MockInfo mockInfo : combinedResults) {
            mockInfo.mockObjectId = mockId++;
            allSequences.addAll(mockInfo.toMockSequences());
        }

        // Step 3: Detect Clones
        Map<String, List<MockCloneInstance>> cloneMap = new MockCloneDetector().detect(allSequences);
        MockCloneResult cloneResult = new MockCloneResult(combinedResults, cloneMap);
        // Step 4: Write JSON
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(outputPath), StandardCharsets.UTF_8)) {
            gson.toJson(cloneResult, writer);
        }

        System.out.println("Mock clone detection completed. Result -> " + outputPath);
    }
    private static class MockCloneResult{
        private Map<String, List<MockCloneInstance>> detectedMockClones;
        private List<MockInfo> detectedMockObjects;
        public MockCloneResult() {
            this.detectedMockObjects = new ArrayList<>();
            this.detectedMockClones = new HashMap<>();
        }
        public MockCloneResult(List<MockInfo> detectedMockObjects, Map<String, List<MockCloneInstance>> detectedMockClones) {
            this.detectedMockObjects = detectedMockObjects;
            this.detectedMockClones = detectedMockClones;
        }
        public void setDetectedMockObjects(List<MockInfo> detectedMockObjects) {
            this.detectedMockObjects = detectedMockObjects;
        }
        public void setDetectedMockClones(Map<String, List<MockCloneInstance>> detectedMockClones) {
            this.detectedMockClones = detectedMockClones;
        }
    }
}
