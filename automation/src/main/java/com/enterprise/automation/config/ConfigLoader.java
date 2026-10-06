package com.enterprise.automation.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Builds a {@link TestConfig} from layered sources.
 *
 * <p>Precedence, highest first:
 * <ol>
 *   <li>JVM system property, e.g. {@code -Dbrowser=edge}</li>
 *   <li>environment variable, e.g. {@code BROWSER=edge} (CI, Docker)</li>
 *   <li>the environment file, e.g. {@code config/qa.properties}</li>
 *   <li>{@code config/default.properties}</li>
 * </ol>
 *
 * <p>The sources are passed in as maps instead of being read from {@link System} directly, which
 * makes the loader a pure function: unit tests can check every precedence rule without touching
 * global state.
 */
public final class ConfigLoader {

    /** Selects the environment file: {@code -Denv=qa} or {@code ENV=qa}. */
    public static final String ENV_PROPERTY = "env";
    public static final String ENV_VARIABLE = "ENV";
    public static final String DEFAULT_ENVIRONMENT = "local";

    private static final String CONFIG_DIR = "/config/";

    private final Map<String, String> systemProperties;
    private final Map<String, String> environmentVariables;

    public ConfigLoader(Map<String, String> systemProperties, Map<String, String> environmentVariables) {
        this.systemProperties = Map.copyOf(systemProperties);
        this.environmentVariables = Map.copyOf(environmentVariables);
    }

    /** Resolves the environment name, reads its file and returns the merged configuration. */
    public TestConfig load() {
        String environment = firstNonBlank(systemProperties.get(ENV_PROPERTY),
                environmentVariables.get(ENV_VARIABLE), DEFAULT_ENVIRONMENT).trim();

        Properties files = new Properties();
        files.putAll(read("default.properties", true));
        files.putAll(read(environment + ".properties", false));

        Resolver resolver = new Resolver(environment, files);
        return new TestConfig(
                environment,
                resolver.uri(ConfigKey.BASE_URL),
                resolver.uri(ConfigKey.API_BASE_URL),
                BrowserType.from(resolver.required(ConfigKey.BROWSER)),
                Boolean.parseBoolean(resolver.required(ConfigKey.HEADLESS)),
                ExecutionMode.from(resolver.required(ConfigKey.EXECUTION)),
                resolver.uri(ConfigKey.GRID_URL),
                resolver.positiveInt(ConfigKey.WINDOW_WIDTH),
                resolver.positiveInt(ConfigKey.WINDOW_HEIGHT),
                Duration.ofSeconds(resolver.positiveInt(ConfigKey.TIMEOUT_EXPLICIT_SECONDS)),
                Duration.ofSeconds(resolver.positiveInt(ConfigKey.TIMEOUT_PAGE_LOAD_SECONDS)),
                Boolean.parseBoolean(resolver.required(ConfigKey.APP_AUTOSTART)),
                new Credentials(resolver.required(ConfigKey.ADMIN_USERNAME), resolver.required(ConfigKey.ADMIN_PASSWORD)),
                new Credentials(resolver.required(ConfigKey.VIEWER_USERNAME), resolver.required(ConfigKey.VIEWER_PASSWORD)),
                resolver.optional(ConfigKey.DB_URL).map(url -> new DatabaseConfig(url,
                        resolver.optional(ConfigKey.DB_USERNAME).orElse(""),
                        resolver.optional(ConfigKey.DB_PASSWORD).orElse(""))));
    }

    private static Properties read(String fileName, boolean mandatory) {
        Properties properties = new Properties();
        try (InputStream in = ConfigLoader.class.getResourceAsStream(CONFIG_DIR + fileName)) {
            if (in == null) {
                if (mandatory) {
                    throw new IllegalStateException("Missing configuration file " + CONFIG_DIR + fileName);
                }
                // An unknown environment is a typo far more often than intent: fail loudly
                throw new IllegalArgumentException("Unknown environment: no " + CONFIG_DIR + fileName
                        + " on the classpath. Use -Denv=local|qa|staging");
            }
            properties.load(in);
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + CONFIG_DIR + fileName, e);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /** Looks one key up through all layers and converts the value. */
    private final class Resolver {
        private final String environment;
        private final Properties files;

        Resolver(String environment, Properties files) {
            this.environment = environment;
            this.files = files;
        }

        /** The value if any layer sets it; for settings an environment may legitimately lack. */
        Optional<String> optional(ConfigKey key) {
            return Optional.ofNullable(firstNonBlank(
                    systemProperties.get(key.property()),
                    environmentVariables.get(key.environmentVariable()),
                    files.getProperty(key.property()))).map(String::trim);
        }

        String required(ConfigKey key) {
            String value = optional(key).orElse(null);
            if (value == null) {
                throw new IllegalStateException("Configuration '" + key.property() + "' is not set for environment '"
                        + environment + "' (set it in config/" + environment + ".properties, -D"
                        + key.property() + "=... or " + key.environmentVariable() + ")");
            }
            return value;
        }

        URI uri(ConfigKey key) {
            String value = required(key);
            try {
                URI uri = URI.create(value);
                if (uri.getScheme() == null || uri.getHost() == null) {
                    throw new IllegalArgumentException("not an absolute URL");
                }
                return uri;
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Configuration '" + key.property() + "' is not a valid URL: " + value, e);
            }
        }

        int positiveInt(ConfigKey key) {
            String value = required(key);
            try {
                int number = Integer.parseInt(value);
                if (number <= 0) {
                    throw new NumberFormatException("must be positive");
                }
                return number;
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Configuration '" + key.property() + "' must be a positive number: "
                        + value, e);
            }
        }
    }
}
