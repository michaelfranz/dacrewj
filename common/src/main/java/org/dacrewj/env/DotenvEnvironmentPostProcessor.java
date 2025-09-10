package org.dacrewj.env;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
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
            var envFiles = locateEnvFiles();
            if (envFiles.isEmpty()) {
                return;
            }
			var values = getEnvVarValues(envFiles);
			if (!values.isEmpty()) {
                var sources = environment.getPropertySources();
                // Add with high precedence but after system properties/env
                sources.addAfter("systemEnvironment", new MapPropertySource("dotenv", values));
				for (File envFile: envFiles) {
					log.info("Loaded {} entries from .env at {}", values.size(), envFile.getAbsolutePath());
				}
            }
        } catch (Exception e) {
            log.warn("Failed to load .env: {}", e.toString());
        }
    }

	private static Map<String, Object> getEnvVarValues(List<File> envFiles) throws IOException {
		var values = new LinkedHashMap<String, Object>();
		for (File envFile: envFiles) {
			try (BufferedReader br = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
				String line;
				while ((line = br.readLine()) != null) {
					Map.Entry<String, Object> entry = getStringObjectEntry(line);
					if (entry == null) continue;
					values.put(entry.getKey(), entry.getValue());
				}
			}
		}
		return values;
	}

	private static Map.Entry<String, Object> getStringObjectEntry(String line) {
		line = line.trim();
		if (line.isEmpty() || line.startsWith("#")) return null;
		int eq = line.indexOf('=');
		if (eq <= 0) return null;
		String key = line.substring(0, eq).trim();
		String val = line.substring(eq + 1).trim();
		if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
			val = val.substring(1, val.length() - 1);
		}
		// Do not override real env or system properties
		if (System.getProperty(key) != null || System.getenv(key) != null) {
			return null;
		}
		return Map.entry(key, val);
	}

	private List<File> locateEnvFiles() {
		return Stream.of(".env", "jira_ingester/.env", "agent/.env")
				.map(File::new)
				.filter(File::exists)
				.filter(File::isFile)
				.toList();
    }

    @Override
    public int getOrder() {
        // Run after default config file processing starts but before most others. 0 is fine.
        return 0;
    }
}
