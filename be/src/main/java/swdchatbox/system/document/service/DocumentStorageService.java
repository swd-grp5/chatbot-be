package swdchatbox.system.document.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import swdchatbox.system.common.exception.BadRequestException;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.document.entity.DocumentFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class DocumentStorageService {

    private final DocumentStorageProperties properties;
    private final Path basePath;

    public DocumentStorageService(DocumentStorageProperties properties) {
        this.properties = properties;
        this.basePath = Path.of(properties.getBasePath()).toAbsolutePath().normalize();
    }

    public StoredDocumentFile store(UUID documentId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File must not be empty");
        }

        try {
            Files.createDirectories(basePath.resolve(documentId.toString()));
            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : file.getName();
            String storedFileName = UUID.randomUUID() + "_" + originalName;
            Path target = basePath.resolve(documentId.toString()).resolve(storedFileName).normalize();
            if (!target.startsWith(basePath)) {
                throw new BadRequestException("Invalid storage path");
            }

            MessageDigest digest = MessageDigest.getInstance(properties.getChecksumAlgorithm());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            byte[] checksumBytes = digest.digest(file.getBytes());
            String checksum = HexFormat.of().formatHex(checksumBytes);
            return new StoredDocumentFile(originalName, storedFileName, target.toString(), checksum);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new BadRequestException("Failed to store uploaded file");
        }
    }

    public byte[] readFile(UUID documentId, DocumentFile file) {
        return readFileBytes(getReadableFilePath(documentId, file));
    }

    public Path getReadableFilePath(UUID documentId, DocumentFile file) {
        if (file == null) {
            throw new ResourceNotFoundException("File not found");
        }

        Path resolved = resolveFilePath(documentId, file);
        if (!Files.exists(resolved)) {
            throw new ResourceNotFoundException("File not found on disk");
        }

        return resolved;
    }

    public long getFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new BadRequestException("Failed to read stored file");
        }
    }

    private byte[] readFileBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new BadRequestException("Failed to read stored file");
        }
    }

    private Path resolveFilePath(UUID documentId, DocumentFile file) {
        Path fromBasePath = basePath.resolve(documentId.toString()).resolve(file.getStoredFileName()).normalize();
        if (Files.exists(fromBasePath)) {
            return fromBasePath;
        }

        if (file.getFilePath() != null && !file.getFilePath().isBlank()) {
            Path fromDatabase = Path.of(file.getFilePath()).toAbsolutePath().normalize();
            if (Files.exists(fromDatabase)) {
                return fromDatabase;
            }
        }

        return fromBasePath;
    }

    public void deleteFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (IOException e) {
            throw new BadRequestException("Failed to delete stored file");
        }
    }

    public void deleteDocumentFolder(UUID documentId) {
        Path folder = basePath.resolve(documentId.toString());
        if (!Files.exists(folder)) {
            return;
        }
        try (var paths = Files.walk(folder)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new BadRequestException("Failed to delete document files");
        } catch (RuntimeException e) {
            throw new BadRequestException("Failed to delete document files");
        }
    }

    public record StoredDocumentFile(String originalFileName, String storedFileName, String filePath, String checksum) {}
}
