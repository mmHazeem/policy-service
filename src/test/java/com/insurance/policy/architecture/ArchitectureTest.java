package com.insurance.policy.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ArchitectureTest {

    private static final String BASE_PACKAGE = "com.insurance.policy";
    private static final String PROJECT_PACKAGE = "com.insurance.policy..";
    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_ARCHIVES)
                .importPackages(BASE_PACKAGE);
    }

    @Nested
    class LayerIsolation {

        @Test
        void domainLayerMustNotDependOnOtherProjectPackages() {
            noClasses()
                    .that().resideInAnyPackage(PROJECT_PACKAGE + "domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            PROJECT_PACKAGE + "web..",
                            PROJECT_PACKAGE + "config..",
                            PROJECT_PACKAGE + "repository..",
                            PROJECT_PACKAGE + "policy_service..",
                            PROJECT_PACKAGE + "dtos..",
                            PROJECT_PACKAGE + "mapper..",
                            PROJECT_PACKAGE + "exception..",
                            PROJECT_PACKAGE + "outbox..",
                            PROJECT_PACKAGE + "Listener..",
                            PROJECT_PACKAGE + "service..")
                    .because("domain entities must be the innermost layer with zero outward dependencies")
                    .check(classes);
        }

        @Test
        void repositoriesMustNotDependOnBusinessOrPresentationLayers() {
            noClasses()
                    .that().resideInAnyPackage(PROJECT_PACKAGE + "repository..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            PROJECT_PACKAGE + "web..",
                            PROJECT_PACKAGE + "config..",
                            PROJECT_PACKAGE + "policy_service..",
                            PROJECT_PACKAGE + "mapper..",
                            PROJECT_PACKAGE + "dtos..",
                            PROJECT_PACKAGE + "exception..",
                            PROJECT_PACKAGE + "outbox..",
                            PROJECT_PACKAGE + "Listener..",
                            PROJECT_PACKAGE + "service..")
                    .because("repositories are persistence abstractions and should only reference domain entities")
                    .check(classes);
        }

        @Test
        void webLayerMustNotAccessRepositoriesDirectly() {
            noClasses()
                    .that().resideInAnyPackage(PROJECT_PACKAGE + "web..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(PROJECT_PACKAGE + "repository..")
                    .because("controllers must go through service classes, not access repositories directly")
                    .check(classes);
        }

        @Test
        void webLayerMustNotReferenceDomainEntities() {
            noClasses()
                    .that().resideInAnyPackage(PROJECT_PACKAGE + "web..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(PROJECT_PACKAGE + "domain..")
                    .because("controllers must use DTOs, not domain entities, in their API surface")
                    .check(classes);
        }

        @Test
        void configLayerMustNotDependOnWebLayer() {
            noClasses()
                    .that().resideInAnyPackage(PROJECT_PACKAGE + "config..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(PROJECT_PACKAGE + "web..")
                    .because("configuration is infrastructure and must not couple to presentation layer")
                    .check(classes);
        }

        @Test
        void outboxLayerMustNotDependOnWebLayer() {
            noClasses()
                    .that().resideInAnyPackage(PROJECT_PACKAGE + "outbox..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(PROJECT_PACKAGE + "web..")
                    .because("the outbox pattern is infrastructure and must not depend on the presentation layer")
                    .check(classes);
        }
    }

    @Nested
    class NamingConventions {

        @Test
        void repositoryClassesShouldEndWithRepository() {
            classes()
                    .that().resideInAnyPackage(PROJECT_PACKAGE + "repository..")
                    .should().haveSimpleNameEndingWith("Repository")
                    .because("consistent naming makes the codebase navigable and predictable")
                    .check(classes);
        }

        @Test
        void webClassesShouldEndWithController() {
            classes()
                    .that().resideInAnyPackage(PROJECT_PACKAGE + "web..")
                    .should().haveSimpleNameEndingWith("Controller")
                    .because("web layer classes are REST controllers")
                    .check(classes);
        }

        @Test
        void mapperInterfacesShouldEndWithMapper() {
            classes()
                    .that().resideInAnyPackage(PROJECT_PACKAGE + "mapper..")
                    .and().areInterfaces()
                    .should().haveSimpleNameEndingWith("Mapper")
                    .because("MapStruct mapper interfaces follow a standard naming convention")
                    .check(classes);
        }

        @Test
        void exceptionClassesShouldFollowConvention() {
            classes()
                    .that().resideInAnyPackage(PROJECT_PACKAGE + "exception..")
                    .and().areTopLevelClasses()
                    .should().haveSimpleNameEndingWith("Exception")
                    .orShould().haveSimpleNameEndingWith("Error")
                    .orShould().haveSimpleNameEndingWith("Handler")
                    .because("exception package classes must be clearly identifiable")
                    .check(classes);
        }

        @Test
        void serviceClassesShouldEndWithService() {
            classes()
                    .that().resideInAnyPackage(PROJECT_PACKAGE + "policy_service..")
                    .should().haveSimpleNameEndingWith("Service")
                    .orShould().haveSimpleNameEndingWith("ServiceImpl")
                    .because("service classes should be named consistently")
                    .check(classes);
        }
    }

    @Nested
    class AnnotationPresence {

        @Test
        void webClassesShouldBeAnnotatedWithRestController() {
            classes()
                    .that().resideInAnyPackage(PROJECT_PACKAGE + "web..")
                    .should().beAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                    .because("all web layer classes must be @RestController")
                    .check(classes);
        }

        @Test
        void serviceClassesShouldBeAnnotatedWithService() {
            classes()
                    .that().resideInAnyPackage(PROJECT_PACKAGE + "policy_service..")
                    .should().beAnnotatedWith(org.springframework.stereotype.Service.class)
                    .because("service layer classes must carry @Service")
                    .check(classes);
        }

        @Test
        void entitiesShouldBeAnnotatedWithEntity() {
            classes()
                    .that().resideInAnyPackage(PROJECT_PACKAGE + "domain..")
                    .and().areTopLevelClasses()
                    .and().areNotEnums()
                    .and().doNotHaveSimpleName("BaseEntity")
                    .should().beAnnotatedWith(jakarta.persistence.Entity.class)
                    .because("domain entities must be JPA @Entity (BaseEntity is @MappedSuperclass)")
                    .check(classes);
        }

        @Test
        void mapperInterfacesShouldBeAnnotatedWithMapper() {
            classes()
                    .that().resideInAnyPackage(PROJECT_PACKAGE + "mapper..")
                    .and().areInterfaces()
                    .should().beAnnotatedWith(org.mapstruct.Mapper.class)
                    .because("MapStruct mapper interfaces require @Mapper annotation")
                    .check(classes);
        }
    }

    @Nested
    class StructuralRules {

        @Test
        void packagesShouldBeFreeOfCycles() {
            slices()
                    .matching("com.insurance.policy.(*)..")
                    .should().beFreeOfCycles()
                    .because("circular dependencies create tight coupling and hinder maintainability")
                    .check(classes);
        }

        @Test
        void exceptionClassesShouldExtendRuntimeException() {
            classes()
                    .that().resideInAnyPackage(PROJECT_PACKAGE + "exception..")
                    .and().haveSimpleNameEndingWith("Exception")
                    .should().beAssignableTo(RuntimeException.class)
                    .because("custom exceptions should extend RuntimeException for consistent handling")
                    .check(classes);
        }
    }

    @Nested
    class CodingStandards {

        @Test
        void utilityClassesShouldHavePrivateConstructors() {
            classes()
                    .that().haveSimpleNameEndingWith("Util")
                    .or().haveSimpleNameEndingWith("Utils")
                    .or().haveSimpleNameEndingWith("Constants")
                    .should().haveOnlyPrivateConstructors()
                    .allowEmptyShould(true)
                    .because("utility classes should not be instantiated")
                    .check(classes);
        }
    }
}
