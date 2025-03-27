package com.mockanalyzer.manual;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mockanalyzer.cloneDetector.MockCloneDetector;
import com.mockanalyzer.model.MockCloneInstance;
import com.mockanalyzer.model.MockInfo;
import com.mockanalyzer.model.MockSequence;
import com.mockanalyzer.exporter.MockInfoExporter;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class MockCloneManualVerify {
    public static void main(String[] args) throws Exception {
        String projectName = "dubbo";

        verifyCloneInstances("C:\\java tool\\Apache\\" + projectName,
                "C:\\Users\\10590\\OneDrive - stevens.edu\\PHD\\2025 Spring\\mock clone detection\\mock-clone-analyzer\\Example Test File\\"
                        + projectName + "_clones.json",
                false);
    }

    private static void verifyCloneInstances(String inputPath, String outputPath, boolean runCommand)
            throws Exception {

        Path projectRoot = Paths.get(inputPath);
        // Step 1: Analyze project and get all MockInfo
        List<MockInfo> combinedResults = MockInfoExporter.analyzeProject(projectRoot, runCommand);

        // Step 2: Convert to MockSequences
        List<MockSequence> allSequences = new ArrayList<>();
        for (MockInfo mockInfo : combinedResults) {
            List<MockSequence> seqList = mockInfo.toMockSequences();
            allSequences.addAll(seqList);
        }

        Map<String, List<MockCloneInstance>> allInstances = new MockCloneDetector().detect(allSequences);
 

        // Step 4: Output to JSON
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(outputPath), StandardCharsets.UTF_8)) {
            gson.toJson(allInstances, writer);
        }

        System.out.println("Clone detection completed. Result -> " + outputPath);
    }
}