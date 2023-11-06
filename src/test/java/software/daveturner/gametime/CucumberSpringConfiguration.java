package software.daveturner.gametime;

import io.cucumber.spring.*;
import org.springframework.boot.test.context.*;
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class CucumberSpringConfiguration {

}
