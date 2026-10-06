package io.github.skorczanfff.capcam.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingTest {

    private fun rad(deg: Double) = Math.toRadians(deg)

    /** Head pose built in the documented order: yaw, then pitch, then roll. */
    private fun head(yaw: Double, pitch: Double, roll: Double) =
        Quat.aboutZ(rad(yaw)) * Quat.aboutX(rad(pitch)) * Quat.aboutY(rad(roll))

    private fun inverse(q: Quat) = Quat(-q.x, -q.y, -q.z, q.w)

    /** The device rotation the sensor would report for a given head pose and mounting. */
    private fun deviceFor(head: Quat, mounting: Mounting): Quat {
        val headToDevice = mounting.headOrientation(Quat.IDENTITY)
        return head * inverse(headToDevice)
    }

    private fun assertAngles(expected: HeadAngles, actual: HeadAngles) {
        assertEquals("yaw", expected.yaw, actual.yaw, 1e-6)
        assertEquals("pitch", expected.pitch, actual.pitch, 1e-6)
        assertEquals("roll", expected.roll, actual.roll, 1e-6)
    }

    @Test
    fun identityHasZeroAngles() {
        assertAngles(HeadAngles(0.0, 0.0, 0.0), Quat.IDENTITY.toHeadAngles())
    }

    @Test
    fun eulerRoundTrip() {
        val cases = listOf(
            HeadAngles(30.0, 0.0, 0.0),
            HeadAngles(0.0, 20.0, 0.0),
            HeadAngles(0.0, 0.0, -15.0),
            HeadAngles(-120.0, -35.0, 25.0),
            HeadAngles(170.0, 60.0, -40.0),
        )
        for (c in cases) assertAngles(c, head(c.yaw, c.pitch, c.roll).toHeadAngles())
    }

    @Test
    fun rightHandSigns() {
        // Turning left moves the forward axis (+y) towards -x.
        val left = head(90.0, 0.0, 0.0)
        assertAngles(HeadAngles(90.0, 0.0, 0.0), left.toHeadAngles())
        // Looking up moves the forward axis (+y) towards +z.
        val up = Quat.aboutX(rad(10.0))
        assertEquals(10.0, up.toHeadAngles().pitch, 1e-6)
    }

    @Test
    fun everyMountingRecoversTheHeadPose() {
        val pose = head(25.0, -10.0, 5.0)
        for (m in Mounting.ALL) {
            assertAngles(pose.toHeadAngles(), m.headOrientation(deviceFor(pose, m)).toHeadAngles())
        }
    }

    @Test
    fun landscapeTopLeftLyingStillLooksStraightAhead() {
        // Phone flat, screen up, top edge pointing left: the head faces the device's +x axis.
        // With the device unrotated that's world -90° yaw; the tracker zeroes it.
        val head = Mounting(Facing.SCREEN_UP, HeadDirection.LEFT).headOrientation(Quat.IDENTITY)
        assertEquals(-90.0, head.toHeadAngles().yaw, 1e-6)

        val tracker = HeadTracker(Mounting(Facing.SCREEN_UP, HeadDirection.LEFT))
        assertAngles(HeadAngles(0.0, 0.0, 0.0), tracker.process(Quat.IDENTITY).toHeadAngles())
    }

    @Test
    fun trackerZeroesYawButKeepsPitchAndRoll() {
        val m = Mounting(Facing.SCREEN_UP, HeadDirection.RIGHT)
        val tracker = HeadTracker(m)

        val start = tracker.process(deviceFor(head(140.0, 12.0, -7.0), m)).toHeadAngles()
        assertAngles(HeadAngles(0.0, 12.0, -7.0), start)

        val turned = tracker.process(deviceFor(head(170.0, 12.0, -7.0), m)).toHeadAngles()
        assertAngles(HeadAngles(30.0, 12.0, -7.0), turned)

        // Crossing ±180° in world yaw stays continuous relative to the start.
        val across = tracker.process(deviceFor(head(-160.0, 0.0, 0.0), m)).toHeadAngles()
        assertAngles(HeadAngles(60.0, 0.0, 0.0), across)

        tracker.recenter()
        val recentered = tracker.process(deviceFor(head(-160.0, 0.0, 0.0), m)).toHeadAngles()
        assertAngles(HeadAngles(0.0, 0.0, 0.0), recentered)
    }

    @Test
    fun flatMountsMatchTheOriginalPresets() {
        // Before upright mounts existed, flat mounts were plain rotations about the screen normal.
        val expected = mapOf(
            HeadDirection.LEFT to -90.0,
            HeadDirection.RIGHT to 90.0,
            HeadDirection.FORWARD to 0.0,
            HeadDirection.BACK to 180.0,
        )
        val device = head(37.0, -12.0, 8.0)
        for ((top, deg) in expected) {
            val viaMount = Mounting(Facing.SCREEN_UP, top).headOrientation(device).toHeadAngles()
            val viaZ = (device * Quat.aboutZ(rad(deg))).toHeadAngles()
            assertAngles(viaZ, viaMount)
        }
    }

    @Test
    fun uprightCameraForwardPhoneFacingYouIsStraightAhead() {
        // Phone standing upright in front of you, screen towards your face, top edge up:
        // device y = world up, device z = towards you (world -y). That's +90° about world x.
        val device = Quat.aboutX(rad(90.0))
        val head = Mounting(Facing.CAMERA_FORWARD, HeadDirection.UP).headOrientation(device)
        assertAngles(HeadAngles(0.0, 0.0, 0.0), head.toHeadAngles())
    }

    @Test
    fun uprightCameraBackScreenForwardIsStraightAhead() {
        // Screen faces forward (world +y), top edge up: turn 180° about up after standing upright.
        val device = Quat.aboutZ(rad(180.0)) * Quat.aboutX(rad(90.0))
        val head = Mounting(Facing.CAMERA_BACK, HeadDirection.UP).headOrientation(device)
        assertAngles(HeadAngles(0.0, 0.0, 0.0), head.toHeadAngles())
    }

    @Test
    fun uprightMountTracksNodding() {
        // Looking up 15° tilts a forehead-mounted phone back by 15°.
        val m = Mounting(Facing.CAMERA_FORWARD, HeadDirection.LEFT)
        val tracker = HeadTracker(m)
        tracker.process(deviceFor(head(0.0, 0.0, 0.0), m))
        assertAngles(HeadAngles(0.0, 15.0, 0.0), tracker.process(deviceFor(head(0.0, 15.0, 0.0), m)).toHeadAngles())
    }

    @Test
    fun everyFacingOffersFourTopEdges() {
        for (f in Facing.entries) {
            assertEquals(f.name, 4, f.topEdges.size)
        }
        assertEquals(12, Mounting.ALL.size)
    }
}
