package br.com.dms.service;

import br.com.dms.config.WatchFolderProperties.WatchFolder;
import br.com.dms.domain.AutomaticIngestionMessage;
import br.com.dms.service.publisher.DocumentIngestionPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DirectoryWatcherServiceTest {

    private WatchFolderConfigService configService;
    private DocumentIngestionPublisher publisher;
    private DirectoryWatcherService watcherService;
    private Path tempDirectory;
    private WatchFolder folder;

    @BeforeEach
    void setUp() throws IOException {
        tempDirectory = Files.createTempDirectory("watcher-test");
        folder = new WatchFolder();
        folder.setPath(tempDirectory.toString());
        folder.setCategory("AUTOMATIC");

        configService = Mockito.mock(WatchFolderConfigService.class);
        Mockito.when(configService.loadActiveFolders()).thenReturn(List.of(folder));

        publisher = Mockito.mock(DocumentIngestionPublisher.class);

        watcherService = new DirectoryWatcherService(configService, publisher);
    }

    @Test
    void shouldPublishNewFiles() throws IOException {
        Path file = Files.createTempFile(tempDirectory, "document", ".pdf");

        watcherService.scanFolders();

        ArgumentCaptor<AutomaticIngestionMessage> captor = ArgumentCaptor.forClass(AutomaticIngestionMessage.class);
        Mockito.verify(publisher).publish(captor.capture());

        AutomaticIngestionMessage message = captor.getValue();
        assertThat(message.getFilename()).isEqualTo(file.getFileName().toString());
        assertThat(message.getCategory()).isEqualTo("AUTOMATIC");
        assertThat(message.getSourcePath()).isEqualTo(file.toAbsolutePath().toString());
        assertThat(message.getStoredPath()).isEqualTo(file.toAbsolutePath().toString());
    }

    @Test
    void shouldMoveFileToArchiveDirectoryWhenConfigured() throws IOException {
        Path file = Files.createTempFile(tempDirectory, "document", ".pdf");
        Path archiveDirectory = Files.createTempDirectory("watcher-archive");
        folder.setMoveAfterUpload(true);
        folder.setArchiveDirectory(archiveDirectory.toString());

        watcherService.scanFolders();

        Path expectedLocation = archiveDirectory.resolve(file.getFileName());
        assertThat(Files.exists(expectedLocation)).isTrue();

        ArgumentCaptor<AutomaticIngestionMessage> captor = ArgumentCaptor.forClass(AutomaticIngestionMessage.class);
        Mockito.verify(publisher).publish(captor.capture());
        AutomaticIngestionMessage message = captor.getValue();
        assertThat(message.getStoredPath()).isEqualTo(expectedLocation.toAbsolutePath().toString());
        assertThat(message.getSourcePath()).isEqualTo(file.toAbsolutePath().toString());
    }
}
