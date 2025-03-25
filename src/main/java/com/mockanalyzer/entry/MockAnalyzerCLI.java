package com.mockanalyzer.entry;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.Arrays;

import com.mockanalyzer.exporter.MockInfoExporter;

public class MockAnalyzerCLI {

    public static void main(String[] args) {
        if (args.length < 1) {
            printHelp();
            return;
        }

        String mode = args[0];

        switch (mode) {
            case "info":
                handleInfo(args);
                break;
            case "clone":
                handleClone(args);
                break;
            default:
                System.err.println("Unknown command: " + mode);
                printHelp();
        }
    }

    private static void handleInfo(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: info <projectRoot> <mockinfo.json> [--run]");
            return;
        }

        Path projectRoot = Paths.get(args[1]);
        String outputPath = args[2];
        boolean runCommand = true;
        
        if (Arrays.asList(args).contains("--skip")) {
            runCommand = false;
        }

        if (!Files.exists(projectRoot)) {
            System.err.println("[ERROR] Project path does not exist: " + projectRoot);
            return;
        }

        MockInfoExporter.export(projectRoot, outputPath, runCommand);
    }

    private static void handleClone(String[] args) {

    }

    private static void printHelp() {
        System.out.println("Usage:");
        System.out.println("  java -jar mock-analyzer.jar info <projectRoot> <mockinfo.json> [--skip]");
        System.out.println("  java -jar mock-analyzer.jar clone <mockinfo.json|projectRoot> <clone.json> [--skip]");
    }

}
