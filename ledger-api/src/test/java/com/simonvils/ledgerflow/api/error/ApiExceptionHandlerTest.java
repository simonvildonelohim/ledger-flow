package com.simonvils.ledgerflow.api.error;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.simonvils.ledgerflow.api.transaction.CreateTransactionRequest;
import com.simonvils.ledgerflow.api.transaction.TransactionController;
import com.simonvils.ledgerflow.api.transaction.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Asserts the shape of error responses, not merely their status codes. */
@WebMvcTest(TransactionController.class)
class ApiExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TransactionService service;

    @Test
    void aRejectedFieldIsReportedAsProblemJsonAndNamed() throws Exception {
        mockMvc
                .perform(
                        post("/transactions")
                                .header(TransactionController.IDEMPOTENCY_KEY_HEADER, "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"accountId":"acct-001","amountMinor":12500,"currency":"ABC"}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.type").exists())
                .andExpect(jsonPath("$.errors.currency").exists());
    }

    @Test
    void everyRejectedFieldIsListed() throws Exception {
        mockMvc
                .perform(
                        post("/transactions")
                                .header(TransactionController.IDEMPOTENCY_KEY_HEADER, "key-2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"accountId":"","amountMinor":0,"currency":"ABC"}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.accountId").exists())
                .andExpect(jsonPath("$.errors.amountMinor").exists())
                .andExpect(jsonPath("$.errors.currency").exists());
    }

    @Test
    void aMissingHeaderIsAlsoProblemJson() throws Exception {
        mockMvc
                .perform(
                        post("/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"accountId":"acct-001","amountMinor":12500,"currency":"CAD"}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void aDataIntegrityViolationBecomesA409ThatLeaksNothing() throws Exception {
        given(service.accept(any(), any(CreateTransactionRequest.class)))
                .willThrow(
                        new DataIntegrityViolationException(
                                "ERROR: duplicate key value violates unique constraint "
                                        + "\"uq_transactions_idempotency_key\""));

        mockMvc
                .perform(
                        post("/transactions")
                                .header(TransactionController.IDEMPOTENCY_KEY_HEADER, "key-3")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"accountId":"acct-001","amountMinor":12500,"currency":"CAD"}
                                        """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflict"))
                // The constraint name describes the schema and must not reach a client.
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("uq_transactions_idempotency_key"))))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void aMalformedBodyIsNotReportedAsAServerError() throws Exception {
        mockMvc
                .perform(
                        post("/transactions")
                                .header(TransactionController.IDEMPOTENCY_KEY_HEADER, "key-4")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ this is not json"))
                .andExpect(status().isBadRequest());
    }
}
