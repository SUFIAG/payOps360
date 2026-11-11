package com.payops.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Guards the hexagonal (ports and adapters) structure so it cannot erode.
 */
class HexagonalArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.payops");
    }

    @Test
    void domainIsFrameworkFree() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "org.hibernate..",
                        "com.fasterxml.jackson..", "com.anthropic..")
                .check(classes);
    }

    @Test
    void domainDoesNotDependOnApplicationOrAdapters() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..")
                .check(classes);
    }

    @Test
    void applicationDoesNotDependOnAdapters() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..")
                .check(classes);
    }

    @Test
    void applicationDoesNotUsePersistenceOrWebFrameworks() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..", "org.springframework.data..", "org.springframework.web..",
                        "org.springframework.jdbc..", "jakarta.servlet..", "com.anthropic..")
                .check(classes);
    }

    @Test
    void modulesOnlyTalkThroughPorts() {
        for (String module : new String[]{"identity", "audit", "payment", "provider", "alert", "incident",
                "analytics", "intelligence", "notification"}) {
            noClasses().that().resideOutsideOfPackages("com.payops." + module + "..", "com.payops.bootstrap..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.payops." + module + ".adapter..", "com.payops." + module + ".application.service..")
                    .because("other modules must use " + module + "'s input/output ports, never its adapters or services")
                    .check(classes);
        }
    }

    @Test
    void sharedKernelDoesNotDependOnBusinessModules() {
        noClasses().that().resideInAPackage("com.payops.shared..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.payops.identity..", "com.payops.payment..", "com.payops.provider..", "com.payops.alert..",
                        "com.payops.incident..", "com.payops.analytics..", "com.payops.intelligence..",
                        "com.payops.audit..", "com.payops.notification..")
                .check(classes);
    }

    @Test
    void domainPackagesAreAcyclic() {
        slices().matching("com.payops.(*).domain..").should().beFreeOfCycles().check(classes);
    }
}
