package software.daveturner.gametime;

import io.cucumber.junit.*;
import org.junit.runner.*;
import org.springframework.test.context.*;
import org.springframework.test.context.junit4.*;

@RunWith(Cucumber.class)
@CucumberOptions(
        features = {"src/test/resources/features"},
        plugin = {"pretty"},
        glue = {"software.daveturner.gametime.cucumber"})
public class CucumberRunner {
}
