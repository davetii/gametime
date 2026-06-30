package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class ShotTypeTest {

    @Test
    void driveIsTwoPoints() {
        assertEquals(2, ShotType.DRIVE.getPoints());
    }

    @Test
    void perimeterIsTwoPoints() {
        assertEquals(2, ShotType.PERIMETER.getPoints());
    }

    @Test
    void postIsTwoPoints() {
        assertEquals(2, ShotType.POST.getPoints());
    }

    @Test
    void threeIsThreePoints() {
        assertEquals(3, ShotType.THREE.getPoints());
    }

    @Test
    void driveIsContactType() {
        assertTrue(ShotType.DRIVE.isContactType());
    }

    @Test
    void postIsContactType() {
        assertTrue(ShotType.POST.isContactType());
    }

    @Test
    void perimeterIsNotContactType() {
        assertFalse(ShotType.PERIMETER.isContactType());
    }

    @Test
    void threeIsNotContactType() {
        assertFalse(ShotType.THREE.isContactType());
    }

    @ParameterizedTest
    @EnumSource(ShotType.class)
    void allShotTypesHavePositivePoints(ShotType type) {
        assertTrue(type.getPoints() > 0);
    }
}
