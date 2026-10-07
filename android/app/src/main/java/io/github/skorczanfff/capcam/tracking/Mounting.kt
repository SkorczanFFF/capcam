package io.github.skorczanfff.capcam.tracking

/** Directions in the head frame: x right, y forward, z up. */
enum class HeadDirection(val x: Int, val y: Int, val z: Int, val label: String) {
    LEFT(-1, 0, 0, "left"),
    RIGHT(1, 0, 0, "right"),
    FORWARD(0, 1, 0, "forward"),
    BACK(0, -1, 0, "back"),
    UP(0, 0, 1, "up"),
    DOWN(0, 0, -1, "down");

    val opposite: HeadDirection
        get() = entries.first { it.x == -x && it.y == -y && it.z == -z }

    fun isPerpendicularTo(o: HeadDirection) = x * o.x + y * o.y + z * o.z == 0
}

/** Which way the screen faces. The rear camera always points the opposite way. */
enum class Facing(val label: String, val screenNormal: HeadDirection) {
    SCREEN_UP("Flat, screen up", HeadDirection.UP),
    SCREEN_FORWARD("Upright, screen facing forward", HeadDirection.FORWARD),
    SCREEN_BACK("Upright, screen facing your forehead", HeadDirection.BACK);

    /** Top-edge directions that make sense for this facing (perpendicular to the screen). */
    val topEdges: List<HeadDirection>
        get() = HeadDirection.entries.filter { it.isPerpendicularTo(screenNormal) }

    /** Top edge pointing sideways: the phone lies across the head (landscape). */
    val landscapeEdges: List<HeadDirection>
        get() = topEdges.filter { it == HeadDirection.LEFT || it == HeadDirection.RIGHT }

    /** Top edge along the head (forward/back when flat, up/down when upright). */
    val portraitEdges: List<HeadDirection>
        get() = topEdges - landscapeEdges.toSet()
}

/** How the phone sits on the head: which way the screen faces and where its top edge points. */
data class Mounting(val facing: Facing, val topEdge: HeadDirection) {

    init {
        require(topEdge.isPerpendicularTo(facing.screenNormal)) { "Top edge can't point along the screen normal" }
    }

    val isLandscape: Boolean get() = topEdge in facing.landscapeEdges

    /** The same position with the phone turned 180° about its screen normal. */
    fun flipped() = Mounting(facing, topEdge.opposite)

    /** Maps head coordinates to device coordinates. */
    private val headToDevice: Quat = run {
        // Device axes expressed in head coordinates: y = top edge, z = out of the screen, x = y × z.
        val y = topEdge
        val z = facing.screenNormal
        val x = intArrayOf(y.y * z.z - y.z * z.y, y.z * z.x - y.x * z.z, y.x * z.y - y.y * z.x)
        // Rows are the device axes, so this matrix takes head coordinates to device coordinates.
        Quat.fromRotationMatrix(
            arrayOf(
                doubleArrayOf(x[0].toDouble(), x[1].toDouble(), x[2].toDouble()),
                doubleArrayOf(y.x.toDouble(), y.y.toDouble(), y.z.toDouble()),
                doubleArrayOf(z.x.toDouble(), z.y.toDouble(), z.z.toDouble()),
            )
        )
    }

    /** Turns the sensor's device → world rotation into a head → world rotation. */
    fun headOrientation(deviceToWorld: Quat): Quat = deviceToWorld * headToDevice

    companion object {
        val DEFAULT get() = MountPreset.LYING_FLAT.mounting
        val ALL: List<Mounting> = Facing.entries.flatMap { f -> f.topEdges.map { Mounting(f, it) } }
    }
}

/**
 * The three ways we expect people to wear the phone. Only the phone's orientation matters for
 * tracking, not where on the head it sits, so "standing" covers the brim, forehead and helmet.
 * Each preset can also be turned 180°.
 */
enum class MountPreset(val title: String, val where: String, val mounting: Mounting) {
    LYING_FLAT(
        "Lying flat",
        "On the cap brim",
        Mounting(Facing.SCREEN_UP, HeadDirection.LEFT),
    ),
    STANDING_PORTRAIT(
        "Standing, portrait",
        "On the brim, forehead or helmet",
        Mounting(Facing.SCREEN_FORWARD, HeadDirection.UP),
    ),
    STANDING_LANDSCAPE(
        "Standing, landscape",
        "On the brim, forehead or helmet",
        Mounting(Facing.SCREEN_FORWARD, HeadDirection.LEFT),
    );

    fun matches(m: Mounting) = m == mounting || m == mounting.flipped()

    companion object {
        fun of(m: Mounting): MountPreset? = entries.firstOrNull { it.matches(m) }
    }
}
