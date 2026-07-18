package swdchatbox.modules.document.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import swdchatbox.modules.document.dto.EffectiveChunkConfig;
import swdchatbox.modules.document.dto.request.UpdateDocumentChunkSettingRequest;
import swdchatbox.modules.document.dto.response.DocumentChunkSettingResponse;
import swdchatbox.modules.document.entity.DocumentChunkSetting;
import swdchatbox.modules.document.repository.DocumentChunkSettingRepository;
import swdchatbox.shared.exception.BadRequestException;

@Service
@RequiredArgsConstructor
public class DocumentChunkSettingService {

    public static final int DEFAULT_CHUNK_SIZE = 1200;
    public static final int DEFAULT_CHUNK_OVERLAP = 200;

    private final DocumentChunkSettingRepository documentChunkSettingRepository;

    public DocumentChunkSettingResponse getCurrent() {
        return documentChunkSettingRepository.findFirstByOrderByUpdatedAtDesc()
                .map(this::toResponse)
                .orElseGet(this::defaultResponse);
    }

    public EffectiveChunkConfig resolveEffectiveConfig() {
        return documentChunkSettingRepository.findFirstByOrderByUpdatedAtDesc()
                .map(setting -> EffectiveChunkConfig.builder()
                        .chunkSize(setting.getChunkSize())
                        .chunkOverlap(normalizeOverlap(setting.getChunkOverlap(), setting.getChunkSize()))
                        .fromDatabase(true)
                        .build())
                .orElseGet(this::defaultConfig);
    }

    @Transactional
    public DocumentChunkSettingResponse update(UpdateDocumentChunkSettingRequest request) {
        validate(request.getChunkSize(), request.getChunkOverlap());

        DocumentChunkSetting setting = documentChunkSettingRepository.findFirstByOrderByUpdatedAtDesc()
                .orElseGet(() -> DocumentChunkSetting.builder().build());

        setting.setChunkSize(request.getChunkSize());
        setting.setChunkOverlap(request.getChunkOverlap());

        return toResponse(documentChunkSettingRepository.save(setting));
    }

    private void validate(int chunkSize, int chunkOverlap) {
        if (chunkOverlap >= chunkSize) {
            throw new BadRequestException("chunkOverlap must be less than chunkSize");
        }
    }

    private int normalizeOverlap(int overlap, int chunkSize) {
        return Math.min(overlap, chunkSize / 2);
    }

    private DocumentChunkSettingResponse toResponse(DocumentChunkSetting setting) {
        return DocumentChunkSettingResponse.builder()
                .id(setting.getId())
                .chunkSize(setting.getChunkSize())
                .chunkOverlap(setting.getChunkOverlap())
                .fromDatabase(true)
                .createdAt(setting.getCreatedAt())
                .updatedAt(setting.getUpdatedAt())
                .build();
    }

    private DocumentChunkSettingResponse defaultResponse() {
        return DocumentChunkSettingResponse.builder()
                .chunkSize(DEFAULT_CHUNK_SIZE)
                .chunkOverlap(DEFAULT_CHUNK_OVERLAP)
                .fromDatabase(false)
                .build();
    }

    private EffectiveChunkConfig defaultConfig() {
        return EffectiveChunkConfig.builder()
                .chunkSize(DEFAULT_CHUNK_SIZE)
                .chunkOverlap(DEFAULT_CHUNK_OVERLAP)
                .fromDatabase(false)
                .build();
    }
}
