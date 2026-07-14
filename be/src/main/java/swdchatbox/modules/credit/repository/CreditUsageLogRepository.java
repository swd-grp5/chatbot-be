package swdchatbox.modules.credit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import swdchatbox.modules.credit.entity.CreditUsageLog;

import java.util.UUID;

@Repository
public interface CreditUsageLogRepository extends JpaRepository<CreditUsageLog, UUID> {
}
