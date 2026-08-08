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

    // §3.12 (decisions.md #030 A1/C): these four REPLACE the isContactType()
    // assertions. That binary predicate is deleted — every shot type can now draw a
    // foul, at a graduated rate carried by SimConfig's FOUL_MULT_* table — and the
    // rule that took its place on this enum is the free-throw count.

    @Test
    void aFouledThreeAwardsThreeFreeThrows() {
        // The latent bug §3.12 activates and fixes: unreachable before, because a
        // THREE could not be fouled at all.
        assertEquals(3, ShotType.THREE.freeThrowsIfFouled());
    }

    @Test
    void aFouledDriveAwardsTwoFreeThrows() {
        assertEquals(2, ShotType.DRIVE.freeThrowsIfFouled());
    }

    @Test
    void aFouledPostAwardsTwoFreeThrows() {
        assertEquals(2, ShotType.POST.freeThrowsIfFouled());
    }

    @Test
    void aFouledPerimeterAwardsTwoFreeThrows() {
        assertEquals(2, ShotType.PERIMETER.freeThrowsIfFouled());
    }

    @ParameterizedTest
    @EnumSource(ShotType.class)
    void onlyAThreeAwardsMoreThanTwoFreeThrows(ShotType type) {
        assertEquals(type == ShotType.THREE ? 3 : 2, type.freeThrowsIfFouled());
    }

    @ParameterizedTest
    @EnumSource(ShotType.class)
    void allShotTypesHavePositivePoints(ShotType type) {
        assertTrue(type.getPoints() > 0);
    }
}
