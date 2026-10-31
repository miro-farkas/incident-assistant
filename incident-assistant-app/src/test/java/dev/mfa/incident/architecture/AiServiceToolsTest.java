package dev.mfa.incident.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import dev.mfa.incident.ai.DiagnosisTools;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;
import java.util.Set;

/**
 * No action tool is reachable from any AI service (P-0006, A-0015): every AI service names its tool
 * provider, which is none or {@link DiagnosisTools} with its read tool allowlist ({@code
 * DiagnosisToolsTest}, {@code DiagnosisAgentLoopTest}), and declares no other tools. The default
 * tool provider supplier would attach any {@code ToolProvider} bean.
 */
@AnalyzeClasses(packages = "dev.mfa.incident", importOptions = ImportOption.DoNotIncludeTests.class)
class AiServiceToolsTest {

  private static final Set<Class<?>> ALLOWED_TOOL_PROVIDERS =
      Set.of(RegisterAiService.NoToolProviderSupplier.class, DiagnosisTools.class);

  @ArchTest
  static final ArchRule aiServicesUseOnlyAllowlistedTools =
      classes().that().areAnnotatedWith(RegisterAiService.class).should(declareOnlyAllowedTools());

  private static ArchCondition<JavaClass> declareOnlyAllowedTools() {
    return new ArchCondition<>("declare only allowlisted tools") {
      @Override
      public void check(JavaClass service, ConditionEvents events) {
        RegisterAiService registration = service.getAnnotationOfType(RegisterAiService.class);
        if (!ALLOWED_TOOL_PROVIDERS.contains(registration.toolProviderSupplier())) {
          events.add(
              SimpleConditionEvent.violated(
                  service,
                  service.getName()
                      + " uses tool provider "
                      + registration.toolProviderSupplier().getName()));
        }
        if (registration.tools().length > 0) {
          events.add(
              SimpleConditionEvent.violated(
                  service, service.getName() + " declares tools in @RegisterAiService"));
        }
        for (JavaMethod method : service.getMethods()) {
          if (method.isAnnotatedWith(ToolBox.class) || method.isAnnotatedWith(McpToolBox.class)) {
            events.add(
                SimpleConditionEvent.violated(
                    method, method.getFullName() + " declares a tool box"));
          }
        }
      }
    };
  }
}
