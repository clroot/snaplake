package com.snaplake

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import io.kotest.core.spec.style.DescribeSpec

class ArchitectureTest : DescribeSpec({

    val classes = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.snaplake")

    describe("레이어 의존성") {
        it("domain → application, adapter, config 의존 금지") {
            noClasses()
                .that().resideInAPackage("com.snaplake.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "com.snaplake.application..",
                    "com.snaplake.adapter..",
                    "com.snaplake.config..",
                )
                .check(classes)
        }

        it("application → adapter 의존 금지") {
            noClasses()
                .that().resideInAPackage("com.snaplake.application..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.snaplake.adapter..")
                .check(classes)
        }

        it("layered architecture 검증") {
            layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy("com.snaplake.domain..")
                .layer("Application").definedBy("com.snaplake.application..")
                .layer("Adapter").definedBy("com.snaplake.adapter..")
                .layer("Config").definedBy("com.snaplake.config..")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter", "Config")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter", "Config")
                // Config는 composition root이므로 Adapter 참조 허용
                .whereLayer("Adapter").mayOnlyBeAccessedByLayers("Config")
                .check(classes)
        }
    }

    describe("domain 프레임워크 격리") {
        it("domain은 Spring에 의존하지 않는다") {
            noClasses()
                .that().resideInAPackage("com.snaplake.domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .check(classes)
        }

        it("domain은 JPA/Hibernate에 의존하지 않는다") {
            noClasses()
                .that().resideInAPackage("com.snaplake.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "jakarta.persistence..",
                    "org.hibernate..",
                )
                .check(classes)
        }
    }

    describe("application port 격리") {
        it("inbound port는 외부 프레임워크에 의존하지 않는다") {
            noClasses()
                .that().resideInAPackage("com.snaplake.application.port..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                )
                .check(classes)
        }
    }
})
