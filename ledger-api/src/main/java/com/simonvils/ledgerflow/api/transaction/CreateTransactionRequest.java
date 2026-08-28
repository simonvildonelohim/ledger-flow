package com.simonvils.ledgerflow.api.transaction;

import com.simonvils.ledgerflow.api.validation.Iso4217;
import com.simonvils.ledgerflow.api.validation.NonZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Incoming payload for {@code POST /transactions}.
 *
 * <p>Kept separate from {@link Transaction} on purpose. The domain record owns
 * fields the client must never set — the ledger identifier, the status, the
 * acceptance timestamp — and binding a request straight onto it would let a
 * caller submit a transaction that is already {@code SETTLED}.
 *
 * @param accountId   account the transaction belongs to
 * @param amountMinor amount in minor units; negative for a debit, never zero
 * @param currency    ISO-4217 alphabetic code, uppercase
 */
public record CreateTransactionRequest(
        @NotBlank(message = "must not be blank") String accountId,
        @NotNull(message = "must not be null") @NonZero Long amountMinor,
        @NotBlank(message = "must not be blank") @Iso4217 String currency) {}
