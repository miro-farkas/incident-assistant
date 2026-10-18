package dev.mfa.incident.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

/** JPA stays in {@code persistence} subpackages; domain and services do not see it. */
@AnalyzeClasses(packages = "dev.mfa.incident", importOptions = ImportOption.DoNotIncludeTests.class)
class PersistenceRulesTest {

  private static final DescribedPredicate<JavaClass> JPA_OR_HIBERNATE_ORM =
      resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
          .and(DescribedPredicate.not(resideInAPackage("org.hibernate.validator..")))
          .as("are JPA or Hibernate ORM classes");

  @ArchTest
  static final ArchRule entitiesLiveInPersistencePackages =
      classes().that().areAnnotatedWith(Entity.class).should().resideInAPackage("..persistence..");

  @ArchTest
  static final ArchRule onlyPersistencePackagesUseJpa =
      noClasses()
          .that()
          .resideOutsideOfPackage("..persistence..")
          .should()
          .dependOnClassesThat(JPA_OR_HIBERNATE_ORM);
}
