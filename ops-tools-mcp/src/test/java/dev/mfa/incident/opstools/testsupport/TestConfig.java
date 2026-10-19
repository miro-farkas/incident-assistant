package dev.mfa.incident.opstools.testsupport;

import dev.mfa.incident.opstools.config.OpsToolsConfig;
import io.quarkus.runtime.configuration.DurationConverter;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.validator.BeanValidationConfigValidator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

/**
 * Builds {@link OpsToolsConfig} outside Quarkus, with Quarkus duration syntax and validated as at
 * startup.
 */
public final class TestConfig {

  private static final ValidatorFactory VALIDATOR_FACTORY =
      Validation.byDefaultProvider()
          .configure()
          .messageInterpolator(new ParameterMessageInterpolator())
          .buildValidatorFactory();

  private TestConfig() {}

  /** A valid configuration with the given properties added or replaced. */
  public static OpsToolsConfig with(Map<String, String> overrides) {
    Map<String, String> properties = new HashMap<>();
    properties.put("ops.actions.api-key", McpClients.TEST_API_KEY);
    properties.putAll(overrides);
    return build(properties);
  }

  /** Exactly the given properties. */
  public static OpsToolsConfig build(Map<String, String> properties) {
    Validator validator = VALIDATOR_FACTORY.getValidator();
    return new SmallRyeConfigBuilder()
        .withMapping(OpsToolsConfig.class)
        .withConverter(Duration.class, 100, new DurationConverter())
        .withSources(new PropertiesConfigSource(properties, "test", 100))
        .withValidator((BeanValidationConfigValidator) () -> validator)
        .build()
        .getConfigMapping(OpsToolsConfig.class);
  }
}
