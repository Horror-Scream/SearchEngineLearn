package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "crawler")
public class CrawlerConfig {
    private String userAgent;
    private String referrer;
    private int delayMinMs;
    private int delayMaxMs;
}