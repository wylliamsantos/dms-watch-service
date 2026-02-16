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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

        AutomaticIngestionMessage message = new AutomaticIngestionMessage();
        message.setSourcePath(path.toAbsolutePath().toString());
        message.setStoredPath(storedPath.toAbsolutePath().toString());
        message.setFilename(path.getFileName().toString());
        message.setCategory(folder.getCategory());
        message.setTenantId(folder.getTenantId());
        message.setDiscoveredAt(Instant.now());

        publisher.publish(message);
        processedFiles.add(storedPath);
    }
}
