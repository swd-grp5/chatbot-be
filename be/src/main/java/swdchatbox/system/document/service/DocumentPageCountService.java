package swdchatbox.system.document.service;

import lombok.RequiredArgsConstructor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.document.entity.DocumentFile;
import swdchatbox.system.document.repository.DocumentFileRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentPageCountService {

    private static final String[] PAGE_COUNT_METADATA_KEYS = {
            "xmpTPg:NPages",
            "Page-Count",
            "meta:page-count",
            "pdf:docinfo:page_count"
    };

    private final DocumentFileRepository documentFileRepository;
    private final DocumentStorageService documentStorageService;
    private final AutoDetectParser parser = new AutoDetectParser();

    public int countDocumentPages(UUID documentId) {
        List<DocumentFile> files = documentFileRepository.findAllByDocument_Id(documentId);
        int total = 0;
        for (DocumentFile file : files) {
            total += countFilePages(documentId, file);
        }
        return total;
    }

    private int countFilePages(UUID documentId, DocumentFile file) {
        try {
            return countPages(documentId, file);
        } catch (ResourceNotFoundException ex) {
            return 0;
        }
    }

    private int countPages(UUID documentId, DocumentFile file) {
        if (isPlainText(file.getMimeType(), file.getOriginalFileName())) {
            return 1;
        }

        try (InputStream inputStream = documentStorageService.openInputStream(documentId, file)) {
            Metadata metadata = new Metadata();
            parser.parse(inputStream, new BodyContentHandler(-1), metadata, new ParseContext());
            return parsePageCount(metadata);
        } catch (IOException | org.apache.tika.exception.TikaException | org.xml.sax.SAXException ex) {
            return 0;
        }
    }

    private int parsePageCount(Metadata metadata) {
        for (String key : PAGE_COUNT_METADATA_KEYS) {
            String value = metadata.get(key);
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                int pages = Integer.parseInt(value.trim());
                if (pages > 0) {
                    return pages;
                }
            } catch (NumberFormatException ignored) {
                // try next metadata key
            }
        }
        return 0;
    }

    private boolean isPlainText(String mimeType, String originalFileName) {
        return (mimeType != null && mimeType.startsWith("text/"))
                || (originalFileName != null && originalFileName.toLowerCase().endsWith(".txt"));
    }
}
