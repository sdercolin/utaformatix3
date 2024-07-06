package core.process

/*
* The Ramer–Douglas–Peucker algorithm is a line simplification algorithm
* for reducing the number of points used to define its shape.
*
* Wikipedia: https://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm
* Implementation reference: https://rosettacode.org/wiki/Ramer-Douglas-Peucker_line_simplification
* */

// As UtaFormatix use Pair<Long, Double> to describe pitch point, so...
private typealias Point = Pair<Long, Double>

private fun perpendicularDistance(pt: Point, lineStart: Point, lineEnd: Point): Double {
    var dx = (lineEnd.first - lineStart.first).toDouble()
    var dy = lineEnd.second - lineStart.second

    // Normalize
    val mag = kotlin.math.hypot(dx, dy)
    if (mag > 0.0) {
        dx /= mag
        dy /= mag
    }
    val pvx = pt.first - lineStart.first
    val pvy = pt.second - lineStart.second

    // Get dot product (project pv onto normalized direction)
    val pvdot = dx * pvx + dy * pvy

    // Scale line direction vector and subtract it from pv
    val ax = pvx - pvdot * dx
    val ay = pvy - pvdot * dy

    return kotlin.math.hypot(ax, ay)
}

/**
 * The RDP(Ramer–Douglas–Peucker) simplification algorithm for line based shape.
 * Slightly modified from rosettacode.org's implementation.
 * @param pointList Points that form the shape, connected by lines.
 * @param epsilon Defines how much the algorithm should simplify. Bigger value leads to more point to drop.
 */
fun simplifyShape(pointList: List<Point>, epsilon: Double): List<Point> {
    if (pointList.size < 2) return pointList

    // Find the point with the maximum distance from line between start and end
    var dmax = 0.0
    var index = 0
    val end = pointList.size - 1
    for (i in 1 until end) {
        val d = perpendicularDistance(pointList[i], pointList[0], pointList[end])
        if (d > dmax) {
            index = i
            dmax = d
        }
    }

    // If max distance is greater than epsilon, recursively simplify
    return if (dmax > epsilon) {
        val firstLine = pointList.take(index + 1)
        val lastLine = pointList.drop(index)
        val recResults1 = simplifyShape(firstLine, epsilon)
        val recResults2 = simplifyShape(lastLine, epsilon)

        // build the result list
        listOf(recResults1.take(recResults1.size - 1), recResults2).flatten()
    } else {
        // Just return start and end points
        listOf(pointList.first(), pointList.last())
    }
}

/**
 * Simplify given shape, which should later be described by [maxPointCount] (or less) points.
 * Note that this function will simplify with a small epsilon anyway even your input count is satisfied.
 * Using [simplifyShape], which implements The RDP(Ramer–Douglas–Peucker) algorithm.
 * */
fun simplifyShapeTo(pointList: List<Point>, maxPointCount: Long): List<Point> {
    /* As sometimes we will have pit data that is less than 50 points, but way too dense for mode2 ust
       So we commented this check here to make sure data will be simplified least with epsilon = step. */
    val step = 0.05
    var epsilon = step
    while (true) {
        val result = simplifyShape(pointList, epsilon)
        if (result.count() < maxPointCount) return result
        epsilon += step
    }
}
