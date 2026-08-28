package com.simonvils.ledgerflow.api.transaction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test for {@link TransactionController}. The service is mocked, so this
 * covers request binding, validation and the response shape without a database.
 */
@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    private static final String VALID_BODY =
            """
            {"accountId":"acct-001","amountMinor":12500,"currency":"CAD"}
            """;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TransactionService service;

    @Test
    void returns201WhenTheTransactionIsCreated() throws Exception {
        Transaction accepted = Transaction.accept("key-1", "acct-001", 125_00L, "CAD");
        given(service.accept(eq("key-1"), any(CreateTransactionRequest.class)))
                .willReturn(TransactionAcceptance.created(accepted));

        mockMvc
                .perform(
                        post("/transactions")
                                .header(TransactionController.IDEMPOTENCY_KEY_HEADER, "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(accepted.id().toString()))
                .andExpect(jsonPath("$.accountId").value("acct-001"))
                .andExpect(jsonPath("$.amountMinor").value(12500))
                .andExpect(jsonPath("$.currency").value("CAD"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void returns200AndTheOriginalTransactionOnAReplay() throws Exception {
        Transaction original = Transaction.accept("key-1", "acct-001", 125_00L, "CAD");
        given(service.accept(eq("key-1"), any(CreateTransactionRequest.class)))
                .willReturn(TransactionAcceptance.replayed(original));

        mockMvc
                .perform(
                        post("/transactions")
                                .header(TransactionController.IDEMPOTENCY_KEY_HEADER, "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(original.id().toString()));
    }

    @Test
    void rejectsARequestWithNoIdempotencyKeyHeader() throws Exception {
        mockMvc
                .perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest());

        verify(service, never()).accept(any(), any());
    }

    @Test
    void rejectsABlankIdempotencyKeyHeader() throws Exception {
        mockMvc
                .perform(
                        post("/transactions")
                                .header(TransactionController.IDEMPOTENCY_KEY_HEADER, "   ")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY))
                .andExpect(status().isBadRequest());

        verify(service, never()).accept(any(), any());
    }

    @Test
    void acceptsANegativeAmountAsADebit() throws Exception {
        Transaction accepted = Transaction.accept("key-2", "acct-002", -4_250L, "EUR");
        given(service.accept(eq("key-2"), any(CreateTransactionRequest.class)))
                .willReturn(TransactionAcceptance.created(accepted));

        mockMvc
                .perform(
                        post("/transactions")
                                .header(TransactionController.IDEMPOTENCY_KEY_HEADER, "key-2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"accountId":"acct-002","amountMinor":-4250,"currency":"EUR"}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountMinor").value(-4250));
    }

    @Test
    void rejectsABlankAccountId() throws Exception {
        mockMvc
                .perform(
                        post("/transactions")
                                .header(TransactionController.IDEMPOTENCY_KEY_HEADER, "key-3")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"accountId":"  ","amountMinor":12500,"currency":"CAD"}
                                        """))
                .andExpect(status().isBadRequest());

        verify(service, never()).accept(any(), any());
    }

    @Test
    void rejectsAZeroAmount() throws Exception {
        mockMvc
                .perform(
                        post("/transactions")
                                .header(TransactionController.IDEMPOTENCY_KEY_HEADER, "key-4")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"accountId":"acct-001","amountMinor":0,"currency":"CAD"}
                                        """))
                .andExpect(status().isBadRequest());

        verify(service, never()).accept(any(), any());
    }

    @Test
    void rejectsAnUnknownCurrencyCode() throws Exception {
        // Three uppercase letters, so a pattern check would let this through.
        mockMvc
                .perform(
                        post("/transactions")
                                .header(TransactionController.IDEMPOTENCY_KEY_HEADER, "key-5")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"accountId":"acct-001","amountMinor":12500,"currency":"ABC"}
                                        """))
                .andExpect(status().isBadRequest());

        verify(service, never()).accept(any(), any());
    }

    @Test
    void rejectsALowercaseCurrencyCode() throws Exception {
        mockMvc
                .perform(
                        post("/transactions")
                                .header(TransactionController.IDEMPOTENCY_KEY_HEADER, "key-6")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"accountId":"acct-001","amountMinor":12500,"currency":"cad"}
                                        """))
                .andExpect(status().isBadRequest());

        verify(service, never()).accept(any(), any());
    }
}
