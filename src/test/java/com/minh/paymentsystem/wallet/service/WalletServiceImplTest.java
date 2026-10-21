package com.minh.paymentsystem.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.wallet.dto.WalletResponse;
import com.minh.paymentsystem.wallet.entity.Wallet;
import com.minh.paymentsystem.wallet.repository.WalletRepository;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private WalletServiceImpl walletService;

    private User testUser;
    private Wallet testWallet;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .build();

        testWallet = Wallet.builder()
                .id(100L)
                .user(testUser)
                .balance(new BigDecimal("50000"))
                .currency("VND")
                .build();
    }

    @Test
    void shouldRejectFractionalDebitAndCreditBeforeAccessingWallet() {
        BigDecimal fraction = new BigDecimal("1000.001");

        assertThrows(BusinessException.class, () -> walletService.debit(100L, fraction));
        assertThrows(BusinessException.class, () -> walletService.credit(100L, fraction));

        verifyNoInteractions(walletRepository);
    }

    @Test
    void shouldBlockLegacyFractionalBalanceWithoutChangingIt() {
        testWallet.setBalance(new BigDecimal("50000.50"));
        when(walletRepository.findById(100L)).thenReturn(Optional.of(testWallet));

        BusinessException debit = assertThrows(BusinessException.class, () -> walletService.debit(100L, new BigDecimal("1000")));
        BusinessException credit = assertThrows(BusinessException.class, () -> walletService.credit(100L, new BigDecimal("1000")));

        assertThat(debit.getErrorCode()).isEqualTo(ErrorCode.LEGACY_FRACTIONAL_MONEY);
        assertThat(credit.getErrorCode()).isEqualTo(ErrorCode.LEGACY_FRACTIONAL_MONEY);
        assertThat(testWallet.getBalance()).isEqualByComparingTo("50000.50");
        verify(walletRepository, never()).save(any());
    }

    @Test
    void shouldAcceptWholeDatabaseScaleAndPreventBalanceOverflow() {
        testWallet.setBalance(new BigDecimal("9999999999999999.00"));
        when(walletRepository.findById(100L)).thenReturn(Optional.of(testWallet));

        BusinessException failure = assertThrows(BusinessException.class, () -> walletService.credit(100L, new BigDecimal("1.00")));

        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.AMOUNT_OUT_OF_RANGE);
        assertThat(testWallet.getBalance()).isEqualByComparingTo("9999999999999999");
        verify(walletRepository, never()).save(any());
    }

    @Nested
    @DisplayName("createWalletForUser()")
    class CreateWalletForUserTests {

        @Test
        void createWalletForUser_validUser_savesWalletWithZeroBalance() {
            // given
            ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
            when(walletRepository.save(any(Wallet.class))).thenReturn(new Wallet());

            // when
            walletService.createWalletForUser(testUser);

            // then
            verify(walletRepository, times(1)).save(walletCaptor.capture());
            Wallet savedWallet = walletCaptor.getValue();
            assertThat(savedWallet.getUser()).isEqualTo(testUser);
            assertThat(savedWallet.getBalance()).isEqualByComparingTo("0");
            assertThat(savedWallet.getCurrency()).isEqualTo("VND");
        }
    }

    @Nested
    @DisplayName("getMyWallet()")
    class GetMyWalletTests {

        @Test
        void getMyWallet_walletNotFound_throwsBusinessException() {
            // given
            when(walletRepository.findByUserId(1L)).thenReturn(Optional.empty());

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> walletService.getMyWallet(1L));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.WALLET_NOT_FOUND);
        }

        @Test
        void getMyWallet_walletFound_returnsWalletResponse() {
            // given
            when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(testWallet));

            // when
            WalletResponse response = walletService.getMyWallet(1L);

            // then
            assertThat(response.id()).isEqualTo(testWallet.getId());
            assertThat(response.balance()).isEqualByComparingTo("50000");
            assertThat(response.currency()).isEqualTo("VND");
        }
    }

    @Nested
    @DisplayName("debit()")
    class DebitTests {

        @Test
        void debit_walletNotFound_throwsBusinessException() {
            // given
            when(walletRepository.findById(100L)).thenReturn(Optional.empty());

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> walletService.debit(100L, BigDecimal.TEN));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.WALLET_NOT_FOUND);
        }

        @Test
        void debit_insufficientBalance_throwsBusinessException() {
            // given
            when(walletRepository.findById(100L)).thenReturn(Optional.of(testWallet));
            BigDecimal amountToDebit = new BigDecimal("60000"); // Greater than 50000

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> walletService.debit(100L, amountToDebit));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.WALLET_INSUFFICIENT_BALANCE);
            verify(walletRepository, never()).save(any());
        }

        @Test
        void debit_validAmount_updatesBalance() {
            // given
            when(walletRepository.findById(100L)).thenReturn(Optional.of(testWallet));
            BigDecimal amountToDebit = new BigDecimal("30000");
            ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);

            // when
            walletService.debit(100L, amountToDebit);

            // then
            verify(walletRepository, times(1)).save(walletCaptor.capture());
            assertThat(walletCaptor.getValue().getBalance()).isEqualByComparingTo("20000");
        }
    }

    @Nested
    @DisplayName("credit()")
    class CreditTests {

        @Test
        void credit_walletNotFound_throwsBusinessException() {
            // given
            when(walletRepository.findById(100L)).thenReturn(Optional.empty());

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> walletService.credit(100L, BigDecimal.TEN));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.WALLET_NOT_FOUND);
        }

        @Test
        void credit_validAmount_updatesBalance() {
            // given
            when(walletRepository.findById(100L)).thenReturn(Optional.of(testWallet));
            BigDecimal amountToCredit = new BigDecimal("20000");
            ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);

            // when
            walletService.credit(100L, amountToCredit);

            // then
            verify(walletRepository, times(1)).save(walletCaptor.capture());
            assertThat(walletCaptor.getValue().getBalance()).isEqualByComparingTo("70000");
        }
    }
}
