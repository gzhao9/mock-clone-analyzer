package com.mockanalyzer.sequencesParser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.mockanalyzer.model.MockInfo;

public class StubbingAnalyzer {
    // 从表达式中提取变量名，例如 mock 对象名
    public static String extractVariableName(Expression expr) {
        if (expr == null) return null;
    
        if (expr.isNameExpr()) {
            return expr.asNameExpr().getNameAsString();
        } else if (expr.isFieldAccessExpr()) {
            return expr.asFieldAccessExpr().getNameAsString();
        } else if (expr.isMethodCallExpr()) {
            return extractVariableName(expr.asMethodCallExpr().getScope().orElse(null));
        }
    
        return null;
    }
    

    // 遍历链式调用，找到 when() 或 given()：
    private static Optional<MethodCallExpr> findWhenOrGivenCall(MethodCallExpr call) {
        MethodCallExpr current = call;
        while (true) {
            String methodName = current.getNameAsString();
            if (methodName.equals("when") || methodName.equals("given")) {
                return Optional.of(current);
            }
            if (current.getScope().isPresent() && current.getScope().get().isMethodCallExpr()) {
                current = current.getScope().get().asMethodCallExpr();
            } else {
                break;
            }
        }
        return Optional.empty();
    }

    // 提取 mock 对象名（when/given 的参数）
    private static Optional<String> extractMockFromWhenOrGiven(MethodCallExpr whenOrGivenCall) {
        if (!whenOrGivenCall.getArguments().isEmpty()) {
            Expression arg = whenOrGivenCall.getArgument(0);
    
            // case 1: when(mock.method())
            if (arg.isMethodCallExpr()) {
                MethodCallExpr innerCall = arg.asMethodCallExpr();
                if (innerCall.getScope().isPresent()) {
                    return Optional.ofNullable(extractVariableName(innerCall.getScope().get()));
                }
            }
    
            //  case 2: when(mock)
            else {
                return Optional.ofNullable(extractVariableName(arg));
            }
        }
        return Optional.empty();
    }
    

    // 整合判断与提取
    public static Optional<String> getStubbingTargetVariable(Expression expr) {
        if (!expr.isMethodCallExpr())
            return Optional.empty();

        try {
            MethodCallExpr call = expr.asMethodCallExpr();
            Optional<MethodCallExpr> whenOrGivenOpt = findWhenOrGivenCall(call);
            if (whenOrGivenOpt.isPresent()) {
                return extractMockFromWhenOrGiven(whenOrGivenOpt.get());
            }
        } catch (Exception e) {
            // Resolve 失败
        }

        return Optional.empty();
    }

    /**
     * 将 BDDMockito 风格方法转换为 Mockito 标准风格方法
     * e.g., given ➜ when, willReturn ➜ doReturn, willReturn ➜ thenReturn
     */
    private static String normalizeMockitoMethodName(String methodName) {
        Map<String, String> methodMap = new HashMap<>();

        // given ➜ when
        methodMap.put("given", "when");

        // BDD ➜ doXXX
        methodMap.put("willAnswer", "doAnswer");
        methodMap.put("willCallRealMethod", "doCallRealMethod");
        methodMap.put("willNothing", "doNothing");
        methodMap.put("willReturn", "doReturn");
        methodMap.put("willThrow", "doThrow");

        // BDD ➜ thenXXX
        methodMap.put("will", "then");
        methodMap.put("willAnswer", "thenAnswer");
        methodMap.put("willCallRealMethod", "thenCallRealMethod");
        methodMap.put("willReturn", "thenReturn");
        methodMap.put("willThrow", "thenThrow");

        return methodMap.getOrDefault(methodName, methodName); // 默认返回原名
    }

    public static String abstractStubbingStatement(Expression expression, MockInfo mock) {

        if (!expression.isMethodCallExpr()) return expression.toString();
    
        try {
            MethodCallExpr fullCall = expression.asMethodCallExpr();
            Deque<String> chain = new ArrayDeque<>();
    
            // Step 1: 找到 when/given 调用
            Optional<MethodCallExpr> whenCallOpt = findWhenOrGivenCall(fullCall);
            MethodCallExpr current = fullCall;
    
            // Step 2: 遍历链式结构
            while (true) {
                String rawMethod = current.getNameAsString();
                String methodName = normalizeMockitoMethodName(rawMethod);
                StringBuilder builder = new StringBuilder();
                builder.append(methodName).append("(");
    
                // 特别处理 when/given：抽象化为完整调用
                if (methodName.equals("when") && current == whenCallOpt.orElse(null)) {
                    Expression whenArg = current.getArgument(0);
    
                    if (whenArg.isMethodCallExpr()) {
                        MethodCallExpr stubMethod = whenArg.asMethodCallExpr();
    
                        // 提取 mock 类型
                        String mockType = mock.mockedClass;
                        if (stubMethod.getScope().isPresent()) {
                            try {
                                mockType = stubMethod.getScope().get().calculateResolvedType().describe();
                            } catch (Exception ignored) {}
                        }
    
                        // 提取参数类型
                        List<String> paramTypes = new ArrayList<>();
                        for (Expression param : stubMethod.getArguments()) {
                            try {
                                paramTypes.add(param.calculateResolvedType().describe());
                            } catch (Exception e) {
                                paramTypes.add(param.toString());
                            }
                        }
    
                        builder.append(mockType)
                               .append(".")
                               .append(stubMethod.getNameAsString())
                               .append("(")
                               .append(String.join(", ", paramTypes))
                               .append(")");
                    } else {
                        // fallback
                        builder.append( mock.mockedClass);
                    }
    
                } else {
                    // 其他调用（如 thenReturn/doThrow），只解析参数类型
                    List<String> argTypes = new ArrayList<>();
                    for (Expression arg : current.getArguments()) {
                        try {
                            argTypes.add(arg.calculateResolvedType().describe());
                        } catch (Exception e) {
                            argTypes.add( mock.mockedClass);
                        }
                    }
                    builder.append(String.join(", ", argTypes));
                }
    
                builder.append(")");
                chain.addFirst(builder.toString());
    
                if (current.getScope().isPresent() && current.getScope().get().isMethodCallExpr()) {
                    current = current.getScope().get().asMethodCallExpr();
                } else {
                    break;
                }
            }
    
            return String.join(".", chain);
    
        } catch (Exception e) {
            return expression.toString();
        }
    }     
}