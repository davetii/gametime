package software.daveturner.gametime.entity;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class CoachEntityTest {

    CoachEntity p1 = createCoach("1");

    CoachEntity p2 = createCoach("1");
    CoachEntity p3 = createCoach("2");

    CoachEntity empty_coach = new CoachEntity();

    @Test
    void assertEquals() {
        assertTrue(p1.equals(p2));
        assertTrue(p2.equals(p1));
        assertFalse(p1.equals(p3));
        assertFalse(p2.equals(p3));
        assertTrue(p3.equals(p3));
    }

    @Test
    void assertHash() {
        Assertions.assertEquals(0, empty_coach.hashCode());
        Assertions.assertEquals("1".hashCode(), p1.hashCode());
    }

    @Test
    void assertToString() {
    }

    private CoachEntity createCoach(String id) {
        CoachEntity p = new CoachEntity();
        p.setId(id);
        return p;
    }
}