package swdchatbox.modules.document.initializer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.modules.document.entity.DocumentChunkSetting;
import swdchatbox.modules.document.repository.DocumentChunkSettingRepository;
import swdchatbox.modules.document.service.DocumentChunkSettingService;

@Slf4j
@Component
@Order(26)
@RequiredArgsConstructor
public class DefaultDocumentChunkSettingInitializer implements CommandLineRunner {

    private final DocumentChunkSettingRepository documentChunkSettingRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (documentChunkSettingRepository.count() > 0) {
            return;
        }

        DocumentChunkSetting setting = DocumentChunkSetting.builder()
                .chunkSize(DocumentChunkSettingService.DEFAULT_CHUNK_SIZE)
                .chunkOverlap(DocumentChunkSettingService.DEFAULT_CHUNK_OVERLAP)
                .build();

        documentChunkSettingRepository.save(setting);
        log.info("Seeded default document chunk setting: chunkSize={}, chunkOverlap={}",
                setting.getChunkSize(), setting.getChunkOverlap());
    }
}
