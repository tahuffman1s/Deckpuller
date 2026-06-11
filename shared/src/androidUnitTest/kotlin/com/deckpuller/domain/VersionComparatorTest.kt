package com.deckpuller.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun `newer patch is newer`() {
        assertTrue(VersionComparator.isNewer("1.0.1", "1.0.0"))
    }

    @Test
    fun `minor beats trailing patch`() {
        assertTrue(VersionComparator.isNewer("1.1", "1.0.9"))
    }

    @Test
    fun `major bump is newer`() {
        assertTrue(VersionComparator.isNewer("2.0", "1.9.9"))
    }

    @Test
    fun `missing components are treated as zero`() {
        assertFalse(VersionComparator.isNewer("1.0", "1.0.0"))
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0"))
    }

    @Test
    fun `older is not newer`() {
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0.1"))
    }

    @Test
    fun `leading v prefix is ignored`() {
        assertTrue(VersionComparator.isNewer("v1.2.0", "1.1.0"))
        assertFalse(VersionComparator.isNewer("v1.0.0", "1.0.0"))
    }

    @Test
    fun `pre-release suffix is ignored on the numeric core`() {
        assertFalse(VersionComparator.isNewer("1.0.0-rc1", "1.0.0"))
        assertTrue(VersionComparator.isNewer("1.0.1-rc1", "1.0.0"))
    }
}
