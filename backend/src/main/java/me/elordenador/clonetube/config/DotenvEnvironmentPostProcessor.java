package me.elordenador.clonetube.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final List<Path> CANDIDATES = List.of(Path.of(".env"), Path.of("..", ".env"));

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        for (Path candidate : CANDIDATES) {
            if (Files.exists(candidate)) {
                try {
                    Properties values = parse(Files.readAllLines(candidate));
                    environment.getPropertySources().addLast(new PropertiesPropertySource(candidate.toString(), values));
                } catch (IOException e) {
                    throw new IllegalStateException("No se pudo leer el archivo " + candidate, e);
                }
                return;
            }
        }
    }

    static Properties parse(List<String> lines) {
        Properties values = new Properties();
        for (String line : lines) {
            String entry = line.strip();
            if (entry.isEmpty() || entry.startsWith("#")) {
                continue;
            }
            if (entry.startsWith("export ")) {
                entry = entry.substring(7).strip();
            }
            int separator = entry.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = entry.substring(0, separator).strip();
            String value = entry.substring(separator + 1).strip();
            if (value.length() >= 2 && isQuoted(value)) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value);
        }
        return values;
    }

    private static boolean isQuoted(String value) {
        char quote = value.charAt(0);
        return (quote == '"' || quote == '\'') && value.charAt(value.length() - 1) == quote;
    }
}
