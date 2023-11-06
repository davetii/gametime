package software.daveturner.gametime;

import io.cucumber.junit.*;
import org.junit.runner.*;

@RunWith(Cucumber.class)
@CucumberOptions(
        features = {"src/test/resources/features"},
        plugin = {"pretty"},
        glue = {"software.daveturner.gametime.cucumber"})
public class CucumberRunner {
}
