package otavio.openchat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    /**
     * WebClient configurado para comunicação com o Ollama.
     * O buffer é aumentado para 10 MB para suportar respostas grandes em streaming.
     */
    @Bean
    public WebClient ollamaWebClient() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(config -> config
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024)) // 10 MB
                .build();

        return WebClient.builder()
                .baseUrl(ollamaBaseUrl)
                .exchangeStrategies(strategies)
                .build();
    }
}