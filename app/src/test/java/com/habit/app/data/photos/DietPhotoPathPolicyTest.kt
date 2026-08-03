package com.habit.app.data.photos

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DietPhotoPathPolicyTest {
    private val root = File(System.getProperty("java.io.tmpdir"), "habit-photo-policy")
    private val policy = DietPhotoPathPolicy(root)

    @Test(expected = IllegalArgumentException::class)
    fun resolvedPhotoCannotEscapePrivateRoot() {
        policy.resolve("../outside.jpg")
    }

    @Test
    fun libraryPhotoResolvesBelowPrivateRoot() {
        val resolved = policy.resolve("library/abc.jpg")
        assertTrue(resolved.canonicalPath.startsWith(root.canonicalPath + File.separator))
    }
}
