package com.simonvils.ledgerflow.notifier.projection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads and writes the transaction status projection. */
@Repository
public class TransactionStatusRepository {

    private static final String UPSERT_SQL =
            """
            INSERT INTO transaction_status
                (transaction_id, account_id, amount_minor, currency, status, accepted_at, updated_at)
            VALUES
                (:transactionId, :accountId, :amountMinor, :currency, :status, :acceptedAt, :updatedAt)
            ON CONFLICT (transaction_id) DO UPDATE SET
                status = EXCLUDED.status,
                updated_at = EXCLUDED.updated_at
            """;

    private static final String SELECT_SQL =
            """
            SELECT transaction_id, account_id, amount_minor, currency, status, accepted_at, updated_at
            FROM transaction_status
            WHERE transaction_id = :transactionId
            """;

    private final JdbcClient jdbcClient;

    public TransactionStatusRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Writes the status for a transaction, replacing any previous value.
     *
     * <p>An upsert rather than an insert, so applying the same event twice
     * produces the same row instead of a constraint violation. Deduplication
     * already prevents that, but a projection whose correctness depends on
     * something upstream staying correct is a projection that breaks silently the
     * first time that assumption stops holding.
     *
     * <p>The conflict branch updates only the status and the timestamp. Account,
     * amount and currency describe the transaction as it was accepted, and a later
     * event has no business rewriting them — if one ever disagreed, overwriting
     * would hide the disagreement rather than surface it.
     */
    public void save(TransactionStatusView view) {
        jdbcClient
                .sql(UPSERT_SQL)
                .param("transactionId", view.transactionId())
                .param("accountId", view.accountId())
                .param("amountMinor", view.amountMinor())
                .param("currency", view.currency())
                .param("status", view.status())
                .param("acceptedAt", toOffsetDateTime(view.acceptedAt()))
                .param("updatedAt", toOffsetDateTime(view.updatedAt()))
                .update();
    }

    /** Returns the status of a transaction, or empty if this service has not seen it. */
    public Optional<TransactionStatusView> findByTransactionId(UUID transactionId) {
        return jdbcClient
                .sql(SELECT_SQL)
                .param("transactionId", transactionId)
                .query(TransactionStatusRepository::mapRow)
                .optional();
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        // pgjdbc binds OffsetDateTime to TIMESTAMPTZ directly; Instant is not a
        // supported parameter type.
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static TransactionStatusView mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TransactionStatusView(
                rs.getObject("transaction_id", UUID.class),
                rs.getString("account_id"),
                rs.getLong("amount_minor"),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getObject("accepted_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
