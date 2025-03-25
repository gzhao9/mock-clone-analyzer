package com.mockanalyzer.exporter;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mockanalyzer.model.MockInfo;
import com.mockanalyzer.visitor.EnhancedProjectResolver;
import com.mockanalyzer.visitor.MockCollectorVisitor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 分析整个 Java 项目并输出 MockInfo 列表。
 * 用法:
 * java -jar mock-analyzer.jar -project <pathToProjectRoot> <output.json>
 */
public class MockInfoExporter {

    /**
     * 分析项目并输出 MockInfo 列表到 JSON 文件。
     *
     * @param projectRoot 项目根目录
     * @param outputPath  输出 JSON 文件路径
     * @param runCommand  是否执行构建命令
     */

    public static void export(Path projectRoot, String outputPath, boolean runCommand) {

        if (!Files.exists(projectRoot) || !Files.isDirectory(projectRoot)) {
            System.err.println("Error: Invalid project root -> " + projectRoot);
            System.exit(1);
        }

        try {
            List<MockInfo> combinedResults = analyzeProject(projectRoot, runCommand);
            writeMockInfoToJson(combinedResults, outputPath);
            System.out.println("Analysis completed. Result -> " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<MockInfo> analyzeProject(Path projectRoot, boolean runCommand)
            throws IOException, InterruptedException {
        List<MockInfo> combinedResults = new ArrayList<>();

        // 获取 CombinedTypeSolver
        CombinedTypeSolver combinedSolver = EnhancedProjectResolver.createTypeSolver(projectRoot, runCommand);

        ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setSymbolResolver(new JavaSymbolSolver(combinedSolver));
        JavaParser parser = new JavaParser(parserConfiguration);

        // 收集所有 Java 文件
        List<Path> javaFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(javaFiles::add);
        }

        for (Path javaFile : javaFiles) {
            try {
                ParseResult<CompilationUnit> parseResult = parser.parse(javaFile);

                if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                    CompilationUnit cu = parseResult.getResult().get();

                    // 判断是否包含 mockito 导入
                    boolean hasMockitoImport = cu.findAll(ImportDeclaration.class).stream()
                            .anyMatch(imp -> imp.getNameAsString().startsWith("org.mockito"));

                    if (hasMockitoImport) {
                        MockCollectorVisitor visitor = new MockCollectorVisitor(javaFile.toString());
                        visitor.visit(cu, null);

                        List<MockInfo> mockList = visitor.getFinalMockList();
                        for (MockInfo info : mockList) {
                            info.classContext.filePath = javaFile.toString();
                        }
                        combinedResults.addAll(mockList);
                    }
                } else {
                    System.err.println("[WARN] Parse failed: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("[WARN] Skipping file due to exception: " + javaFile + " - " + e.getMessage());
            }
        }

        return combinedResults;
    }

    public static void writeMockInfoToJson(List<MockInfo> mockInfos, String outputPath) {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(outputPath), StandardCharsets.UTF_8)) {
            gson.toJson(mockInfos, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
