package com.mockanalyzer.exporter;

import java.nio.file.Path;
import java.nio.file.Paths;
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
import com.mockanalyzer.model.MockInfo;
import com.mockanalyzer.visitor.EnhancedProjectResolver;

public class ManualVerify {
    public static void main(String[] args) throws Exception {
        manualVerifyProcess();
        manualVerifySolver();
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
