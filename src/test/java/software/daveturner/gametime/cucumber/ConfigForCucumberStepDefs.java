package software.daveturner.gametime.cucumber;

import org.springframework.context.annotation.*;
import org.springframework.web.client.*;

@Configuration
public class ConfigForCucumberStepDefs {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
