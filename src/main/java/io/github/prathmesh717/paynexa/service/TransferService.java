package io.github.prathmesh717.paynexa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.prathmesh717.paynexa.domain.Account;
import io.github.prathmesh717.paynexa.domain.OutboxEvent;
import io.github.prathmesh717.paynexa.domain.Transaction;
import io.github.prathmesh717.paynexa.domain.TransactionStatus;
import io.github.prathmesh717.paynexa.dto.PaymentProviderRequest;
import io.github.prathmesh717.paynexa.dto.TransferRequest;
import io.github.prathmesh717.paynexa.exception.DuplicateRequestException;
import io.github.prathmesh717.paynexa.exception.PayNexaException;
import io.github.prathmesh717.paynexa.repository.AccountRepository;
import io.github.prathmesh717.paynexa.repository.OutboxEventRepository;
import io.github.prathmesh717.paynexa.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final StringRedisTemplate redisTemplate;
    private final MoneyTransferProcessor processor;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;


    private static final String IDEMPOTENCY_PREFIX = "transfer_idempotency:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    @Retryable(
            retryFor = {org.springframework.orm.ObjectOptimisticLockingFailureException.class},
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional(noRollbackFor = PayNexaException.class)
    public boolean transfer(TransferRequest request) {
        String key = IDEMPOTENCY_PREFIX + request.getIdempotencyKey();

        // Redis is only a fast-path cache for completed idempotent requests.
        String cachedState = redisTemplate.opsForValue().get(key);
        if ("COMPLETED".equals(cachedState)) {
            log.info("Idempotent replay from cache for SUCCESS key: {}", request.getIdempotencyKey());
            return true;
        }

        transactionRepository.insertPendingIfAbsent(
                UUID.randomUUID(),
                request.getFromId(),
                request.getToId(),
                request.getAmount(),
                request.getAmount(),
                BigDecimal.ONE,
                TransactionStatus.PENDING.name(),
                request.getIdempotencyKey()
        );

        Transaction tx = transactionRepository
                .findByIdempotencyKeyForUpdate(request.getIdempotencyKey())
                .orElseThrow(() -> new IllegalStateException(
                        "Transaction row missing for idempotency key: " + request.getIdempotencyKey()));

        if (tx.getStatus() == TransactionStatus.SUCCESS) {
            cacheCompletedAfterCommit(key);
            log.info("Idempotent replay for SUCCESSFUL key: {}", request.getIdempotencyKey());
            return true;
        }

        if (tx.getStatus() == TransactionStatus.FAILED) {
            throw new DuplicateRequestException("Transfer already failed for idempotency key: " + request.getIdempotencyKey());
        }

        try {
            processor.executeWithLock(request, tx);
            createOutboxEvent(tx, request);
            cacheCompletedAfterCommit(key);
            return false;
        } catch (Exception e) {
            if (e instanceof PayNexaException
                    && tx.getStatus() != TransactionStatus.SUCCESS
                    && tx.getId() != null) {
                processor.markAsFailed(tx, e.getMessage());
            } else if (!(e instanceof ObjectOptimisticLockingFailureException)
                    && tx.getStatus() != TransactionStatus.SUCCESS
                    && tx.getId() != null) {
                log.error("Transfer failed with non-business exception for key {}. Transaction will roll back.",
                        request.getIdempotencyKey(), e);
            }
            throw e;
        }
    }

    private void cacheCompletedAfterCommit(String key) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    redisTemplate.opsForValue().set(key, "COMPLETED", IDEMPOTENCY_TTL);
                }
            });
            return;
        }

        redisTemplate.opsForValue().set(key, "COMPLETED", IDEMPOTENCY_TTL);
    }

    public Page<Transaction> getTransactions(UUID accountId, Pageable pageable) {
        return transactionRepository.findAllByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(accountId, accountId, pageable);
    }

    private void createOutboxEvent(Transaction tx, TransferRequest request) {
        try {
            Account toAccount = accountRepository.findById(request.getToId())
                    .orElseThrow(() -> new IllegalStateException("Account not found: " + request.getToId()));
            PaymentProviderRequest gatewayReq = new PaymentProviderRequest(
                    tx.getId().toString(),
                    request.getAmount(),
                    toAccount.getCurrency(),
                    request.getToId().toString(),
                    "Transfer from " + request.getFromId()
            );

            String payload = objectMapper.writeValueAsString(gatewayReq);

            OutboxEvent event = new OutboxEvent();
            event.setAggregateId(tx.getId().toString());
            event.setType("EXTERNAL_PAYMENT_SEND");
            event.setPayload(payload);

            outboxEventRepository.save(event);
            log.info("Outbox event created for transaction: {}", tx.getId());

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
    }
}
