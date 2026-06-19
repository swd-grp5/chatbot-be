package swdchatbox.system.document.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import swdchatbox.system.common.migration.SchemaMigrationSupport;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentIndexScheduler {

    private final DocumentIndexingService documentIndexingService;
    private final JdbcTemplate jdbcTemplate;

    private volatile boolean tableMissingLogged;

    @Scheduled(fixedDelay = 5000)
    public void processPendingJobs() {
        if (!SchemaMigrationSupport.tableExists(jdbcTemplate, "document_index_jobs")) {
            if (!tableMissingLogged) {
                log.warn("Skipping document index scheduler - table document_index_jobs does not exist");
                tableMissingLogged = true;
            }
            return;
        }
        tableMissingLogged = false;
        documentIndexingService.processPendingJobs();
    }
}
