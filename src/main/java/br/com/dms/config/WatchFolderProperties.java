package br.com.dms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "watcher")
@Validated
public class WatchFolderProperties {

    private Duration scanInterval = Duration.ofSeconds(10);
    private List<WatchFolder> folders = new ArrayList<>();

    public Duration getScanInterval() {
        return scanInterval;
    }

    public void setScanInterval(Duration scanInterval) {
        this.scanInterval = scanInterval;
    }

    public List<WatchFolder> getFolders() {
        return folders;
    }

    public void setFolders(List<WatchFolder> folders) {
        this.folders = folders;
    }

    public static class WatchFolder {
        @NotBlank
        private String path;
        @NotBlank
        private String category;
        private boolean moveAfterUpload;
        private String archiveDirectory;
        private String tenantId;
        private boolean active = true;

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
