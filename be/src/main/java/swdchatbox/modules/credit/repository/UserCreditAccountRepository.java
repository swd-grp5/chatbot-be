package swdchatbox.modules.credit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import swdchatbox.modules.credit.entity.UserCreditAccount;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserCreditAccountRepository extends JpaRepository<UserCreditAccount, UUID> {
    Optional<UserCreditAccount> findByUser_Id(UUID userId);
    Optional<UserCreditAccount> findByUser_Email(String email);

    @Query("SELECT uca FROM UserCreditAccount uca WHERE uca.nextResetAt <= :now")
    List<UserCreditAccount> findAllExpiredAccounts(@Param("now") LocalDateTime now);
}
