package com.buildflow.erp.common.code;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Behaviour of server-side code allocation. Runs against a LOCAL throwaway
 * Postgres; every row is rolled back by {@code @Transactional}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CodeGeneratorTests {

    @Autowired CodeGenerator codeGenerator;
    @Autowired EntityManager entityManager;

    @Test
    void flatSequenceUsesThePrefixAndPadsToThreeDigits() {
        String code = codeGenerator.next(CodeSequence.FOURNISSEUR);
        assertThat(code).matches("^FRN-\\d{3,}$");
    }

    @Test
    void yearlySequenceEmbedsTheCurrentYear() {
        String year = String.valueOf(Year.from(LocalDate.now()).getValue());
        assertThat(codeGenerator.next(CodeSequence.CHANTIER)).matches("^CH-" + year + "-\\d{3,}$");
        assertThat(codeGenerator.next(CodeSequence.ACHAT)).matches("^ACH-" + year + "-\\d{3,}$");
    }

    @Test
    void payslipSequenceEmbedsThePeriodAndRestartsWithIt() {
        String julyFirst = codeGenerator.next(CodeSequence.FICHE_PAIE, "2099-07");
        String julySecond = codeGenerator.next(CodeSequence.FICHE_PAIE, "2099-07");
        String august = codeGenerator.next(CodeSequence.FICHE_PAIE, "2099-08");

        assertThat(julyFirst).isEqualTo("FDP-2099-07-001");
        assertThat(julySecond).isEqualTo("FDP-2099-07-002");
        // A different period is a different counter, so it starts over at 1.
        assertThat(august).isEqualTo("FDP-2099-08-001");
    }

    @Test
    void consecutiveCallsNeverRepeatANumber() {
        List<String> codes = IntStream.range(0, 50)
                .mapToObj(i -> codeGenerator.next(CodeSequence.ARTICLE))
                .toList();

        assertThat(Set.copyOf(codes)).hasSize(50);
    }

    @Test
    void sequenceNeedingADiscriminatorRejectsTheSingleArgumentCall() {
        assertThatThrownBy(() -> codeGenerator.next(CodeSequence.FICHE_PAIE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a discriminator");
    }

    @Test
    void sequenceNeedingADiscriminatorRejectsABlankOne() {
        assertThatThrownBy(() -> codeGenerator.next(CodeSequence.FICHE_PAIE, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The migration seeds each counter above the highest number already in use,
     * so a generated code can never collide with pre-existing data.
     */
    @Test
    void generatedCodeNeverCollidesWithAnExistingFournisseurCode() {
        String code = codeGenerator.next(CodeSequence.FOURNISSEUR);

        Number clashes = (Number) entityManager
                .createNativeQuery("SELECT count(*) FROM fournisseurs WHERE code = :code")
                .setParameter("code", code)
                .getSingleResult();

        assertThat(clashes.longValue()).isZero();
    }

    @Test
    void separateSequencesDoNotShareACounter() {
        String fournisseur = codeGenerator.next(CodeSequence.FOURNISSEUR);
        String sousTraitant = codeGenerator.next(CodeSequence.SOUS_TRAITANT);

        assertThat(fournisseur).startsWith("FRN-");
        assertThat(sousTraitant).startsWith("ST-");
    }

    /** BPU refs come from the client's tender document, so there is no counter. */
    @Test
    void thereIsNoSequenceForBpuLignes() {
        assertThat(java.util.Arrays.stream(CodeSequence.values()).map(Enum::name))
                .doesNotContain("BPU_LIGNE");
    }

    /**
     * Allocation must join the caller's transaction. Outside one it would burn
     * numbers that no entity ever ends up using, so it refuses instead.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void allocationRefusesToRunOutsideATransaction() {
        assertThatThrownBy(() -> codeGenerator.next(CodeSequence.FOURNISSEUR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must run inside the caller's transaction");
    }
}
