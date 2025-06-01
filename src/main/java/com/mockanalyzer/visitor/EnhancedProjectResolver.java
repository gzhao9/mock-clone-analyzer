package com.mockanalyzer.visitor;

import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class EnhancedProjectResolver {
    public static CombinedTypeSolver createTypeSolver(Path projectRoot, boolean runCommand)
            throws IOException, InterruptedException {
        // 检测项目类型
        boolean isMaven = Files.exists(projectRoot.resolve("pom.xml"));
        boolean isGradle = Files.exists(projectRoot.resolve("build.gradle"))
                || Files.exists(projectRoot.resolve("build.gradle.kts"));

        if (isMaven) {
            if (runCommand) {
                // System.out.println("[INFO] Maven 项目，执行 mvn package...");
                // runCommand("mvn clean package -DskipTests -DfailOnError=false", projectRoot);

                System.out.println("[INFO] 添加依赖到libs，执行 mvn dependency:copy-dependencies...");
                runCommand("mvn --fail-never  test-compile dependency:copy-dependencies -DincludeScope=test -DoutputDirectory=libs -Daggregate ",
                        projectRoot);
            }

            CombinedTypeSolver combinedSolver = new CombinedTypeSolver();
            combinedSolver.add(new ReflectionTypeSolver());

            addAllModuleLibs(projectRoot, combinedSolver);
            return combinedSolver; 

        }
        if (isGradle) {
            if (runCommand) {
                System.out.println("[INFO] 执行 gradlew assemble...");
                runCommand("gradlew.bat assemble -x test --continue", projectRoot);

                ensureGradleCopyLibsTask(projectRoot);
                runCommand("gradlew.bat copyTestLibs", projectRoot);
            }
            CombinedTypeSolver combinedSolver = new CombinedTypeSolver();
            combinedSolver.add(new ReflectionTypeSolver());
            addAllModuleLibs(projectRoot, combinedSolver);
            return combinedSolver;

        }

        System.err.println("[ERROR] 未检测到 Maven 或 Gradle 项目配置。");
        return null;
    }

    private static void ensureGradleCopyLibsTask(Path projectRoot) throws IOException {
        Path buildFile = projectRoot.resolve("build.gradle");

        if (!Files.exists(buildFile)) {
            System.err.println("[WARN] 未找到 build.gradle，跳过注入 copyTestLibs 任务。");
            return;
        }
        boolean taskExists;
        List<String> lines = readFileWithFallback(buildFile);

        taskExists = lines.stream().anyMatch(line -> line.contains("copyTestLibs"));

        if (taskExists) {
            System.out.println("[INFO] 已检测到 copyTestLibs 任务，跳过注入。");
            return;
        }

        String gradleTaskCode = "\n// 自动添加任务：复制 test 运行时依赖到 libs\n" +
                "subprojects {\n" +
                "    afterEvaluate {\n" +
                "        def config = configurations.findByName('testRuntimeClasspath')\n" +
                "        if (config != null && config.canBeResolved) {\n" +
                "            tasks.register('copyTestLibs', Copy) {\n" +
                "                from config\n" +
                "                into rootProject.layout.projectDirectory.dir('libs')\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}\n";

        Files.write(buildFile, gradleTaskCode.getBytes(), StandardOpenOption.APPEND);
        System.out.println("[INFO] 已成功注入 copyTestLibs 任务至 build.gradle");
    }

    private static List<String> readFileWithFallback(Path file) throws IOException {
        Charset[] charsetsToTry = { StandardCharsets.UTF_8, Charset.forName("GBK") };

        for (Charset charset : charsetsToTry) {
            try {
                return Files.readAllLines(file, charset);
            } catch (MalformedInputException e) {
                System.err.println("[WARN] 读取失败，尝试编码: " + charset.name());
            }
        }

        throw new IOException("无法读取文件，所有编码尝试失败: " + file);
    }

    private static void addAllModuleLibs(Path projectRoot, CombinedTypeSolver solver) {
        try {
            Files.walk(projectRoot)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".jar"))
                    .forEach(jarPath -> {
                        try {
                            System.out.println("[INFO] 加载依赖 Jar: " + jarPath);
                            solver.add(new JarTypeSolver(jarPath.toFile()));
                        } catch (IOException e) {
                            System.err.println("[WARN] 无法加载 Jar: " + jarPath + " - " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("[WARN] 遍历项目 jar 文件失败: " + e.getMessage());
        }
    }

    private static void runCommand(String command, Path workingDir) throws IOException, InterruptedException {
        System.out.println("[INFO] 执行命令: " + command);
        List<String> cmdList = new ArrayList<>();
        if (isWindows()) {
            cmdList.add("cmd");
            cmdList.add("/c");
        } else {
            cmdList.add("/bin/sh");
            cmdList.add("-c");
        }
        cmdList.add(command);

        ProcessBuilder pb = new ProcessBuilder(cmdList);
        pb.directory(workingDir.toFile());
        pb.inheritIO();
        Process p = pb.start();
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            System.err.println("[WARN] 命令返回非零退出码: " + exitCode);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}