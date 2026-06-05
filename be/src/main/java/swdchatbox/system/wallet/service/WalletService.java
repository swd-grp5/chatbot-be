package swdchatbox.system.wallet.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.system.common.exception.BadRequestException;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.repository.UserRepository;
import swdchatbox.system.wallet.dto.response.WalletResponse;
import swdchatbox.system.wallet.dto.response.WalletTransactionResponse;
import swdchatbox.system.wallet.entity.Wallet;
import swdchatbox.system.wallet.entity.WalletTransaction;
import swdchatbox.system.wallet.enums.WalletTransactionStatus;
import swdchatbox.system.wallet.enums.WalletTransactionType;
import swdchatbox.system.wallet.repository.WalletRepository;
import swdchatbox.system.wallet.repository.WalletTransactionRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public WalletResponse getMyWallet(String email) {
        Wallet wallet = getOrCreateWallet(email);
        return toResponse(wallet);
    }

    @Transactional(readOnly = true)
    public List<WalletTransaction> getMyTransactions(String email) {
        User user = findUser(email);
        return walletTransactionRepository.findAllByWallet_User_IdOrderByCreatedAtDesc(user.getId());
    }

    @Transactional(readOnly = true)
    public List<WalletTransactionResponse> getMyTransactionHistory(String email) {
        User user = findUser(email);
        return walletTransactionRepository.findAllByWallet_User_IdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toTransactionResponse).toList();
    }

    @Transactional
    public WalletTransaction creditWallet(String email, BigDecimal amount, String referenceId, String description) {
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Amount must be greater than 0");
        }

        Wallet wallet = getOrCreateWallet(email);
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        return walletTransactionRepository.save(WalletTransaction.builder()
                .wallet(wallet)
                .transactionType(WalletTransactionType.TOP_UP)
                .status(WalletTransactionStatus.SUCCESS)
                .amount(amount)
                .referenceId(referenceId)
                .description(description)
                .build());
    }

    @Transactional
    public WalletTransaction debitWallet(String email, BigDecimal amount, String referenceId, String description) {
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Amount must be greater than 0");
        }

        Wallet wallet = getOrCreateWallet(email);
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Insufficient wallet balance");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        return walletTransactionRepository.save(WalletTransaction.builder()
                .wallet(wallet)
                .transactionType(WalletTransactionType.SUBSCRIPTION_PAYMENT)
                .status(WalletTransactionStatus.SUCCESS)
                .amount(amount)
                .referenceId(referenceId)
                .description(description)
                .build());
    }

    @Transactional
    public Wallet getOrCreateWallet(String email) {
        User user = findUser(email);
        return walletRepository.findByUser_Id(user.getId())
                .orElseGet(() -> walletRepository.save(Wallet.builder().user(user).build()));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private WalletResponse toResponse(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .balance(wallet.getBalance())
                .reservedBalance(wallet.getReservedBalance())
                .active(wallet.getActive())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    private WalletTransactionResponse toTransactionResponse(WalletTransaction tx) {
        return WalletTransactionResponse.builder()
                .id(tx.getId())
                .walletId(tx.getWallet().getId())
                .transactionType(tx.getTransactionType())
                .status(tx.getStatus())
                .amount(tx.getAmount())
                .referenceId(tx.getReferenceId())
                .description(tx.getDescription())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
