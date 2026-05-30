package swdchatbox.system.document.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import swdchatbox.system.common.exception.BadRequestException;
import swdchatbox.system.document.entity.Document;
import swdchatbox.system.document.entity.DocumentFile;
import swdchatbox.system.document.enums.DocumentType;
import swdchatbox.system.document.repository.DocumentFileRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;

@Service
public class DocumentExtractionService {

    private final DocumentFileRepository documentFileRepository;
    private final Tika tika = new Tika();

    public DocumentExtractionService(DocumentFileRepository documentFileRepository) {
        this.documentFileRepository = documentFileRepository;
    }

    public String extract(Document document) {
        List<DocumentFile> files = documentFileRepository.findAllByDocument_Id(document.getId());
        if (files.isEmpty()) {
            return "";
        }

        StringJoiner joiner = new StringJoiner(System.lineSeparator() + System.lineSeparator());
        for (DocumentFile file : files) {
            joiner.add(extractFile(file));
        }
        return joiner.toString().trim();
    }

    private String extractFile(DocumentFile file) {
        Path path = Path.of(file.getFilePath());
        if (!Files.exists(path)) {
            return "";
        }

        try {
            if (isPlainText(file.getMimeType(), file.getOriginalFileName())) {
                return Files.readString(path);
            }
            return tika.parseToString(path);
        } catch (IOException | TikaException e) {
            throw new BadRequestException("Failed to extract text from file: " + file.getOriginalFileName());
        }
    }

    private boolean isPlainText(String mimeType, String originalFileName) {
        return (mimeType != null && mimeType.startsWith("text/"))
                || (originalFileName != null && originalFileName.toLowerCase().endsWith(".txt"));
    }
}
