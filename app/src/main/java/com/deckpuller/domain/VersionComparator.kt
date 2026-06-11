package com.deckpuller.domain

/**
 * Compares dotted version strings (e.g. "1.2.3", "v1.2", "1.0.0-rc1").
 *
 * Only the leading numeric, dot-separated components are considered; a leading
 * "v" and any trailing non-numeric suffix (build metadata, pre-release tags) are
 * ignored. Missing trailing components are treated as 0, so "1.2" == "1.2.0".
 */
object VersionComparator {

    /** True when [candidate] represents a strictly newer version than [current]. */
    fun isNewer(candidate: String, current: String): Boolean =
        compare(candidate, current) > 0

    /** Standard comparator contract: negative / zero / positive. */
    fun compare(a: String, b: String): Int {
        val pa = parse(a)
        val pb = parse(b)
        val max = maxOf(pa.size, pb.size)
        for (i in 0 until max) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun parse(version: String): List<Int> {
        val trimmed = version.trim().removePrefix("v").removePrefix("V")
        // Keep the leading numeric core, dropping any "-suffix" / "+meta".
        val core = trimmed.takeWhile { it.isDigit() || it == '.' }
        return core.split('.')
            .mapNotNull { it.toIntOrNull() }
            .ifEmpty { listOf(0) }
    }
}
