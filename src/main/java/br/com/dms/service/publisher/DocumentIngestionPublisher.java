package br.com.dms.service.publisher;

import br.com.dms.domain.AutomaticIngestionMessage;

public interface DocumentIngestionPublisher {

    void publish(AutomaticIngestionMessage message);
}
