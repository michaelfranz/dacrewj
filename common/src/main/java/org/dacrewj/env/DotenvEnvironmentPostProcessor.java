package org.dacrewj.env;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple .env loader that runs early in Spring Boot startup and injects
 * key=value pairs from a .env file in the current working directory into the Environment.
 * - Lines starting with # are ignored
 * - Supports KEY=VALUE (VALUE may be quoted with single or double quotes; surrounding quotes are stripped)
 * - Does not override values already present from system properties or real environment variables
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            File envFile = locateEnvFile();
            if (envFile == null) {
                return;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            try (BufferedReader br = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    String key = line.substring(0, eq).trim();
                    String val = line.substring(eq + 1).trim();
                    if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                        val = val.substring(1, val.length() - 1);
                    }
                    // Do not override real env or system properties
                    if (System.getProperty(key) != null || System.getenv(key) != null) {
                        continue;
                    }
                    values.put(key, val);
                }
            }
            if (!values.isEmpty()) {
                MutablePropertySources sources = environment.getPropertySources();
                // Add with high precedence but after system properties/env
                sources.addAfter("systemEnvironment", new MapPropertySource("dotenv", values));
                log.info("Loaded {} entries from .env at {}", values.size(), envFile.getAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("Failed to load .env: {}", e.toString());
        }
    }

    private File locateEnvFile() {
        // 1) current working directory
        File cwdEnv = new File(".env");
        if (cwdEnv.exists() && cwdEnv.isFile()) return cwdEnv;
        // 2) walk up directories to find nearest .env (useful when launching from subdirs)
        File dir = new File(System.getProperty("user.dir", "."));
        int up = 0;
        while (dir != null && up < 5) { // limit depth to avoid scanning entire FS
            File candidate = new File(dir, ".env");
            if (candidate.exists() && candidate.isFile()) return candidate;
            dir = dir.getParentFile();
            up++;
        }
        // 3) try common module locations when running from repo root
        File jiraEnv = new File("jira_ingester/.env");
        if (jiraEnv.exists() && jiraEnv.isFile()) return jiraEnv;
        File agentEnv = new File("agent/.env");
        if (agentEnv.exists() && agentEnv.isFile()) return agentEnv;
        return null;
    }

    @Override
    public int getOrder() {
        // Run after default config file processing starts but before most others. 0 is fine.
        return 0;
    }
}
