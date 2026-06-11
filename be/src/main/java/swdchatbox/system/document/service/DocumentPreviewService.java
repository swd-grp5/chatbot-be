package swdchatbox.system.document.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.document.dto.response.DocumentPreviewResponse;
import swdchatbox.system.document.entity.Document;
import swdchatbox.system.document.entity.DocumentFile;
import swdchatbox.system.document.enums.DocumentType;
import swdchatbox.system.document.enums.PreviewContentType;
import swdchatbox.system.document.repository.DocumentFileRepository;
import swdchatbox.system.document.repository.DocumentRepository;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentPreviewService {

    private static final int MAX_TEXT_CHARS = 4_000;
    private static final int MAX_IMAGE_WIDTH = 800;
    private static final float PDF_PREVIEW_DPI = 120f;

    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;
    private final DocumentStorageService documentStorageService;
    private final AutoDetectParser parser = new AutoDetectParser();

    public DocumentPreviewResponse preview(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        DocumentFile file = documentFileRepository.findAllByDocument_Id(documentId).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        DocumentType documentType = document.getDocumentType();
        try {
            return switch (documentType) {
                case PDF -> renderPdfFirstPage(document, file);
                case TXT -> renderTextPreview(document, file, true);
                case DOCX, PPTX -> renderTextPreview(document, file, false);
                default -> renderFallbackPreview(document, file);
            };
        } catch (IOException e) {
            log.warn("Preview failed documentId={} fileName={}", documentId, file.getOriginalFileName(), e);
            return unsupportedPreview(document, file);
        }
    }

    private DocumentPreviewResponse renderPdfFirstPage(Document document, DocumentFile file) throws IOException {
        Path tempFile = Files.createTempFile("doc-preview-", ".pdf");
        try {
            try (InputStream inputStream = documentStorageService.openInputStream(document.getId(), file)) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            try (PDDocument pdf = Loader.loadPDF(tempFile.toFile())) {
                if (pdf.getNumberOfPages() < 1) {
                    return unsupportedPreview(document, file);
                }

                PDFRenderer renderer = new PDFRenderer(pdf);
                BufferedImage image = renderer.renderImageWithDPI(0, PDF_PREVIEW_DPI);
                BufferedImage scaled = scaleImage(image, MAX_IMAGE_WIDTH);

                byte[] pngBytes = writePng(scaled);
                return DocumentPreviewResponse.builder()
                        .documentId(document.getId())
                        .fileName(file.getOriginalFileName())
                        .documentType(document.getDocumentType())
                        .totalPages(document.getTotalPages())
                        .previewContentType(PreviewContentType.IMAGE)
                        .mimeType("image/png")
                        .contentBase64(Base64.getEncoder().encodeToString(pngBytes))
                        .truncated(false)
                        .build();
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private DocumentPreviewResponse renderTextPreview(Document document, DocumentFile file, boolean plainTextOnly)
            throws IOException {
        String text;
        boolean truncated;

        if (plainTextOnly) {
            try (InputStream inputStream = documentStorageService.openInputStream(document.getId(), file)) {
                byte[] bytes = inputStream.readNBytes(MAX_TEXT_CHARS + 1);
                truncated = bytes.length > MAX_TEXT_CHARS;
                int length = Math.min(bytes.length, MAX_TEXT_CHARS);
                text = new String(bytes, 0, length, StandardCharsets.UTF_8).trim();
            }
        } else {
            BodyContentHandler handler = new BodyContentHandler(MAX_TEXT_CHARS);
            try (InputStream inputStream = documentStorageService.openInputStream(document.getId(), file)) {
                Metadata metadata = new Metadata();
                if (file.getMimeType() != null) {
                    metadata.set(Metadata.CONTENT_TYPE, file.getMimeType());
                }
                parser.parse(inputStream, handler, metadata, new ParseContext());
            } catch (org.apache.tika.exception.TikaException | org.xml.sax.SAXException e) {
                throw new IOException("Failed to extract preview text", e);
            }
            text = handler.toString().trim();
            truncated = text.length() >= MAX_TEXT_CHARS;
        }

        if (text.isEmpty()) {
            return unsupportedPreview(document, file);
        }

        return DocumentPreviewResponse.builder()
                .documentId(document.getId())
                .fileName(file.getOriginalFileName())
                .documentType(document.getDocumentType())
                .totalPages(document.getTotalPages())
                .previewContentType(PreviewContentType.TEXT)
                .mimeType("text/plain; charset=utf-8")
                .textPreview(text)
                .truncated(truncated)
                .build();
    }

    private DocumentPreviewResponse renderFallbackPreview(Document document, DocumentFile file) throws IOException {
        String mimeType = file.getMimeType();
        if (mimeType != null && mimeType.startsWith("image/")) {
            try (InputStream inputStream = documentStorageService.openInputStream(document.getId(), file)) {
                BufferedImage image = ImageIO.read(inputStream);
                if (image == null) {
                    return unsupportedPreview(document, file);
                }
                BufferedImage scaled = scaleImage(image, MAX_IMAGE_WIDTH);
                byte[] imageBytes = writeImage(scaled, mimeType);
                String outputMimeType = mimeType.startsWith("image/png") ? "image/png" : "image/jpeg";

                return DocumentPreviewResponse.builder()
                        .documentId(document.getId())
                        .fileName(file.getOriginalFileName())
                        .documentType(document.getDocumentType())
                        .totalPages(document.getTotalPages())
                        .previewContentType(PreviewContentType.IMAGE)
                        .mimeType(outputMimeType)
                        .contentBase64(Base64.getEncoder().encodeToString(imageBytes))
                        .truncated(false)
                        .build();
            }
        }

        if (mimeType != null && mimeType.startsWith("text/")) {
            return renderTextPreview(document, file, true);
        }

        return unsupportedPreview(document, file);
    }

    private DocumentPreviewResponse unsupportedPreview(Document document, DocumentFile file) {
        return DocumentPreviewResponse.builder()
                .documentId(document.getId())
                .fileName(file.getOriginalFileName())
                .documentType(document.getDocumentType())
                .totalPages(document.getTotalPages())
                .previewContentType(PreviewContentType.UNSUPPORTED)
                .truncated(false)
                .build();
    }

    private BufferedImage scaleImage(BufferedImage source, int maxWidth) {
        if (source.getWidth() <= maxWidth) {
            return source;
        }
        int height = (int) Math.round(source.getHeight() * ((double) maxWidth / source.getWidth()));
        Image scaled = source.getScaledInstance(maxWidth, height, Image.SCALE_SMOOTH);
        BufferedImage output = new BufferedImage(maxWidth, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.drawImage(scaled, 0, 0, null);
        graphics.dispose();
        return output;
    }

    private byte[] writePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private byte[] writeImage(BufferedImage image, String mimeType) throws IOException {
        String format = mimeType.contains("png") ? "png" : "jpeg";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}
