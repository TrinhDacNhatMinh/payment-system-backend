package com.minh.paymentsystem.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.minh.paymentsystem.common.dto.PageResponse;
import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.transaction.dto.AdminTransactionFilterRequest;
import com.minh.paymentsystem.transaction.dto.CreateTransactionCommand;
import com.minh.paymentsystem.transaction.dto.TransactionFilterRequest;
import com.minh.paymentsystem.transaction.dto.TransactionResponse;
import com.minh.paymentsystem.transaction.entity.Transaction;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;
import com.minh.paymentsystem.transaction.repository.TransactionRepository;
import com.minh.paymentsystem.wallet.entity.Wallet;
import com.minh.paymentsystem.wallet.service.WalletService;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletService walletService;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private Wallet testWallet;

    @BeforeEach
    void setUp() {
        testWallet = Wallet.builder()
                .id(10L)
                .build();
    }

    @Test
    void shouldRejectFractionalTransactionBeforePersistence() {
        BusinessException exception = assertThrows(BusinessException.class, () -> transactionService.createTransaction(
                new CreateTransactionCommand(testWallet, TransactionType.DEPOSIT, TransactionStatus.SUCCESS,
                        new BigDecimal("1000.50"), "fractional-reference", null)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AMOUNT_OUT_OF_RANGE);
        verifyNoInteractions(transactionRepository);
    }

    @Nested
    @DisplayName("createTransaction()")
    class CreateTransactionTests {

        @Test
        void createTransaction_validRequest_savesTransaction() {
            // given
            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            Transaction persistedTransaction = Transaction.builder()
                    .id(100L)
                    .build();
            when(transactionRepository.save(any(Transaction.class))).thenReturn(persistedTransaction);

            // when
            Transaction result = transactionService.createTransaction(new CreateTransactionCommand(
                    testWallet,
                    TransactionType.DEPOSIT,
                    TransactionStatus.SUCCESS,
                    new BigDecimal("100000"),
                    "REF123",
                    "Test description"));

            // then
            verify(transactionRepository, times(1)).save(captor.capture());
            assertThat(result).isSameAs(persistedTransaction);
            Transaction savedTx = captor.getValue();
            assertThat(savedTx.getWallet()).isEqualTo(testWallet);
            assertThat(savedTx.getType()).isEqualTo(TransactionType.DEPOSIT);
            assertThat(savedTx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
            assertThat(savedTx.getAmount()).isEqualByComparingTo("100000");
            assertThat(savedTx.getReferenceCode()).isEqualTo("REF123");
            assertThat(savedTx.getDescription()).isEqualTo("Test description");
        }
    }

    @Nested
    @DisplayName("getMyTransactions()")
    class GetMyTransactionsTests {

        @Test
        void getMyTransactions_walletNotFound_throwsBusinessException() {
            // given
            when(walletService.getWalletByUserId(1L)).thenThrow(new BusinessException(ErrorCode.WALLET_NOT_FOUND));
            TransactionFilterRequest filter = new TransactionFilterRequest(null, null, null, null, 0, 10);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> transactionService.getMyTransactions(1L, filter));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.WALLET_NOT_FOUND);
        }

        @Test
        void getMyTransactions_validFilter_returnsPageResponse() {
            // given
            when(walletService.getWalletByUserId(1L)).thenReturn(testWallet);

            Transaction tx = Transaction.builder()
                    .id(100L)
                    .wallet(testWallet)
                    .type(TransactionType.DEPOSIT)
                    .status(TransactionStatus.SUCCESS)
                    .amount(new BigDecimal("50000"))
                    .createdAt(LocalDateTime.now())
                    .build();

            Page<Transaction> page = new PageImpl<>(List.of(tx));

            when(transactionRepository.findWithFilter(
                    eq(10L), any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);

            TransactionFilterRequest filter = new TransactionFilterRequest(null, null, null, null, 0, 10);

            // when
            PageResponse<TransactionResponse> response = transactionService.getMyTransactions(1L, filter);

            // then
            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).id()).isEqualTo(100L);
            assertThat(response.totalElements()).isEqualTo(1);
            assertThat(response.page()).isZero();
            assertThat(response.size()).isEqualTo(1);
            assertThat(response.totalPages()).isEqualTo(1);
        }
    }

    @Test
    void shouldPreserveAdminPageContentAndMetadata() {
        AdminTransactionFilterRequest filter = new AdminTransactionFilterRequest(1L, null, TransactionType.DEPOSIT, TransactionStatus.SUCCESS, null, null);
        Pageable pageable = PageRequest.of(2, 2);
        Transaction transaction = Transaction.builder()
                .id(100L)
                .wallet(testWallet)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.SUCCESS)
                .amount(new BigDecimal("50000"))
                .description("Deposit")
                .build();
        when(transactionRepository.findWithAdminFilter(1L, null, TransactionType.DEPOSIT, TransactionStatus.SUCCESS, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(transaction), pageable, 5));

        PageResponse<TransactionResponse> response = transactionService.getAllTransactions(filter, pageable);

        assertThat(response.content()).containsExactly(TransactionResponse.from(transaction));
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }
}
