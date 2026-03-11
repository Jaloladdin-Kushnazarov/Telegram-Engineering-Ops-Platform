package com.engops.platform.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Modul chegaralarini (module boundaries) avtomatik tekshiruvchi testlar.
 *
 * Bu testlar arxitektura qoidalarini kod yozilishi bilan birga tekshiradi.
 * Agar dasturchi modul chegarasini buzadigan kod yozsa — test muvaffaqiyatsiz bo'ladi.
 *
 * Hozirgi qoidalar:
 * 1. shared-kernel boshqa hech qanday modulga bog'liq bo'lmasligi kerak
 * 2. Biznes modullari bir-birining ichki (internal) package'lariga kira olmasligi kerak
 */
class ModuleBoundaryTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        // DO_NOT_INCLUDE_JARS: JDK va kutubxona classlarini import qilmaydi —
        // Java 24 runtime da class file version 68 xatosini oldini oladi.
        // Faqat project classlarini tekshiradi.
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages("com.engops.platform");
    }

    /**
     * shared-kernel moduli boshqa hech qanday biznes moduliga bog'liq bo'lmasligi kerak.
     * U faqat Java standart kutubxonalari, JPA va Spring annotation'lariga murojaat qilishi mumkin.
     */
    @Test
    void sharedKernel_boshqaModullargaBogliqBolmasligi() {
        noClasses()
                .that().resideInAPackage("..sharedkernel..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..identity..",
                        "..tenantconfig..",
                        "..intake..",
                        "..routing..",
                        "..workitem..",
                        "..workflow..",
                        "..telegram..",
                        "..audit..",
                        "..analytics..",
                        "..admin..",
                        "..infrastructure.."
                )
                .because("shared-kernel mustaqil bo'lishi kerak — u boshqa modullarga bog'lanmasligi shart")
                .check(classes);
    }

    /**
     * Telegram moduli biznes qoidalarini o'z ichiga olmasligi kerak.
     * U faqat ko'rsatish (projection) va foydalanuvchi interaksiyasi uchun javobgar.
     */
    @Test
    void telegramModuli_workflowModuligaTogridanTogriKiraOlmasligi() {
        noClasses()
                .that().resideInAPackage("..telegram..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..workflow..")
                .because("Telegram moduli workflow'ga to'g'ridan-to'g'ri bog'lanmasligi kerak — " +
                         "faqat workitem yoki service API orqali ishlashi shart")
                .check(classes);
    }

    /**
     * Infrastructure moduli faqat shared-kernel'dan foydalanishi mumkin,
     * boshqa biznes modullaridan emas.
     */
    @Test
    void infrastructureModuli_faqatSharedKerneldanFoydalanishi() {
        noClasses()
                .that().resideInAPackage("..infrastructure..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..identity..",
                        "..tenantconfig..",
                        "..intake..",
                        "..routing..",
                        "..workitem..",
                        "..workflow..",
                        "..telegram..",
                        "..audit..",
                        "..analytics..",
                        "..admin.."
                )
                .because("Infrastructure moduli biznes modullariga bog'lanmasligi kerak")
                .check(classes);
    }
}