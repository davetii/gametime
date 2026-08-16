package software.daveturner.gametime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** {@code SimConfig} is a {@code @ConfigurationProperties} bean, so it needs the scan. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GametimeApplication {

	public static void main(String[] args) {
		SpringApplication.run(GametimeApplication.class, args);
	}

}
