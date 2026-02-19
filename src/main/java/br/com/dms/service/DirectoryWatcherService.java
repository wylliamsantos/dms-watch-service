package br.com.dms.service;

import br.com.dms.config.WatchFolderProperties.WatchFolder;
import br.com.dms.domain.AutomaticIngestionMessage;
import br.com.dms.service.publisher.DocumentIngestionPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class DirectoryWatcherService {

    private static final Logger logger = LoggerFactory.getLogger(DirectoryWatcherService.class);

    private final WatchFolderConfigService configService;
    private final DocumentIngestionPublisher publisher;
    private final Map<String, Set<Path>> processedFilesByFolder = new HashMap<>();

    public DirectoryWatcherService(WatchFolderConfigService configService, DocumentIngestionPublisher publisher) {
        this.configService = configService;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${watcher.scan-interval:PT10S}")
    public void scanFolders() {
        configService.loadActiveFolders().forEach(this::processFolder);
    }

    private void processFolder(WatchFolder folder) {
        Path basePath = Paths.get(folder.getPath());
        if (!Files.exists(basePath)) {
            logger.warn("Configured watch folder {} does not exist", basePath);
            return;
        }

        processedFilesByFolder.putIfAbsent(folder.getPath(), new HashSet<>());
        Set<Path> processedFiles = processedFilesByFolder.get(folder.getPath());

        try (Stream<Path> stream = Files.list(basePath)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> !processedFiles.contains(path))
                .forEach(path -> handleNewFile(path, folder, processedFiles));
        } catch (IOException e) {
            logger.error("Failed to scan folder {}", folder.getPath(), e);
        }
    }

    private void handleNewFile(Path path, WatchFolder folder, Set<Path> processedFiles) {
        logger.info("Discovered file {} in folder {}", path, folder.getPath());
        Path storedPath = path;

        if (folder.isMoveAfterUpload() && StringUtils.hasText(folder.getArchiveDirectory())) {
            Path archiveDirectory = Paths.get(folder.getArchiveDirectory());
            try {
                Files.createDirectories(archiveDirectory);
                Path target = archiveDirectory.resolve(path.getFileName());
                Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Moved file {} to archive {}", path, target);
                storedPath = target;
            } catch (IOException e) {
                logger.error("Failed to move file {} to archive directory {}", path, archiveDirectory, e);
                return;
            }
        }

        if (!StringUtils.hasText(folder.getTenantId())) {
            logger.warn("Skipping file {} because watch folder {} has no tenantId configured", path, folder.getPath());
            return;
        }

        AutomaticIngestionMessage message = new AutomaticIngestionMessage();
        message.setSourcePath(path.toAbsolutePath().toString());
        message.setStoredPath(storedPath.toAbsolutePath().toString());
        message.setFilename(path.getFileName().toString());
        message.setCategory(folder.getCategory());
        message.setTenantId(folder.getTenantId());
        message.setDiscoveredAt(Instant.now());

        message.setEventType("DOCUMENT_WATCHED");
        message.setOccurredAt(message.getDiscoveredAt());
        message.setUserId("watch-service");
        message.setEntityType("DOCUMENT");
        message.setEntityId(storedPath.getFileName().toString());

        String idempotencyKey = generateIdempotencyKey(folder.getTenantId(), storedPath);
        message.setMetadata(Map.of(
            "category", folder.getCategory(),
            "sourcePath", path.toAbsolutePath().toString(),
            "storedPath", storedPath.toAbsolutePath().toString(),
            "idempotencyKey", idempotencyKey
        ));
        message.setAttributes(Map.of(
            "origin", "watch-service",
            "moveAfterUpload", folder.isMoveAfterUpload(),
            "idempotencyKey", idempotencyKey
        ));

        publisher.publish(message);
        processedFiles.add(storedPath);
    }

    private String generateIdempotencyKey(String tenantId, Path storedPath) {
        String raw = tenantId + "::" + storedPath.toAbsolutePath();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.warn("SHA-256 unavailable, using raw idempotency key fallback", e);
            return raw;
        }
    }
}
