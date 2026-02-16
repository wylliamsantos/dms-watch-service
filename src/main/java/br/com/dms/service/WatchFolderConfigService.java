package br.com.dms.service;

import br.com.dms.config.WatchFolderProperties;
import br.com.dms.config.WatchFolderProperties.WatchFolder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WatchFolderConfigService {

    private final WatchFolderProperties properties;
    private final RestTemplate restTemplate;
    private final String documentServiceBaseUrl;
    private final String documentServiceToken;

    public WatchFolderConfigService(WatchFolderProperties properties,
                                    RestTemplateBuilder restTemplateBuilder,
                                    @org.springframework.beans.factory.annotation.Value("${watcher.document-service.base-url:}") String documentServiceBaseUrl,
                                    @org.springframework.beans.factory.annotation.Value("${watcher.document-service.token:}") String documentServiceToken,
                                    @org.springframework.beans.factory.annotation.Value("${watcher.document-service.timeout:PT5S}") Duration timeout) {
        this.properties = properties;
        this.documentServiceBaseUrl = documentServiceBaseUrl;
        this.documentServiceToken = documentServiceToken;
        this.restTemplate = restTemplateBuilder
            .setConnectTimeout(timeout)
            .setReadTimeout(timeout)
            .build();
    }

    public List<WatchFolder> loadActiveFolders() {
        if (StringUtils.hasText(documentServiceBaseUrl)) {
            return fetchFromDocumentService();
        }

        return properties.getFolders().stream()
            .filter(WatchFolder::isActive)
            .collect(Collectors.toList());
    }

    private List<WatchFolder> fetchFromDocumentService() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("TransactionId", UUID.randomUUID().toString());
        if (StringUtils.hasText(documentServiceToken)) {
            headers.add(HttpHeaders.AUTHORIZATION, documentServiceToken);
        }
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<RemoteWatchFolder[]> response = restTemplate.exchange(
                documentServiceBaseUrl + "/v1/watch-folders/active",
                HttpMethod.GET,
                request,
                RemoteWatchFolder[].class
            );

            RemoteWatchFolder[] body = response.getBody();
            if (body == null) {
                return List.of();
            }

            return Arrays.stream(body)
                .filter(RemoteWatchFolder::isActive)
                .map(this::toWatchFolder)
                .collect(Collectors.toList());
        } catch (Exception ex) {
            return properties.getFolders().stream()
                .filter(WatchFolder::isActive)
                .collect(Collectors.toList());
        }
    }

    private WatchFolder toWatchFolder(RemoteWatchFolder remote) {
        WatchFolder folder = new WatchFolder();
        folder.setPath(remote.getPath());
        folder.setCategory(remote.getCategory());
        folder.setMoveAfterUpload(remote.isMoveAfterUpload());
        folder.setArchiveDirectory(remote.getArchiveDirectory());
        folder.setTenantId(remote.getTenantId());
        folder.setActive(remote.isActive());
        return folder;
    }

    private static class RemoteWatchFolder {
        private String path;
        private String category;
        private boolean moveAfterUpload;
        private String archiveDirectory;
        private String tenantId;
        private boolean active;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public boolean isMoveAfterUpload() {
            return moveAfterUpload;
        }

        public void setMoveAfterUpload(boolean moveAfterUpload) {
            this.moveAfterUpload = moveAfterUpload;
        }

        public String getArchiveDirectory() {
            return archiveDirectory;
        }

        public void setArchiveDirectory(String archiveDirectory) {
            this.archiveDirectory = archiveDirectory;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
