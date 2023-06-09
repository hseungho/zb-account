package com.zerobase.hseungho.account.service;

import com.zerobase.hseungho.account.domain.Account;
import com.zerobase.hseungho.account.domain.AccountUser;
import com.zerobase.hseungho.account.domain.Transaction;
import com.zerobase.hseungho.account.dto.TransactionDto;
import com.zerobase.hseungho.account.exception.AccountException;
import com.zerobase.hseungho.account.repository.AccountRepository;
import com.zerobase.hseungho.account.repository.AccountUserRepository;
import com.zerobase.hseungho.account.repository.TransactionRepository;
import com.zerobase.hseungho.account.type.AccountStatus;
import com.zerobase.hseungho.account.type.ErrorCode;
import com.zerobase.hseungho.account.type.TransactionResultType;
import com.zerobase.hseungho.account.type.TransactionType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountUserRepository accountUserRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    @DisplayName("거래 사용 성공")
    void successUseBalance() {
        // given
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));
        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .transactionType(TransactionType.USE)
                        .transactionResultType(TransactionResultType.S)
                        .transactionId("transactionId")
                        .transactedAt(LocalDateTime.now())
                        .amount(1000L)
                        .balanceSnapshot(9000L)
                        .build());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        // when
        TransactionDto transactionDto = transactionService.useBalance(1L, "100000000", 1000L);
        // then
        verify(transactionRepository, times(1)).save(captor.capture());
        assertEquals(TransactionResultType.S, transactionDto.getTransactionResultType());
        assertEquals(1000L, captor.getValue().getAmount());
        assertEquals(9000L, captor.getValue().getBalanceSnapshot());
        assertEquals(TransactionType.USE, transactionDto.getTransactionType());
        assertEquals(9000L, transactionDto.getBalanceSnapshot());
        assertEquals(1000L, transactionDto.getAmount());
    }

    @Test
    @DisplayName("해당 유저 없음 - 거래 사용 실패")
    void useBalance_UserNotFound() {
        // given
        given((accountUserRepository.findById(anyLong())))
                .willReturn(Optional.empty());
        // when
        AccountException exception = assertThrows(
                AccountException.class,
                () -> transactionService.useBalance(1L, "1000000000", 1000L)
        );
        // then
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("해당 계좌 없음 - 거래 사용 실패")
    void useBalance_AccountNotFound() {
        // given
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        given((accountUserRepository.findById(anyLong())))
                .willReturn(Optional.of(user));
        given((accountRepository.findByAccountNumber(anyString())))
                .willReturn(Optional.empty());
        // when
        AccountException exception = assertThrows(
                AccountException.class,
                () -> transactionService.useBalance(1L, "1000000000", 1000L)
        );
        // then
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("사용자와 계좌 소유주 불일치 - 거래 사용 실패")
    void useBalance_UserAccountUnMatch() {
        // given
        AccountUser pobi = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        AccountUser harry = AccountUser.builder()
                .id(13L)
                .name("Harry")
                .build();
        given((accountUserRepository.findById(anyLong())))
                .willReturn(Optional.of(pobi));
        given((accountRepository.findByAccountNumber(anyString())))
                .willReturn(Optional.of(
                        Account.builder()
                                .accountUser(harry)
                                .balance(0L)
                                .accountNumber("1000000012").build()
                ));
        // when
        AccountException exception = assertThrows(
                AccountException.class,
                () -> transactionService.useBalance(1L, "1000000000", 1000L)
        );
        // then
        assertEquals(ErrorCode.USER_ACCOUNT_UN_MATCH, exception.getErrorCode());
    }

    @Test
    @DisplayName("이미 해지된 계좌 - 거래 사용 실패")
    void useBalance_AccountAlreadyUnregistered() {
        // given
        AccountUser pobi = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        given((accountUserRepository.findById(anyLong())))
                .willReturn(Optional.of(pobi));
        given((accountRepository.findByAccountNumber(anyString())))
                .willReturn(Optional.of(
                        Account.builder()
                                .accountUser(pobi)
                                .balance(100L)
                                .accountStatus(AccountStatus.UNREGISTERED)
                                .accountNumber("1000000012").build()
                ));
        // when
        AccountException exception = assertThrows(
                AccountException.class,
                () -> transactionService.useBalance(1L, "1000000000", 1000L)
        );
        // then
        assertEquals(ErrorCode.ACCOUNT_ALREADY_UNREGISTERED, exception.getErrorCode());
    }

    @Test
    @DisplayName("거래 금액이 잔액보다 큰 경우 - 거래 사용 실패")
    void useBalance_AmountExceedBalance() {
        // given
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(100L)
                .accountNumber("1000000012").build();
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        // when

        AccountException exception = assertThrows(
                AccountException.class,
                () -> transactionService.useBalance(1L, "1000000000", 1000L)
        );
        // then
        assertEquals(ErrorCode.AMOUNT_EXCEED_BALANCE, exception.getErrorCode());
        verify(transactionRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("실패 트랜잭션 저장 성공")
    void saveFailedUseTransaction() {
        // given
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));
        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .transactionType(TransactionType.USE)
                        .transactionResultType(TransactionResultType.S)
                        .transactionId("transactionId")
                        .transactedAt(LocalDateTime.now())
                        .amount(1000L)
                        .balanceSnapshot(9000L)
                        .build());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        // when
        transactionService.saveFailedUseTransaction("100000000", 1000L);
        // then
        verify(transactionRepository, times(1)).save(captor.capture());
        assertEquals(1000L, captor.getValue().getAmount());
        assertEquals(10000L, captor.getValue().getBalanceSnapshot());
        assertEquals(TransactionResultType.F, captor.getValue().getTransactionResultType());
    }

    @Test
    @DisplayName("거래 취소 성공")
    void successCancelBalance() {
        // given
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();
        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(TransactionType.USE)
                .transactionResultType(TransactionResultType.S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now())
                .amount(1000L)
                .balanceSnapshot(9000L)
                .build();
        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));
        given(transactionRepository.save(any()))
                .willReturn(
                        Transaction.builder()
                                .account(account)
                                .transactionType(TransactionType.CANCEL)
                                .transactionResultType(TransactionResultType.S)
                                .transactionId("transactionIdForCancel")
                                .transactedAt(LocalDateTime.now())
                                .amount(1000L)
                                .balanceSnapshot(10000L)
                                .build()
                );
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        // when
        TransactionDto transactionDto = transactionService.cancelBalance("transactionId", "100000000", 1000L);

        // then
        verify(transactionRepository, times(1)).save(captor.capture());
        assertEquals(1000L, captor.getValue().getAmount());
        assertEquals(10000L + 1000L, captor.getValue().getBalanceSnapshot());
        assertEquals(TransactionResultType.S, transactionDto.getTransactionResultType());
        assertEquals(TransactionType.CANCEL, transactionDto.getTransactionType());
        assertEquals(10000L, transactionDto.getBalanceSnapshot());
        assertEquals(1000L, transactionDto.getAmount());
    }

    @Test
    @DisplayName("해당 계좌 없음 - 거래 취소 실패")
    void cancelBalance_AccountNotFound() {
        // given
        Transaction transaction = Transaction.builder()
                .transactionType(TransactionType.USE)
                .transactionResultType(TransactionResultType.S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now())
                .amount(1000L)
                .balanceSnapshot(9000L)
                .build();
        given((transactionRepository.findByTransactionId(anyString())))
                .willReturn(Optional.of(transaction));
        given((accountRepository.findByAccountNumber(anyString())))
                .willReturn(Optional.empty());
        // when
        AccountException exception = assertThrows(
                AccountException.class,
                () -> transactionService.cancelBalance("transactionId", "1000000000", 1000L)
        );
        // then
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("원 거래 없음 - 거래 취소 실패")
    void cancelBalance_TransactionNotFound() {
        // given
        given((transactionRepository.findByTransactionId(anyString())))
                .willReturn(Optional.empty());

        AccountException exception = assertThrows(
                AccountException.class,
                () -> transactionService.cancelBalance("transactionId", "1000000000", 1000L)
        );
        // then
        assertEquals(ErrorCode.TRANSACTION_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("거래와 계좌 불일치 - 거래 취소 실패")
    void cancelBalance_TransactionAccountUnMatch() {
        // given
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .id(1L)
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();
        Account accountNotUse = Account.builder()
                .id(2L)
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();
        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(TransactionType.USE)
                .transactionResultType(TransactionResultType.S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now())
                .amount(1000L)
                .balanceSnapshot(9000L)
                .build();

        given((transactionRepository.findByTransactionId(anyString())))
                .willReturn(Optional.of(transaction));
        given((accountRepository.findByAccountNumber(anyString())))
                .willReturn(Optional.of(accountNotUse));
        // when
        AccountException exception = assertThrows(
                AccountException.class,
                () -> transactionService.cancelBalance(
                        "transactionId",
                        "1000000000",
                        1000L
                )
        );
        // then
        assertEquals(ErrorCode.TRANSACTION_ACCOUNT_UN_MATCH, exception.getErrorCode());
    }

    @Test
    @DisplayName("거래금액과 취소금액 불일치 - 거래 취소 실패")
    void cancelBalance_CancelMustFully() {
        // given
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .id(1L)
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();
        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(TransactionType.USE)
                .transactionResultType(TransactionResultType.S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now())
                .amount(1000L+1000L)
                .balanceSnapshot(9000L)
                .build();

        given((transactionRepository.findByTransactionId(anyString())))
                .willReturn(Optional.of(transaction));
        given((accountRepository.findByAccountNumber(anyString())))
                .willReturn(Optional.of(account));
        // when
        AccountException exception = assertThrows(
                AccountException.class,
                () -> transactionService.cancelBalance(
                        "transactionId",
                        "1000000000",
                        1000L
                )
        );
        // then
        assertEquals(ErrorCode.CANCEL_MUST_FULLY, exception.getErrorCode());
    }

    @Test
    @DisplayName("1년 전 거래 취소 요청 - 거래 취소 실패")
    void cancelBalance_TooOldOrder() {
        // given
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .id(1L)
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();
        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(TransactionType.USE)
                .transactionResultType(TransactionResultType.S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now().minusYears(1))
                .amount(1000L)
                .balanceSnapshot(9000L)
                .build();

        given((transactionRepository.findByTransactionId(anyString())))
                .willReturn(Optional.of(transaction));
        given((accountRepository.findByAccountNumber(anyString())))
                .willReturn(Optional.of(account));
        // when
        AccountException exception = assertThrows(
                AccountException.class,
                () -> transactionService.cancelBalance(
                        "transactionId",
                        "1000000000",
                        1000L
                )
        );
        // then
        assertEquals(ErrorCode.TOO_OLD_ORDER_TO_CANCEL, exception.getErrorCode());
    }

    @Test
    @DisplayName("거래 내역 조회 성공")
    void successQueryTransaction() {
        // given
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .id(1L)
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();
        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(TransactionType.USE)
                .transactionResultType(TransactionResultType.S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now().minusYears(1))
                .amount(1000L)
                .balanceSnapshot(9000L)
                .build();
        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));
        // when
        TransactionDto transactionDto = transactionService.queryTransactionById("trxId");
        // then
        Assertions.assertEquals(TransactionType.USE, transactionDto.getTransactionType());
        Assertions.assertEquals(TransactionResultType.S, transactionDto.getTransactionResultType());
        Assertions.assertEquals(1000L, transactionDto.getAmount());
        Assertions.assertEquals("transactionId", transactionDto.getTransactionId());

    }

    @Test
    @DisplayName("원 거래 없음 - 거래 조회 실패")
    void queryTransaction_TransactionNotFound() {
        // given
        given((transactionRepository.findByTransactionId(anyString())))
                .willReturn(Optional.empty());

        // when
        AccountException exception = assertThrows(
                AccountException.class,
                () -> transactionService.queryTransactionById("transactionId")
        );
        // then
        assertEquals(ErrorCode.TRANSACTION_NOT_FOUND, exception.getErrorCode());
    }
}