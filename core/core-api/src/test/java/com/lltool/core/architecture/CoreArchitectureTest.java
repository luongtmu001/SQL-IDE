package com.lltool.core.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class CoreArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setUp() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.lltool.core");
    }

    @Test
    @DisplayName("ArchUnit: Core packages must never depend on JavaFX")
    void testCoreDoesNotDependOnJavaFX() {
        noClasses()
                .that().resideInAPackage("com.lltool.core..")
                .should().dependOnClassesThat().resideInAPackage("javafx..")
                .as("Core platform must never depend on JavaFX UI libraries")
                .check(importedClasses);
    }

    @Test
    @DisplayName("ArchUnit: Core packages must never depend on Spring Framework")
    void testCoreDoesNotDependOnSpring() {
        noClasses()
                .that().resideInAPackage("com.lltool.core..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .as("Core platform must not depend on Spring Framework")
                .check(importedClasses);
    }

    @Test
    @DisplayName("ArchUnit: Core packages must never depend on Desktop Application UI layer")
    void testCoreDoesNotDependOnDesktopUI() {
        noClasses()
                .that().resideInAPackage("com.lltool.core..")
                .should().dependOnClassesThat().resideInAPackage("app..")
                .as("Core platform must never depend on desktop app layer")
                .check(importedClasses);
    }

    @Test
    @DisplayName("ArchUnit: Domain layer must not depend on Application or API layers")
    void testDomainIndependence() {
        noClasses()
                .that().resideInAPackage("com.lltool.core.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.lltool.core.application..",
                        "com.lltool.core.api.."
                )
                .as("Domain layer must remain purely independent from application and API layers")
                .check(importedClasses);
    }
}

