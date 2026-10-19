package dev.mfa.incident.opstools.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.quarkiverse.mcp.server.Tool;

/** Layering of the MCP server module (P-0013 applied as recorded in A-0005). */
@AnalyzeClasses(
    packages = "dev.mfa.incident.opstools",
    importOptions = ImportOption.DoNotIncludeTests.class)
class LayerRulesTest {

  @ArchTest
  static final ArchRule layersRespectAllowedDependencies =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .withOptionalLayers(true)
          .layer("Tool")
          .definedBy("..tool..")
          .layer("Security")
          .definedBy("..security..")
          .layer("Service")
          .definedBy("..service..")
          .layer("Dto")
          .definedBy("..dto..")
          .layer("Exception")
          .definedBy("..exception..")
          .layer("Config")
          .definedBy("..config..")
          .whereLayer("Tool")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Security")
          .mayOnlyBeAccessedByLayers("Tool")
          .whereLayer("Service")
          .mayOnlyBeAccessedByLayers("Tool")
          .whereLayer("Dto")
          .mayOnlyBeAccessedByLayers("Tool", "Service")
          .whereLayer("Exception")
          .mayOnlyBeAccessedByLayers("Tool", "Service")
          .whereLayer("Config")
          .mayOnlyBeAccessedByLayers("Security", "Service");

  @ArchTest
  static final ArchRule mcpToolsLiveInTool =
      methods()
          .that()
          .areAnnotatedWith(Tool.class)
          .should()
          .beDeclaredInClassesThat()
          .resideInAPackage("..tool..");

  @ArchTest
  static final ArchRule onlyToolsUseTheMcpApi =
      noClasses()
          .that()
          .resideOutsideOfPackage("..tool..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("io.quarkiverse.mcp..");
}
