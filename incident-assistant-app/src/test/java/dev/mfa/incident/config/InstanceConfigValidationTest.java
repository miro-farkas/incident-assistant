package dev.mfa.incident.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.smallrye.config.ConfigValidationException;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.validator.BeanValidationConfigValidator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Map;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InstanceConfigValidationTest {

  private static final String ID_PROPERTY = "incident.instance.id";
  private static final int MAX_ID_LENGTH = 63;

  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validatorFactory =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    validatorFactory.close();
  }

  @ParameterizedTest
  @ValueSource(strings = {"local", "incident-assistant-7f9c4d-x2k8p", "host_1.example"})
  void acceptsValidIds(String id) {
    InstanceConfig config = build(Map.of(ID_PROPERTY, id)).getConfigMapping(InstanceConfig.class);

    assertThat(config.id()).isEqualTo(id);
  }

  @Test
  void acceptsIdOfMaximumLength() {
    String id = "a".repeat(MAX_ID_LENGTH);

    assertThat(build(Map.of(ID_PROPERTY, id)).getConfigMapping(InstanceConfig.class).id())
        .isEqualTo(id);
  }

  @ParameterizedTest
  @ValueSource(strings = {"has space", "-leading-dash", "semi;colon", "ümlaut"})
  void rejectsIdsWithInvalidCharacters(String id) {
    assertThatThrownBy(() -> build(Map.of(ID_PROPERTY, id)))
        .isInstanceOf(ConfigValidationException.class)
        .hasMessageContaining(ID_PROPERTY);
  }

  @Test
  void rejectsTooLongId() {
    String id = "a".repeat(MAX_ID_LENGTH + 1);

    assertThatThrownBy(() -> build(Map.of(ID_PROPERTY, id)))
        .isInstanceOf(ConfigValidationException.class)
        .hasMessageContaining(ID_PROPERTY);
  }

  @Test
  void rejectsMissingId() {
    assertThatThrownBy(() -> build(Map.of()))
        .isInstanceOf(ConfigValidationException.class)
        .hasMessageContaining(ID_PROPERTY);
  }

  private static SmallRyeConfig build(Map<String, String> properties) {
    return new SmallRyeConfigBuilder()
        .withMapping(InstanceConfig.class)
        .withSources(new PropertiesConfigSource(properties, "test", 100))
        .withValidator((BeanValidationConfigValidator) () -> validator)
        .build();
  }
}
