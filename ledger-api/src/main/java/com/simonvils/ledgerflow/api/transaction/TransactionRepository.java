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
 * <p>Deliberately plain JDBC rather than JPA. The write path here is a single
 * insert that will, from M3, have to share a transaction with an outbox insert;
 * keeping the SQL explicit makes what actually reaches the database obvious
 * rather than a consequence of lazy loading and flush timing.
 */
@Repository
public class TransactionRepository {

    private static final String INSERT_SQL =
            """
            INSERT INTO transactions
                (id, idempotency_key, account_id, amount_minor, currency, status, created_at)
            VALUES
                (:id, :idempotencyKey, :accountId, :amountMinor, :currency, :status, :createdAt)
            """;

    private static final String SELECT_BY_ID_SQL =
            """
            SELECT id, idempotency_key, account_id, amount_minor, currency, status, created_at
            FROM transactions
            WHERE id = :id
            """;

    private final JdbcClient jdbcClient;

    public TransactionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Inserts a transaction.
     *
     * <p>Throws {@link org.springframework.dao.DuplicateKeyException} if the
     * idempotency key is already present. That is not a failure mode to guard
     * against with a prior lookup — the unique constraint is the only check that
     * holds under concurrency, and catching the violation is how the intake path
     * detects a retry (issue #10).
     */
    public void insert(Transaction transaction) {
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
                .param("createdAt", OffsetDateTime.ofInstant(transaction.createdAt(), ZoneOffset.UTC))
                .update();
    }

    /** Returns the transaction with this id, or empty if there is none. */
    public Optional<Transaction> findById(UUID id) {
        return jdbcClient
                .sql(SELECT_BY_ID_SQL)
                .param("id", id)
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
