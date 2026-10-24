package dev.mfa.incident.notification.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;

/**
 * Layering (P-0013 with the {@code client} and {@code channel} packages of A-0011), entity/DTO
 * rules (P-0014) and data access (P-0015) for production code.
 */
@AnalyzeClasses(
    packages = "dev.mfa.incident.notification",
    importOptions = ImportOption.DoNotIncludeTests.class)
class LayerRulesTest {

  private static final DescribedPredicate<JavaClass> ENTITY =
      DescribedPredicate.describe(
          "entities",
          (JavaClass c) ->
              c.isAnnotatedWith(Entity.class) || c.isAnnotatedWith(MappedSuperclass.class));

  private static final DescribedPredicate<JavaClass> JPA_OR_HIBERNATE_ORM =
      resideInAnyPackage(
              "jakarta.persistence..",
              "org.hibernate..",
              "io.quarkus.hibernate.orm.panache..",
              "io.quarkus.panache..")
          .and(DescribedPredicate.not(resideInAPackage("org.hibernate.validator..")))
          .as("are JPA, Hibernate ORM or Panache classes");

  private static final DescribedPredicate<JavaClass> NON_ENUM_MODEL_CLASS =
      resideInAPackage("..model..")
          .and(DescribedPredicate.describe("not enums", (JavaClass c) -> !c.isEnum()))
          .as("model classes other than enums");

  @ArchTest
  static final ArchRule layersRespectAllowedDependencies =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .withOptionalLayers(true)
          .layer("Messaging")
          .definedBy("..messaging..")
          .layer("Service")
          .definedBy("..service..")
          .layer("Client")
          .definedBy("..client..")
          .layer("Channel")
          .definedBy("..channel..")
          .layer("Repository")
          .definedBy("..repository..")
          .layer("Mapper")
          .definedBy("..mapper..")
          .layer("Dto")
          .definedBy("..dto..")
          .layer("Config")
          .definedBy("..config..")
          .layer("Model")
          .definedBy("..model..")
          .whereLayer("Messaging")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Service")
          .mayOnlyBeAccessedByLayers("Messaging")
          .whereLayer("Client")
          .mayOnlyBeAccessedByLayers("Service")
          .whereLayer("Channel")
          .mayOnlyBeAccessedByLayers("Service")
          .whereLayer("Repository")
          .mayOnlyBeAccessedByLayers("Service")
          .whereLayer("Mapper")
          .mayOnlyBeAccessedByLayers("Service")
          .whereLayer("Dto")
          .mayOnlyBeAccessedByLayers("Messaging", "Service", "Channel", "Mapper")
          .whereLayer("Config")
          .mayOnlyBeAccessedByLayers("Messaging", "Service")
          .whereLayer("Model")
          .mayOnlyBeAccessedByLayers("Service", "Channel", "Repository", "Mapper", "Dto");

  @ArchTest
  static final ArchRule dtosAndChannelsUseOnlyModelEnums =
      noClasses()
          .that()
          .resideInAnyPackage("..dto..", "..channel..")
          .should()
          .dependOnClassesThat(NON_ENUM_MODEL_CLASS);

  @ArchTest
  static final ArchRule onlyModelAndRepositoriesUseJpa =
      noClasses()
          .that()
          .resideOutsideOfPackages("..model..", "..repository..")
          .should()
          .dependOnClassesThat(JPA_OR_HIBERNATE_ORM);

  @ArchTest
  static final ArchRule entitiesLiveInModel =
      classes().that(ENTITY).should().resideInAPackage("..model..");

  /** Besides accessors only the row identity of BaseEntity, which is final (P-0014). */
  @ArchTest
  static final ArchRule entitiesHaveOnlyAccessors =
      methods()
          .that()
          .areDeclaredInClassesThat(ENTITY)
          .and()
          .arePublic()
          .and()
          .doNotHaveName("equals")
          .and()
          .doNotHaveName("hashCode")
          .should()
          .haveNameMatching("(get|set|is)[A-Z].*");

  @ArchTest
  static final ArchRule entitiesHaveNoStaticMethods =
      noMethods().that().areDeclaredInClassesThat(ENTITY).should().beStatic();

  @ArchTest
  static final ArchRule servicesDoNotExposeEntities =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAPackage("..service..")
          .and()
          .arePublic()
          .should(neitherTakeNorReturnEntities());

  private static ArchCondition<JavaMethod> neitherTakeNorReturnEntities() {
    return new ArchCondition<>("neither take nor return entities") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        if (ENTITY.test(method.getRawReturnType())) {
          events.add(
              SimpleConditionEvent.violated(method, method.getFullName() + " returns an entity"));
        }
        for (JavaClass parameter : method.getRawParameterTypes()) {
          if (ENTITY.test(parameter)) {
            events.add(
                SimpleConditionEvent.violated(
                    method, method.getFullName() + " takes entity " + parameter.getSimpleName()));
          }
        }
      }
    };
  }
}
