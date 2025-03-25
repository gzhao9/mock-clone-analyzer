package com.mockanalyzer.model;

import java.util.*;

/**
 * 存储 Mock 相关信息
 */

public class MockInfo {
    public String variableName;
    public String variableType;
    public String mockedClass;
    public boolean isReuseableMock = false;
    public String mockPattern = "";

    // 类上下文
    public ClassContext classContext = new ClassContext();

    // locationContext
    public List<StatementInfo> statements = new ArrayList<>();

    public static class ClassContext {
        public String packageName;
        public String filePath;
        public String className;
        // Getter & Setter
    }

    public void checkmockPattern() {
        Map<String, Map<String, Set<String>>> categoryMap = new LinkedHashMap<>() {
            {
                put("creation", new LinkedHashMap<>());
                put("stubbing", new LinkedHashMap<>());
                put("verification", new LinkedHashMap<>());
            }
        };

        boolean isGlobalField = false;

        for (StatementInfo stmt : statements) {

            try {
                stmt.checkLocate();
            } catch (Exception e) {
                stmt.locate = "Other Methods";
            }
            final String type = stmt.type.toUpperCase();
            final String locate = stmt.locate;
            final String methodName = stmt.locationContext.methodName;

            // ===== 属性识别逻辑 =====
            final boolean isFieldOperation = type.equals("FIELD_MOCK_CREATION")
                    || type.equals("FIELD_SPY_CREATION")
                    || type.equals("FIELD_MOCK_CREATION");
            if (isFieldOperation)
                isGlobalField = true;

            // ===== 分类处理逻辑 =====
            switch (type) {
                case "FIELD_DECLARATION":
                case "FIELD_MOCK_CREATION":
                case "FIELD_SPY_CREATION":
                case "DECLARATION":
                case "ASSIGNMENT":
                case "ASSIGNMENT_MOCK":
                case "ASSIGNMENT_SPY":
                case "METHOD_MOCK_CREATION":
                case "METHOD_SPY_CREATION":
                    handleCreation(categoryMap.get("creation"), stmt, isGlobalField);
                    break;

                case "STUBBING":
                    updateLocationCount(categoryMap.get("stubbing"), locate, methodName);
                    break;

                case "VERIFICATION":
                    updateLocationCount(categoryMap.get("verification"), locate, methodName);
                    break;
            }
        }

        this.mockPattern = formatPatternOutput(categoryMap);
    }

    private void handleCreation(
            Map<String, Set<String>> creationMap,
            StatementInfo stmt,
            boolean isGlobalField) {
        String key = null;

        switch (stmt.type.toUpperCase()) {
            case "FIELD_DECLARATION":
                key = "Declaration Attribute";
                break;

            case "FIELD_MOCK_CREATION":
            case "FIELD_SPY_CREATION":
                key = "Attribute Mock Creation";
                break;

            case "METHOD_MOCK_CREATION":
            case "METHOD_SPY_CREATION":
            case "ASSIGNMENT_MOCK":
            case "ASSIGNMENT_SPY":
                if (stmt.locate.equals("@Before")) {
                    key = "Global Init in @Before";
                } else {
                    key = isGlobalField ? String.format("Lazy-init via %s", stmt.locate)
                            : String.format("Local Assignment in %s", stmt.locate);
                }
                break;

            case "DECLARATION":
                // 明确区分本地声明场景
                key = String.format("Local Mock Creation in %s",
                        stmt.locate.equals("Helper Method") ? "Helper" : stmt.locate);
                break;
        }

        if (key != null) {
            updateLocationCount(creationMap, key, stmt.locationContext.methodName);
        }
    }

    private void updateLocationCount(
            Map<String, Set<String>> map,
            String locationType,
            String methodName) {
        Set<String> methods = map.computeIfAbsent(locationType, k -> new HashSet<>());
        methods.add(methodName);
    }

    private String formatPatternOutput(Map<String, Map<String, Set<String>>> categoryMap) {
        StringBuilder sb = new StringBuilder();

        categoryMap.forEach((category, details) -> {
            if (category.equals("creation") &&
                    details.keySet().containsAll(Arrays.asList("Declaration Attribute", "Global Init in @Before")) &&
                    details.size() == 2) {
                // 合并为 Attribute Mock Creation
                Map<String, Set<String>> mergedDetails = new LinkedHashMap<>();
                mergedDetails.put("Attribute Mock Creation", new HashSet<>());
                details = mergedDetails; // 替换原始数据
            }
            sb.append(category.substring(0, 1).toUpperCase())
                    .append(category.substring(1))
                    .append(":\n");

            if (details.isEmpty()) {
                sb.append("— None\n");
            } else {
                details.forEach((desc, methods) -> {
                    // String times = methods.size() > 1 ? "Multiple Times" : "Single Time";
                    sb.append("— ").append(desc).append("\n");
                    // .append(" (").append(times).append(")\n");
                });
            }
            sb.append("\n");
        });

        return sb.toString().trim();
    }

}
