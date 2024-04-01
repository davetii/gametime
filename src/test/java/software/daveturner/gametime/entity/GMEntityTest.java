package software.daveturner.gametime.entity;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class GMEntityTest {

    GMEntity p1 = createGm("1");

    GMEntity p2 = createGm("1");
    GMEntity p3 = createGm("2");

    GMEntity empty_gm = new GMEntity();
    @Test
    void assertEquals() {
        assertTrue(p1.equals(p2));
        assertTrue(p2.equals(p1));
        assertFalse(p1.equals(p3));
        assertFalse(p2.equals(p3));
        assertTrue(p3.equals(p3));
    }

    @Test
    void assertHashCode() {
        Assertions.assertEquals(0, empty_gm.hashCode());
        Assertions.assertEquals("1".hashCode(), p1.hashCode());
    }

    private GMEntity createGm(String id) {
        GMEntity p = new GMEntity();
        p.setId(id);
        return p;
    }
}