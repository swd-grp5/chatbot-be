package swdchatbox.modules.document.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import swdchatbox.shared.exception.BadRequestException;
import swdchatbox.shared.exception.ResourceNotFoundException;
import swdchatbox.modules.document.entity.Document;
import swdchatbox.modules.document.entity.DocumentFile;
import swdchatbox.modules.document.repository.DocumentFileRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.StringJoiner;

@Service
public class DocumentExtractionService {

    private final DocumentFileRepository documentFileRepository;
    private final DocumentStorageService documentStorageService;
    private final Tika tika = new Tika();

    public DocumentExtractionService(
            DocumentFileRepository documentFileRepository,
            DocumentStorageService documentStorageService
    ) {
        this.documentFileRepository = documentFileRepository;
        this.documentStorageService = documentStorageService;
    }

    public String extract(Document document) {
        List<DocumentFile> files = documentFileRepository.findAllByDocument_Id(document.getId());
        if (files.isEmpty()) {
            return "";
        }

        StringJoiner joiner = new StringJoiner(System.lineSeparator() + System.lineSeparator());
        for (DocumentFile file : files) {
            joiner.add(extractFile(document.getId(), file));
        }
        return joiner.toString().trim();
    }

    private String extractFile(java.util.UUID documentId, DocumentFile file) {
        try (InputStream inputStream = documentStorageService.openInputStream(documentId, file)) {
            if (isPlainText(file.getMimeType(), file.getOriginalFileName())) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            return tika.parseToString(inputStream);
        } catch (ResourceNotFoundException e) {
            return "";
        } catch (IOException | TikaException e) {
            throw new BadRequestException("Failed to extract text from file: " + file.getOriginalFileName());
        }
    }

    private boolean isPlainText(String mimeType, String originalFileName) {
        return (mimeType != null && mimeType.startsWith("text/"))
                || (originalFileName != null && originalFileName.toLowerCase().endsWith(".txt"));
    }
}
