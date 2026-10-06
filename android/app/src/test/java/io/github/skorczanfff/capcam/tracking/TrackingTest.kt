package io.github.skorczanfff.capcam.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingTest {

    private fun rad(deg: Double) = Math.toRadians(deg)

    /** Head pose built in the documented order: yaw (+ = right, so −z rotation), then pitch, then roll. */
    private fun head(yaw: Double, pitch: Double, roll: Double) =
        Quat.aboutZ(rad(-yaw)) * Quat.aboutX(rad(pitch)) * Quat.aboutY(rad(roll))

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
    fun signsMatchOpentrack() {
        // Turning left is a positive rotation about up (forward +y swings towards -x): yaw must be negative.
        assertEquals(-30.0, Quat.aboutZ(rad(30.0)).toHeadAngles().yaw, 1e-6)
        // Looking up moves the forward axis (+y) towards +z: pitch positive.
        assertEquals(10.0, Quat.aboutX(rad(10.0)).toHeadAngles().pitch, 1e-6)
        // Right ear down: up axis (+z) swings towards +x, a positive rotation about forward (+y).
        assertEquals(15.0, Quat.aboutY(rad(15.0)).toHeadAngles().roll, 1e-6)
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
        // With the device unrotated the head looks 90° to the right of world +y; the tracker zeroes it.
        val head = Mounting(Facing.SCREEN_UP, HeadDirection.LEFT).headOrientation(Quat.IDENTITY)
        assertEquals(90.0, head.toHeadAngles().yaw, 1e-6)

        val tracker = HeadTracker(Mounting(Facing.SCREEN_UP, HeadDirection.LEFT), settleNs = 0)
        assertAngles(HeadAngles(0.0, 0.0, 0.0), tracker.process(Quat.IDENTITY, 0).toHeadAngles())
    }

    @Test
    fun trackerZeroesYawButKeepsPitchAndRoll() {
        val m = Mounting(Facing.SCREEN_UP, HeadDirection.RIGHT)
        val tracker = HeadTracker(m, settleNs = 0)

        val start = tracker.process(deviceFor(head(140.0, 12.0, -7.0), m), 0).toHeadAngles()
        assertAngles(HeadAngles(0.0, 12.0, -7.0), start)

        val turned = tracker.process(deviceFor(head(170.0, 12.0, -7.0), m), 0).toHeadAngles()
        assertAngles(HeadAngles(30.0, 12.0, -7.0), turned)

        // Crossing ±180° in world yaw stays continuous relative to the start.
        val across = tracker.process(deviceFor(head(-160.0, 0.0, 0.0), m), 0).toHeadAngles()
        assertAngles(HeadAngles(60.0, 0.0, 0.0), across)

        tracker.recenter()
        val recentered = tracker.process(deviceFor(head(-160.0, 0.0, 0.0), m), 0).toHeadAngles()
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
    fun uprightScreenFacingYouIsStraightAhead() {
        // Phone standing upright in front of you, screen towards your face, top edge up:
        // device y = world up, device z = towards you (world -y). That's +90° about world x.
        val device = Quat.aboutX(rad(90.0))
        val head = Mounting(Facing.SCREEN_BACK, HeadDirection.UP).headOrientation(device)
        assertAngles(HeadAngles(0.0, 0.0, 0.0), head.toHeadAngles())
    }

    @Test
    fun uprightScreenForwardIsStraightAhead() {
        // Screen faces forward (world +y), top edge up: turn 180° about up after standing upright.
        val device = Quat.aboutZ(rad(180.0)) * Quat.aboutX(rad(90.0))
        val head = Mounting(Facing.SCREEN_FORWARD, HeadDirection.UP).headOrientation(device)
        assertAngles(HeadAngles(0.0, 0.0, 0.0), head.toHeadAngles())
    }

    @Test
    fun uprightMountTracksNodding() {
        // Looking up 15° tilts a forehead-mounted phone back by 15°.
        val m = Mounting(Facing.SCREEN_BACK, HeadDirection.LEFT)
        val tracker = HeadTracker(m, settleNs = 0)
        tracker.process(deviceFor(head(0.0, 0.0, 0.0), m), 0)
        assertAngles(HeadAngles(0.0, 15.0, 0.0), tracker.process(deviceFor(head(0.0, 15.0, 0.0), m), 0).toHeadAngles())
    }

    @Test
    fun everyFacingOffersFourTopEdges() {
        for (f in Facing.entries) {
            assertEquals(f.name, 4, f.topEdges.size)
        }
        assertEquals(12, Mounting.ALL.size)
    }

    @Test
    fun flippingTurnsThePhone180AboutTheScreen() {
        val pose = head(20.0, 10.0, -5.0)
        for (m in Mounting.ALL) {
            val f = m.flipped()
            assertEquals(m.facing, f.facing)
            assertEquals(m.topEdge.opposite, f.topEdge)
            assertEquals(m, f.flipped())
            // Both describe the same head pose once the device rotation matches the mount.
            assertAngles(pose.toHeadAngles(), f.headOrientation(deviceFor(pose, f)).toHeadAngles())
        }
    }

    @Test
    fun presetsMatchThemselvesAndTheirFlip() {
        for (p in MountPreset.entries) {
            assertEquals(p, MountPreset.of(p.mounting))
            assertEquals(p, MountPreset.of(p.mounting.flipped()))
        }
        assertEquals(null, MountPreset.of(Mounting(Facing.SCREEN_BACK, HeadDirection.UP)))
    }

    @Test
    fun trackerIgnoresTheFirstHalfSecondBeforeZeroing() {
        val m = Mounting(Facing.SCREEN_UP, HeadDirection.LEFT)
        val tracker = HeadTracker(m)
        val ms = 1_000_000L
        // A stale first event pointing somewhere else entirely must not become "forward".
        assertEquals(Quat.IDENTITY, tracker.process(deviceFor(head(170.0, 0.0, 0.0), m), 0))
        assertEquals(Quat.IDENTITY, tracker.process(deviceFor(head(40.0, 5.0, 0.0), m), 499 * ms))
        assertEquals(null, tracker.zeroYaw)
        // The first settled sample is forward; later turns are relative to it.
        assertAngles(HeadAngles(0.0, 5.0, 0.0), tracker.process(deviceFor(head(40.0, 5.0, 0.0), m), 500 * ms).toHeadAngles())
        assertAngles(HeadAngles(-20.0, 5.0, 0.0), tracker.process(deviceFor(head(20.0, 5.0, 0.0), m), 600 * ms).toHeadAngles())
        // Recenter zeroes on the next sample straight away (the sensor has settled by then).
        tracker.recenter()
        assertAngles(HeadAngles(0.0, 5.0, 0.0), tracker.process(deviceFor(head(20.0, 5.0, 0.0), m), 700 * ms).toHeadAngles())
    }
}
