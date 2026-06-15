package swdchatbox.system.wallet.repository;

import org.springframework.data.jpa.domain.Specification;
import swdchatbox.system.wallet.entity.WalletTransaction;
import swdchatbox.system.wallet.enums.WalletTransactionStatus;
import swdchatbox.system.wallet.enums.WalletTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public final class WalletTransactionSpecifications {

    private WalletTransactionSpecifications() {
    }

    public static Specification<WalletTransaction> belongsToUser(UUID userId) {
        return (root, query, cb) -> userId == null
                ? cb.conjunction()
                : cb.equal(root.get("wallet").get("user").get("id"), userId);
    }

    public static Specification<WalletTransaction> hasId(UUID id) {
        return (root, query, cb) -> id == null
                ? cb.conjunction()
                : cb.equal(root.get("id"), id);
    }

    public static Specification<WalletTransaction> hasWalletId(UUID walletId) {
        return (root, query, cb) -> walletId == null
                ? cb.conjunction()
                : cb.equal(root.get("wallet").get("id"), walletId);
    }

    public static Specification<WalletTransaction> hasTransactionType(WalletTransactionType transactionType) {
        return (root, query, cb) -> transactionType == null
                ? cb.conjunction()
                : cb.equal(root.get("transactionType"), transactionType);
    }

    public static Specification<WalletTransaction> hasStatus(WalletTransactionStatus status) {
        return (root, query, cb) -> status == null
                ? cb.conjunction()
                : cb.equal(root.get("status"), status);
    }

    public static Specification<WalletTransaction> hasReferenceId(String referenceId) {
        return (root, query, cb) -> referenceId == null || referenceId.isBlank()
                ? cb.conjunction()
                : cb.equal(root.get("referenceId"), referenceId.trim());
    }

    public static Specification<WalletTransaction> keywordLike(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("referenceId")), like),
                    cb.like(cb.lower(root.get("description")), like)
            );
        };
    }

    public static Specification<WalletTransaction> amountGreaterOrEqual(BigDecimal amountMin) {
        return (root, query, cb) -> amountMin == null
                ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.get("amount"), amountMin);
    }

    public static Specification<WalletTransaction> amountLessOrEqual(BigDecimal amountMax) {
        return (root, query, cb) -> amountMax == null
                ? cb.conjunction()
                : cb.lessThanOrEqualTo(root.get("amount"), amountMax);
    }

    public static Specification<WalletTransaction> createdAfter(LocalDateTime createdFrom) {
        return (root, query, cb) -> createdFrom == null
                ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom);
    }

    public static Specification<WalletTransaction> createdBefore(LocalDateTime createdTo) {
        return (root, query, cb) -> createdTo == null
                ? cb.conjunction()
                : cb.lessThanOrEqualTo(root.get("createdAt"), createdTo);
    }
}
