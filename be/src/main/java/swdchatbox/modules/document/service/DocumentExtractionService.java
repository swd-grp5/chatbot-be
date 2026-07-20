package swdchatbox.modules.document.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
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

@Slf4j
@Service
public class DocumentExtractionService {

    /**
     * Tika's {@code parseToString} defaults to 100_000 chars and silently truncates —
     * long PDFs (e.g. textbooks) only indexed ~1/3 of the content. Use -1 = no limit.
     */
    private static final int WRITE_LIMIT = -1;

    private final DocumentFileRepository documentFileRepository;
    private final DocumentStorageService documentStorageService;
    private final AutoDetectParser parser = new AutoDetectParser();

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
        String text = joiner.toString().trim();
        log.info("[extract] documentId={} files={} chars={}", document.getId(), files.size(), text.length());
        return text;
    }

    private String extractFile(java.util.UUID documentId, DocumentFile file) {
        try (InputStream inputStream = documentStorageService.openInputStream(documentId, file)) {
            if (isPlainText(file.getMimeType(), file.getOriginalFileName())) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            Metadata metadata = new Metadata();
            BodyContentHandler handler = new BodyContentHandler(WRITE_LIMIT);
            parser.parse(inputStream, handler, metadata, new ParseContext());
            return handler.toString();
        } catch (ResourceNotFoundException e) {
            return "";
        } catch (IOException | TikaException | org.xml.sax.SAXException e) {
            throw new BadRequestException("Failed to extract text from file: " + file.getOriginalFileName());
        }
    }

    private boolean isPlainText(String mimeType, String originalFileName) {
        return (mimeType != null && mimeType.startsWith("text/"))
                || (originalFileName != null && originalFileName.toLowerCase().endsWith(".txt"));
    }
}
