package software.daveturner.gametime.entity;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class TeamEntityTest {

    TeamEntity t1 = createTeam("1");
    TeamEntity t2 = createTeam("1");
    TeamEntity t3 = createTeam("2");
    TeamEntity empty_team = new TeamEntity();

    @Test
    void assertEquals() {
        assertTrue(t1.equals(t2));
        assertTrue(t2.equals(t1));
        assertFalse(t1.equals(t3));
        assertFalse(t2.equals(t3));
        assertTrue(t3.equals(t3));
    }

    @Test
    void assertToString() {
        Assertions.assertTrue(t1.toString().contains("id=1"));
    }

    private TeamEntity createTeam(String id) {
        TeamEntity t = new TeamEntity();
        t.setId(id);
        return t;
    }
}