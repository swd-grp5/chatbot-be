package swdchatbox.system.document.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentIndexScheduler {

    private final DocumentIndexingService documentIndexingService;

    @Scheduled(fixedDelay = 5000)
    public void processPendingJobs() {
        documentIndexingService.processPendingJobs();
    }
}
