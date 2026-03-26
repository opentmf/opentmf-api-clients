package org.opentmf.api.client.reactive.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the reactive module does not accidentally depend on REST-specific classes.
 */
class ReactiveArchitectureTest {

  private final JavaClasses classes = new ClassFileImporter()
      .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
      .importPackages("org.opentmf.api.client.reactive");

  @Test
  void reactiveModule_shouldNotDependOnSyncRestClasses() {
    ArchRule rule = ArchRuleDefinition.noClasses()
        .should().dependOnClassesThat()
        .resideInAnyPackage("org.springframework.web.client..");
    rule.check(classes);
  }

  @Test
  void reactiveModule_shouldNotDependOnSyncTokenService() {
    ArchRule rule = ArchRuleDefinition.noClasses()
        .should().dependOnClassesThat()
        .haveFullyQualifiedName("org.opentmf.client.rest.service.api.SyncTokenService");
    rule.check(classes);
  }

  @Test
  void reactiveModule_shouldNotDependOnRestUtil() {
    ArchRule rule = ArchRuleDefinition.noClasses()
        .should().dependOnClassesThat()
        .resideInAnyPackage("org.opentmf.client.rest..");
    rule.check(classes);
  }
}
