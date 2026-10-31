package io.github.prathmesh717.paynexa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.prathmesh717.paynexa.domain.Transaction;
import io.github.prathmesh717.paynexa.service.TransferService;
import io.github.prathmesh717.paynexa.dto.TransferRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfer")
@RequiredArgsConstructor
@Slf4j
public class TransferController {
    private static final String IDEMPOTENCY_HIT_HEADER = "X-Idempotency-Hit";
    private final TransferService transferService;

    @PostMapping
    @Operation(summary = "Transfer money between accounts",
            description = "Safe transfer with idempotency check and concurrency protection")
    @ApiResponse(responseCode = "200", description = "Successful transfer")
    @ApiResponse(responseCode = "400", description = "Business logic error (insufficient funds, etc.)")
    @ApiResponse(responseCode = "409", description = "Idempotency conflict or concurrent update")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public ResponseEntity<String> transfer(@Valid @RequestBody TransferRequest request) {
        log.info("Transfer request to transfer {} from account {} to account {}", request.getAmount(), request.getFromId(), request.getToId());
        boolean idempotencyHit = transferService.transfer(request);
        log.info("Transfer successful for request from {}", request.getFromId());
        return ResponseEntity.ok()
                .header(IDEMPOTENCY_HIT_HEADER, Boolean.toString(idempotencyHit))
                .body("Transfer successful");
    }

    @GetMapping("/accounts/{accountId}/transactions")
    @Operation(summary = "Get transaction history for an account",
            description = "Returns a paginated list of all incoming and outgoing transactions")
    public ResponseEntity<Page<Transaction>> getHistory(
            @PathVariable UUID accountId,
            @Parameter(description = "Pagination parameters (page, size, sort)") Pageable pageable) {

        log.info("REST request to get transaction history for account: {}", accountId);

        Page<Transaction> history = transferService.getTransactions(accountId, pageable);

        return ResponseEntity.ok(history);
    }
}
