package io.github.prathmesh717.paynexa.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.prathmesh717.paynexa.domain.Account;
import io.github.prathmesh717.paynexa.domain.Transaction;
import io.github.prathmesh717.paynexa.domain.TransactionStatus;
import io.github.prathmesh717.paynexa.dto.TransferRequest;
import io.github.prathmesh717.paynexa.exception.AccountNotFoundException;
import io.github.prathmesh717.paynexa.exception.CurrencyMismatchException;
import io.github.prathmesh717.paynexa.exception.DuplicateRequestException;
import io.github.prathmesh717.paynexa.exception.SelfTransferException;
import io.github.prathmesh717.paynexa.repository.AccountRepository;
import io.github.prathmesh717.paynexa.repository.TransactionRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MoneyTransferProcessor {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public void execute(TransferRequest request, Transaction transaction) {

        UUID fromId = request.getFromId();
        UUID toId = request.getToId();
        BigDecimal amount = request.getAmount();

        if (fromId.equals(toId)) {
            throw new SelfTransferException("Cannot transfer to the same account");
        }

        UUID firstId = fromId.compareTo(toId) < 0 ? fromId : toId;
        UUID secondId = fromId.compareTo(toId) < 0 ? toId : fromId;

        Account first = accountRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + firstId));
        Account second = accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + secondId));

        Account from = fromId.equals(firstId) ? first : second;
        Account to = toId.equals(firstId) ? first : second;

        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new CurrencyMismatchException("Currency mismatch");
        }

        from.debit(amount);
        to.credit(amount);

        accountRepository.save(from);
        accountRepository.save(to);

        transaction.setStatus(TransactionStatus.SUCCESS);
        transactionRepository.save(transaction);
        log.info("Transaction {} committed successfully", transaction.getId());
    }

    public void executeWithLock(TransferRequest request, Transaction transaction) {
        if (transaction.getStatus() == TransactionStatus.SUCCESS) {
            return;
        }

        if (transaction.getStatus() == TransactionStatus.FAILED) {
            throw new DuplicateRequestException("Transfer already failed for idempotency key: " + request.getIdempotencyKey());
        }

        if (transaction.getStatus() == TransactionStatus.PROCESSING) {
            throw new DuplicateRequestException("Transfer is already in progress for idempotency key: " + request.getIdempotencyKey());
        }

        transaction.setStatus(TransactionStatus.PROCESSING);
        transactionRepository.save(transaction);

        execute(request, transaction);
    }

    public void markAsFailed(Transaction transaction, String reason) {
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setFailureReason(reason);
        transactionRepository.save(transaction);
    }
}
