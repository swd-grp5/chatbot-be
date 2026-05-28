package swdchatbox.system.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.system.auth.entity.EmailVerificationToken;

import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByToken(String token);
}

