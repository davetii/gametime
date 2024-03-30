package software.daveturner.gametime.cucumber;

import org.springframework.context.annotation.*;
import org.springframework.web.client.*;

@Configuration
public class StepDefsConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
