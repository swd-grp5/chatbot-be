package swdchatbox.system.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.system.auth.entity.PasswordResetToken;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByToken(String token);
}

