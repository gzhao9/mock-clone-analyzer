package com.mockanalyzer.exporter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mockanalyzer.model.MockInfo;
import com.mockanalyzer.model.MockSequences;
import com.mockanalyzer.visitor.EnhancedProjectResolver;

public class ManualVerify {
    public static void main(String[] args) throws Exception {

        String projectName = "dubbo";

        verifyOutputSequences("C:\\java tool\\Apache\\" + projectName,
                "C:\\Users\\10590\\OneDrive - stevens.edu\\PHD\\2025 Spring\\mock clone detection\\mock-clone-analyzer\\Example Test File\\"
                        + projectName + " sequences.json",
                false);
        // manualVerifyProcess();
        // manualVerifySolver();
    }

    private static void manualVerifyProcess() throws Exception {
        String projectName = "dubbo";
        analyzeWithParameters("C:\\java tool\\Apache\\" + projectName,
                "C:\\Users\\10590\\OneDrive - stevens.edu\\PHD\\2025 Spring\\mock clone detection\\mock-clone-analyzer\\Example Test File\\"
                        + projectName + ".json",
                false, false);
    }

    private static void manualVerifySolver() {
        Path projectRoot = Paths.get("C:\\java tool\\Spring\\spring-integration");
        Path targetFile = Paths.get(
                "C:\\java tool\\Spring\\spring-integration\\spring-integration-core\\src\\test\\java\\org\\springframework\\integration\\channel\\ExecutorChannelTests.java");
        int lineNumber = 180; // 替换成你想要分析的具体行号

        resolveStatement(projectRoot, targetFile, lineNumber, false);
    }

    private static void verifyOutputSequences(String inputPath, String outputPath, boolean runCommand)
            throws Exception {

        Path projectRoot = Paths.get(inputPath);
        // 1. 分析整个项目，得到所有 MockInfo
        List<MockInfo> combinedResults = MockInfoExporter.analyzeProject(projectRoot, runCommand);

        // 2. 对每个 MockInfo 生成 MockSequences 列表
        List<MockSequences> allSequences = new ArrayList<>();
        for (MockInfo mockInfo : combinedResults) {
            // 如果尚未在 mockInfo 内部进行定位/排序，可按需执行：
            // mockInfo.statements.sort(Comparator.comparingInt(s -> s.line));

            // 调用你写好的方法，例如：
            // public List<MockSequences> toMockSequences() { ... }
            List<MockSequences> seqList = mockInfo.toMockSequences();
            allSequences.addAll(seqList);
        }

        // 3. 输出到 JSON
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(outputPath), StandardCharsets.UTF_8)) {
            gson.toJson(allSequences, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Analysis completed. Result -> " + outputPath);
    }

    private static void analyzeWithParameters(String inputPath, String outputPath, boolean outCMD, boolean runCommand)
            throws Exception {
        Path projectRoot = Paths.get(inputPath);
        List<MockInfo> combinedResults = MockInfoExporter.analyzeProject(projectRoot, runCommand);
        if (outCMD) {
            System.out.println(combinedResults);
        } else {
            MockInfoExporter.writeMockInfoToJson(combinedResults, outputPath);
            System.out.println("Analysis completed. Result -> " + outputPath);
        }
    }

    public static void resolveStatement(Path projectRoot, Path targetFile, int lineNumber, boolean runCommand) {
        try {
            CombinedTypeSolver combinedSolver = EnhancedProjectResolver.createTypeSolver(projectRoot, runCommand);

            ParserConfiguration parserConfiguration = new ParserConfiguration();
            parserConfiguration.setSymbolResolver(new JavaSymbolSolver(combinedSolver));
            JavaParser parser = new JavaParser(parserConfiguration);

            System.out.println("[INFO] 解析文件: " + targetFile);
            ParseResult<CompilationUnit> parseResult = parser.parse(targetFile);

            if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
                System.err.println("[ERROR] 解析失败: " + parseResult.getProblems());
                return;
            }

            CompilationUnit cu = parseResult.getResult().get();

            MethodCallExpr targetCall = cu.findAll(MethodCallExpr.class).stream()
                    .filter(mce -> mce.getRange().isPresent()
                            && mce.getRange().get().begin.line <= lineNumber
                            && mce.getRange().get().end.line >= lineNumber)
                    .findFirst()
                    .orElse(null);

            if (targetCall == null) {
                System.out.println("[INFO] 第 " + lineNumber + " 行未找到方法调用。");
                return;
            }

            ResolvedMethodDeclaration resolvedMethod = targetCall.resolve();
            System.out.println("方法名称: " + resolvedMethod.getName());
            System.out.println("方法签名: " + resolvedMethod.getQualifiedSignature());
            System.out.println("所属类: " + resolvedMethod.declaringType().getQualifiedName());
            int paramCount = resolvedMethod.getNumberOfParams();
            for (int i = 0; i < paramCount; i++) {
                ResolvedType paramType = resolvedMethod.getParam(i).getType();
                System.out.println("  - 第 " + (i + 1) + " 个参数类型: " + paramType.describe());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
