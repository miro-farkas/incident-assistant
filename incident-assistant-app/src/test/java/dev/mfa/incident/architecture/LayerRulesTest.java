package dev.mfa.incident.architecture;

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
import dev.mfa.incident.model.Incident;
import dev.mfa.incident.model.IncidentState;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;

/** Layering (P-0013) and entity/DTO rules (P-0014) for production code. */
@AnalyzeClasses(packages = "dev.mfa.incident", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerRulesTest {

  private static final DescribedPredicate<JavaClass> ENTITY =
      DescribedPredicate.describe(
          "entities",
          (JavaClass c) ->
              c.isAnnotatedWith(Entity.class) || c.isAnnotatedWith(MappedSuperclass.class));

  private static final DescribedPredicate<JavaClass> JPA_OR_HIBERNATE_ORM =
      resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
          .and(DescribedPredicate.not(resideInAPackage("org.hibernate.validator..")))
          .as("are JPA or Hibernate ORM classes");

  private static final DescribedPredicate<JavaClass> NON_ENUM_MODEL_CLASS =
      resideInAPackage("..model..")
          .and(DescribedPredicate.describe("not enums", (JavaClass c) -> !c.isEnum()))
          .as("model classes other than enums");

  @ArchTest
  static final ArchRule layersRespectAllowedDependencies =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .withOptionalLayers(true)
          .layer("Resource")
          .definedBy("..resource..")
          .layer("Messaging")
          .definedBy("..messaging..")
          .layer("Health")
          .definedBy("..health..")
          .layer("Service")
          .definedBy("..service..")
          .layer("Repository")
          .definedBy("..repository..")
          .layer("Mapper")
          .definedBy("..mapper..")
          .layer("Dto")
          .definedBy("..dto..")
          .layer("Exception")
          .definedBy("..exception..")
          .layer("Config")
          .definedBy("..config..")
          .layer("Model")
          .definedBy("..model..")
          .whereLayer("Resource")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Messaging")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Health")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Service")
          .mayOnlyBeAccessedByLayers("Resource", "Messaging", "Health")
          .whereLayer("Repository")
          .mayOnlyBeAccessedByLayers("Service")
          .whereLayer("Mapper")
          .mayOnlyBeAccessedByLayers("Service")
          .whereLayer("Dto")
          .mayOnlyBeAccessedByLayers("Resource", "Messaging", "Health", "Service", "Mapper")
          .whereLayer("Exception")
          .mayOnlyBeAccessedByLayers("Resource", "Messaging", "Service", "Repository")
          .whereLayer("Config")
          .mayOnlyBeAccessedByLayers("Resource", "Messaging", "Health", "Service")
          .whereLayer("Model")
          .mayOnlyBeAccessedByLayers("Service", "Repository", "Mapper", "Dto", "Exception");

  @ArchTest
  static final ArchRule dtosAndExceptionsUseOnlyModelEnums =
      noClasses()
          .that()
          .resideInAnyPackage("..dto..", "..exception..")
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

  @ArchTest
  static final ArchRule entitiesHaveOnlyAccessors =
      methods()
          .that()
          .areDeclaredInClassesThat(ENTITY)
          .and()
          .arePublic()
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

  @ArchTest
  static final ArchRule onlyServicesChangeIncidentState =
      noClasses()
          .that()
          .resideOutsideOfPackage("..service..")
          .should()
          .callMethod(Incident.class, "setState", IncidentState.class);

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
