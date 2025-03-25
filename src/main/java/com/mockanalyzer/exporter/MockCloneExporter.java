package com.mockanalyzer.exporter;

import java.nio.file.Path;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mockanalyzer.model.MockInfo;

public class MockCloneExporter {
     /**
     * 从 mockinfo.json 中读取 MockInfo 列表，并进行克隆检测
     * @param inputPath 输入 mockinfo.json 路径
     * @param outputPath 输出 clone 检测结果路径
     */
    public static void detectFromMockInfo(Path inputPath, String outputPath) {

    }

    /**
     * 对 mock 列表进行 clone 分析（逻辑后续实现）
     */

     
    public static void writeMockCloneToJson(List<MockInfo> mockInfos, String outputPath) {
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
