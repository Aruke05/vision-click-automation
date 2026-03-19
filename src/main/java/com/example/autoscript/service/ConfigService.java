package com.example.autoscript.service;

import com.example.autoscript.model.AppConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigService {
    private final ObjectMapper objectMapper;
    private final Path configPath;

    public ConfigService() {
        this.objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.configPath = Paths.get(System.getProperty("user.dir"), "config.json")
                .toAbsolutePath()
                .normalize();
    }

    public AppConfig load() {
        return load(configPath);
    }

    public AppConfig load(Path path) {
        if (path == null || Files.notExists(path)) {
            return new AppConfig();
        }
        try {
            return objectMapper.readValue(path.toFile(), AppConfig.class);
        } catch (IOException e) {
            e.printStackTrace();
            return new AppConfig();
        }
    }

    public void save(AppConfig config) {
        save(configPath, config);
    }

    public void save(Path path, AppConfig config) {
        if (path == null) {
            throw new IllegalArgumentException("配置路径不能为空");
        }
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), config);
        } catch (IOException e) {
            throw new RuntimeException("保存配置失败: " + path, e);
        }
    }

    public Path getConfigPath() {
        return configPath;
    }
}
