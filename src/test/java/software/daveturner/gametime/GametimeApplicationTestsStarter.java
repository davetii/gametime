package software.daveturner.gametime;

import org.junit.*;
import org.junit.runner.*;
import org.springframework.test.context.junit4.*;

@RunWith(SpringRunner.class)
public class GametimeApplicationTestsStarter {
    @Test
    public void applicationStarts() {
        GametimeApplication.main(new String[] {});
    }
}
