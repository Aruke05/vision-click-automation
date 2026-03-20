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
        return loadInternal(configPath, false);
    }

    public AppConfig load(Path path) {
        return loadInternal(path, false);
    }

    public AppConfig loadStrict(Path path) {
        return loadInternal(path, true);
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

    private AppConfig loadInternal(Path path, boolean strict) {
        if (path == null) {
            if (strict) {
                throw new IllegalArgumentException("配置路径不能为空");
            }
            return new AppConfig();
        }
        if (Files.notExists(path)) {
            if (strict) {
                throw new IllegalArgumentException("配置文件不存在: " + path);
            }
            return new AppConfig();
        }
        try {
            if (Files.isDirectory(path)) {
                throw new IllegalArgumentException("配置路径是目录，无法读取文件: " + path);
            }
            if (Files.size(path) <= 0) {
                throw new IllegalArgumentException("配置文件为空: " + path);
            }
            return objectMapper.readValue(path.toFile(), AppConfig.class);
        } catch (Exception e) {
            if (strict) {
                throw new IllegalArgumentException("读取配置失败: " + path + "，" + e.getMessage(), e);
            }
            e.printStackTrace();
            return new AppConfig();
        }
    }
}
