package io.cranial.platform.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "io.cranial.platform", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryArchTest {

    @ArchTest
    static final ArchRule no_package_cycles = slices()
            .matching("io.cranial.platform.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule platform_core_must_not_depend_on_domain_or_adapters = noClasses()
            .that().resideInAPackage("..platform.platform..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..platform.audit..",
                    "..platform.application..",
                    "..platform.provider..",
                    "..platform.orchestration..",
                    "..platform.document..",
                    "..platform.knowledge..",
                    "..platform.analytics..",
                    "..platform.customer..",
                    "..platform.security..",
                    "..platform.gateway..",
                    "..platform.admin.."
            );

    @ArchTest
    static final ArchRule audit_must_not_depend_on_application_security_gateway_or_admin = noClasses()
            .that().resideInAPackage("..platform.audit..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..platform.application..",
                    "..platform.provider..",
                    "..platform.orchestration..",
                    "..platform.document..",
                    "..platform.knowledge..",
                    "..platform.analytics..",
                    "..platform.customer..",
                    "..platform.security..",
                    "..platform.gateway..",
                    "..platform.admin.."
            );

    @ArchTest
    static final ArchRule application_domain_must_not_depend_on_security_gateway_or_admin = noClasses()
            .that().resideInAPackage("..platform.application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..platform.provider..",
                    "..platform.orchestration..",
                    "..platform.document..",
                    "..platform.knowledge..",
                    "..platform.analytics..",
                    "..platform.customer..",
                    "..platform.security..",
                    "..platform.gateway..",
                    "..platform.admin.."
            );

    @ArchTest
    static final ArchRule provider_must_not_depend_on_gateway_or_admin = noClasses()
            .that().resideInAPackage("..platform.provider..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..platform.orchestration..",
                    "..platform.document..",
                    "..platform.knowledge..",
                    "..platform.analytics..",
                    "..platform.gateway..",
                    "..platform.admin.."
            );

    @ArchTest
    static final ArchRule security_must_not_depend_on_gateway_or_admin = noClasses()
            .that().resideInAPackage("..platform.security..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..platform.orchestration..",
                    "..platform.document..",
                    "..platform.knowledge..",
                    "..platform.analytics..",
                    "..platform.gateway..",
                    "..platform.admin.."
            );

    @ArchTest
    static final ArchRule orchestration_must_not_depend_on_gateway_or_admin = noClasses()
            .that().resideInAPackage("..platform.orchestration..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..platform.document..",
                    "..platform.knowledge..",
                    "..platform.analytics..",
                    "..platform.gateway..",
                    "..platform.admin.."
            );

    @ArchTest
    static final ArchRule document_must_not_depend_on_gateway_or_admin = noClasses()
            .that().resideInAPackage("..platform.document..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..platform.knowledge..",
                    "..platform.analytics..",
                    "..platform.gateway..",
                    "..platform.admin.."
            );

    @ArchTest
    static final ArchRule knowledge_must_not_depend_on_gateway_or_admin = noClasses()
            .that().resideInAPackage("..platform.knowledge..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..platform.analytics..",
                    "..platform.gateway..",
                    "..platform.admin.."
            );

    @ArchTest
    static final ArchRule analytics_must_not_depend_on_gateway_or_admin = noClasses()
            .that().resideInAPackage("..platform.analytics..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..platform.decision..",
                    "..platform.gateway..",
                    "..platform.admin.."
            );

    @ArchTest
    static final ArchRule decision_must_not_depend_on_gateway_or_admin = noClasses()
            .that().resideInAPackage("..platform.decision..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..platform.gateway..",
                    "..platform.admin.."
            );

    @ArchTest
    static final ArchRule customer_must_not_depend_on_gateway_or_admin = noClasses()
            .that().resideInAPackage("..platform.customer..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..platform.gateway..",
                    "..platform.admin.."
            );

    @ArchTest
    static final ArchRule gateway_and_admin_must_be_isolated = noClasses()
            .that().resideInAPackage("..platform.gateway..")
            .should().dependOnClassesThat()
            .resideInAPackage("..platform.admin..");
}

