package swdchatbox.modules.wallet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.shared.dto.PageResponse;
import swdchatbox.shared.exception.BadRequestException;
import swdchatbox.shared.exception.ResourceNotFoundException;
import swdchatbox.modules.user.entity.User;
import swdchatbox.modules.user.repository.UserRepository;
import swdchatbox.modules.wallet.dto.request.WalletTransactionFilterRequest;
import swdchatbox.modules.wallet.dto.response.WalletResponse;
import swdchatbox.modules.wallet.dto.response.WalletTransactionResponse;
import swdchatbox.modules.wallet.entity.Wallet;
import swdchatbox.modules.wallet.entity.WalletTransaction;
import swdchatbox.modules.wallet.enums.WalletTransactionStatus;
import swdchatbox.modules.wallet.enums.WalletTransactionType;
import swdchatbox.modules.wallet.repository.WalletRepository;
import swdchatbox.modules.wallet.repository.WalletTransactionRepository;
import swdchatbox.modules.wallet.repository.WalletTransactionSpecifications;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
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
    public PageResponse<WalletTransactionResponse> getMyTransactionHistory(
            String email,
            WalletTransactionFilterRequest filter,
            Pageable pageable
    ) {
        User user = findUser(email);
        WalletTransactionFilterRequest effectiveFilter = filter != null ? filter : new WalletTransactionFilterRequest();
        effectiveFilter.setUserId(user.getId());

        Specification<WalletTransaction> spec = Specification
                .where(WalletTransactionSpecifications.belongsToUser(effectiveFilter.getUserId()))
                .and(WalletTransactionSpecifications.hasId(effectiveFilter.getId()))
                .and(WalletTransactionSpecifications.hasWalletId(effectiveFilter.getWalletId()))
                .and(WalletTransactionSpecifications.hasTransactionType(effectiveFilter.getTransactionType()))
                .and(WalletTransactionSpecifications.hasStatus(effectiveFilter.getStatus()))
                .and(WalletTransactionSpecifications.hasReferenceId(effectiveFilter.getReferenceId()))
                .and(WalletTransactionSpecifications.keywordLike(effectiveFilter.getKeyword()))
                .and(WalletTransactionSpecifications.amountGreaterOrEqual(effectiveFilter.getAmountMin()))
                .and(WalletTransactionSpecifications.amountLessOrEqual(effectiveFilter.getAmountMax()))
                .and(WalletTransactionSpecifications.createdAfter(effectiveFilter.getCreatedFrom()))
                .and(WalletTransactionSpecifications.createdBefore(effectiveFilter.getCreatedTo()));

        Page<WalletTransactionResponse> page = walletTransactionRepository.findAll(spec, pageable)
                .map(this::toTransactionResponse);

        log.info("[wallet/transactions] email={} id={} walletId={} type={} status={} referenceId={} keyword={} "
                        + "amountMin={} amountMax={} createdFrom={} createdTo={} page={} size={} totalElements={}",
                email,
                effectiveFilter.getId(),
                effectiveFilter.getWalletId(),
                effectiveFilter.getTransactionType(),
                effectiveFilter.getStatus(),
                effectiveFilter.getReferenceId(),
                effectiveFilter.getKeyword(),
                effectiveFilter.getAmountMin(),
                effectiveFilter.getAmountMax(),
                effectiveFilter.getCreatedFrom(),
                effectiveFilter.getCreatedTo(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());

        return PageResponse.<WalletTransactionResponse>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
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
