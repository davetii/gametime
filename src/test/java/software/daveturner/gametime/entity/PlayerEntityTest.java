package software.daveturner.gametime.entity;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.*;
import software.daveturner.gametime.model.*;

import static org.junit.jupiter.api.Assertions.*;


class PlayerEntityTest {

    PlayerEntity p1 = createPLayer("1");
    PlayerEntity p2 = createPLayer("1");
    PlayerEntity p3 = createPLayer("2");

    PlayerEntity empty_player = new PlayerEntity();

    @Test
    void ensureEqualsMethodReturnsExpected() {
        assertTrue(p1.equals(p2));
        assertTrue(p2.equals(p1));
        assertFalse(p1.equals(p3));
        assertFalse(p2.equals(p3));
        assertTrue(p3.equals(p3));
    }

    @Test
    void assertHash() {
        assertEquals(0, empty_player.hashCode());
        assertEquals("1".hashCode(), p1.hashCode());
    }

    public PlayerEntity createPLayer(String id) {
        PlayerEntity p = new PlayerEntity();
        p.setId(id);
        return p;
    }


    @Test
    void testToString() {
        Assertions.assertTrue(p1.toString().contains("id=1"));
    }
}