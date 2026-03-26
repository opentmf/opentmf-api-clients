package org.opentmf.api.client.rest.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the REST module does not accidentally depend on reactive-specific classes.
 */
class RestArchitectureTest {

  private final JavaClasses classes = new ClassFileImporter()
      .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
      .importPackages("org.opentmf.api.client.rest");

  @Test
  void restModule_shouldNotDependOnWebFlux() {
    ArchRule rule = ArchRuleDefinition.noClasses()
        .should().dependOnClassesThat()
        .resideInAnyPackage("org.springframework.web.reactive..");
    rule.check(classes);
  }

  @Test
  void restModule_shouldNotDependOnReactor() {
    ArchRule rule = ArchRuleDefinition.noClasses()
        .should().dependOnClassesThat()
        .resideInAnyPackage("reactor..");
    rule.check(classes);
  }

  @Test
  void restModule_shouldNotDependOnReactiveTokenService() {
    ArchRule rule = ArchRuleDefinition.noClasses()
        .should().dependOnClassesThat()
        .haveFullyQualifiedName("org.opentmf.client.reactive.service.api.TokenService");
    rule.check(classes);
  }

  @Test
  void restModule_shouldNotDependOnWebClientUtil() {
    ArchRule rule = ArchRuleDefinition.noClasses()
        .should().dependOnClassesThat()
        .resideInAnyPackage("org.opentmf.client.reactive..");
    rule.check(classes);
  }
}
