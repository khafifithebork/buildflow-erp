package com.buildflow.erp.common.code;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.Year;

@Service
@RequiredArgsConstructor
public class CodeGeneratorImpl implements CodeGenerator {

    /**
     * Claims the next number for a scope in one round trip.
     *
     * <p>{@code next_val} holds the next free number. On first use the INSERT
     * seeds it to 2 and {@code next_val - 1} yields 1; afterwards the UPDATE
     * bumps it and {@code next_val - 1} yields the value it had on entry.
     * Either way exactly one number is handed out, and the row lock held until
     * commit keeps concurrent callers from taking the same one.
     */
    private static final String CLAIM_NEXT = """
            INSERT INTO code_sequences (scope, next_val, updated_at)
            VALUES (:scope, 2, now())
            ON CONFLICT (scope) DO UPDATE
                SET next_val = code_sequences.next_val + 1, updated_at = now()
            RETURNING next_val - 1
            """;

    private final EntityManager entityManager;

    @Override
    public String next(CodeSequence sequence) {
        if (sequence.requiresDiscriminator()) {
            throw new IllegalArgumentException(
                    "Sequence " + sequence + " requires a discriminator; call next(sequence, discriminator)");
        }
        return next(sequence, null);
    }

    @Override
    public String next(CodeSequence sequence, String discriminator) {
        if (sequence.requiresDiscriminator() && (discriminator == null || discriminator.isBlank())) {
            throw new IllegalArgumentException("Sequence " + sequence + " requires a non-blank discriminator");
        }

        // Checked here rather than via @Transactional(MANDATORY): next(sequence)
        // delegates to this method internally, and a self-invocation never goes
        // through the Spring proxy, so the annotation would not fire for it.
        // Outside a transaction the allocation would auto-commit on its own and
        // burn a number even when the caller later fails.
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Code allocation must run inside the caller's transaction; none is active");
        }

        String segment = switch (sequence.reset()) {
            case NEVER -> null;
            case YEARLY -> String.valueOf(Year.from(LocalDate.now()).getValue());
            case PER_DISCRIMINATOR_IN_CODE, PER_DISCRIMINATOR_HIDDEN -> discriminator;
        };

        String scope = segment == null ? sequence.name() : sequence.name() + ":" + segment;

        Number allocated = (Number) entityManager.createNativeQuery(CLAIM_NEXT)
                .setParameter("scope", scope)
                .getSingleResult();

        // The discriminator is part of the code only when it carries meaning to
        // a reader — the payroll period does, the parent chantier's UUID does not.
        boolean printSegment = sequence.reset() == CodeSequence.Reset.YEARLY || sequence.printsDiscriminator();

        return printSegment
                ? "%s-%s-%03d".formatted(sequence.prefix(), segment, allocated.longValue())
                : "%s-%03d".formatted(sequence.prefix(), allocated.longValue());
    }
}
