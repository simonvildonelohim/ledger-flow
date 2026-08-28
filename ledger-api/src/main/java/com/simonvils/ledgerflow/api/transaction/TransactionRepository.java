package com.simonvils.ledgerflow.api.transaction;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Persistence for {@link Transaction}, over the {@code transactions} table
 * created by {@code V1__create_ledger_and_outbox.sql}.
 *
 * <p>Deliberately plain JDBC rather than JPA. The write path here has to share a
 * transaction with an outbox insert from M3 onward; keeping the SQL explicit
 * makes what actually reaches the database obvious rather than a consequence of
 * lazy loading and flush timing.
 */
@Repository
public class TransactionRepository {

    private static final String INSERT_SQL =
            """
            INSERT INTO transactions
                (id, idempotency_key, account_id, amount_minor, currency, status, created_at)
            VALUES
                (:id, :idempotencyKey, :accountId, :amountMinor, :currency, :status, :createdAt)
            ON CONFLICT (idempotency_key) DO NOTHING
            """;

    private static final String SELECT_COLUMNS =
            "id, idempotency_key, account_id, amount_minor, currency, status, created_at";

    private final JdbcClient jdbcClient;

    public TransactionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Inserts a transaction unless its idempotency key is already present.
     *
     * <p>{@code ON CONFLICT DO NOTHING} rather than a prior lookup, because a
     * read-then-write check does not survive concurrency: two requests carrying
     * the same key can both read "not found" before either writes, and both then
     * insert. The unique constraint is the only thing that actually holds, and
     * letting the database resolve the race is what makes this safe.
     *
     * <p>{@code ON CONFLICT} rather than catching a constraint violation, because
     * in PostgreSQL a failed statement aborts the surrounding transaction: every
     * subsequent command errors until rollback, so the follow-up read to fetch the
     * original row could not run in the same transaction. Suppressing the conflict
     * keeps the transaction usable, which matters once M3 makes this insert part
     * of a larger transactional unit.
     *
     * @return {@code true} if a row was written, {@code false} if the key already
     *     existed and nothing changed
     */
    public boolean insertIfAbsent(Transaction transaction) {
        int rowsWritten =
                jdbcClient
                        .sql(INSERT_SQL)
                        .param("id", transaction.id())
                        .param("idempotencyKey", transaction.idempotencyKey())
                        .param("accountId", transaction.accountId())
                        .param("amountMinor", transaction.amountMinor())
                        .param("currency", transaction.currency())
                        .param("status", transaction.status().name())
                        // pgjdbc binds OffsetDateTime to TIMESTAMPTZ directly; Instant is
                        // not a supported parameter type, hence the explicit conversion.
                        .param(
                                "createdAt",
                                OffsetDateTime.ofInstant(transaction.createdAt(), ZoneOffset.UTC))
                        .update();

        return rowsWritten == 1;
    }

    /** Returns the transaction with this id, or empty if there is none. */
    public Optional<Transaction> findById(UUID id) {
        return jdbcClient
                .sql("SELECT " + SELECT_COLUMNS + " FROM transactions WHERE id = :id")
                .param("id", id)
                .query(TransactionRepository::mapRow)
                .optional();
    }

    /** Returns the transaction recorded under this idempotency key, if any. */
    public Optional<Transaction> findByIdempotencyKey(String idempotencyKey) {
        return jdbcClient
                .sql(
                        "SELECT "
                                + SELECT_COLUMNS
                                + " FROM transactions WHERE idempotency_key = :idempotencyKey")
                .param("idempotencyKey", idempotencyKey)
                .query(TransactionRepository::mapRow)
                .optional();
    }

    private static Transaction mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Transaction(
                rs.getObject("id", UUID.class),
                rs.getString("idempotency_key"),
                rs.getString("account_id"),
                rs.getLong("amount_minor"),
                rs.getString("currency"),
                TransactionStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
