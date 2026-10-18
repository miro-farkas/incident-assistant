package dev.mfa.incident.opstools.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Every class compiled from this module, production and test, lives under the module root. */
@AnalyzeClasses(wholeClasspath = true, importOptions = ImportOption.DoNotIncludeArchives.class)
class PackageRootTest {

  @ArchTest
  static final ArchRule classesResideUnderModuleRoot =
      classes().should().resideInAPackage("dev.mfa.incident.opstools..");
}
