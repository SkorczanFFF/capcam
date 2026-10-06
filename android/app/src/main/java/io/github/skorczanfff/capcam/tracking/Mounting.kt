package io.github.skorczanfff.capcam.tracking

/** Directions in the head frame: x right, y forward, z up. */
enum class HeadDirection(val x: Int, val y: Int, val z: Int, val label: String) {
    LEFT(-1, 0, 0, "left"),
    RIGHT(1, 0, 0, "right"),
    FORWARD(0, 1, 0, "forward"),
    BACK(0, -1, 0, "back"),
    UP(0, 0, 1, "up"),
    DOWN(0, 0, -1, "down");

    fun isPerpendicularTo(o: HeadDirection) = x * o.x + y * o.y + z * o.z == 0
}

/** Which way the screen faces. The rear camera always points the opposite way. */
enum class Facing(val label: String, val screenNormal: HeadDirection) {
    SCREEN_UP("Flat, screen up (cap brim)", HeadDirection.UP),
    CAMERA_FORWARD("Upright, camera forward (forehead, helmet front)", HeadDirection.BACK),
    CAMERA_BACK("Upright, screen forward", HeadDirection.FORWARD);

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

    val label: String
        get() = "${facing.label} · ${if (isLandscape) "landscape" else "portrait"}, top edge ${topEdge.label}"

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
        val DEFAULT = Mounting(Facing.SCREEN_UP, HeadDirection.LEFT)
        val ALL: List<Mounting> = Facing.entries.flatMap { f -> f.topEdges.map { Mounting(f, it) } }
    }
}
