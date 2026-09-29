package com.aiplacementos.app.data.local

import com.aiplacementos.app.data.remote.CareerPathDto
import com.aiplacementos.app.ui.careerpath.sampleCareerPath
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The completion predicate, case by case, against the fields select_role() actually writes:
 * is_primary, custom_role (the text sent, stripped) and updated_at/id.
 */
class PendingCareerPathGenerationTest {

    private fun pending(role: String = "Data Scientist", before: CurrentPathBefore) =
        PendingCareerPathGeneration(operationId = "op-1", requestedRole = role, startedAtMillis = 0L, before = before)

    private fun current(id: Int, role: String?, updatedAt: String, isPrimary: Boolean = true): CareerPathDto =
        sampleCareerPath(id = id, isPrimary = isPrimary).copy(customRole = role, updatedAt = updatedAt)

    @Test
    fun `no current path is never success`() {
        assertFalse(pending(before = CurrentPathBefore.None).isCompletedBy(null))
    }

    @Test
    fun `a first path for the requested role is success`() {
        assertTrue(pending(before = CurrentPathBefore.None).isCompletedBy(current(7, "Data Scientist", "T2")))
    }

    @Test
    fun `the old path still being current is not success`() {
        // Data Analyst was current; Data Scientist was requested. Until the request commits,
        // /current keeps answering with Data Analyst -- a path exists, and it is not the result.
        val waiting = pending(before = CurrentPathBefore.Path(id = 1, updatedAt = "T1"))
        assertFalse(waiting.isCompletedBy(current(1, "Data Analyst", "T1")))
    }

    @Test
    fun `a different role becoming current is not success`() {
        val waiting = pending(before = CurrentPathBefore.Path(id = 1, updatedAt = "T1"))
        assertFalse(waiting.isCompletedBy(current(9, "Software Engineer", "T3")))
    }

    @Test
    fun `the requested role on a new row is success`() {
        val waiting = pending(before = CurrentPathBefore.Path(id = 1, updatedAt = "T1"))
        assertTrue(waiting.isCompletedBy(current(2, "Data Scientist", "T2")))
    }

    @Test
    fun `re-selecting the current role is success only once updated_at moves`() {
        // The one case where role and id cannot tell the untouched row from the finished request.
        val waiting = pending(role = "Data Analyst", before = CurrentPathBefore.Path(id = 1, updatedAt = "T1"))
        assertFalse(waiting.isCompletedBy(current(1, "Data Analyst", "T1")))
        assertTrue(waiting.isCompletedBy(current(1, "Data Analyst", "T2")))
    }

    @Test
    fun `switching back to a role explored earlier is success on its existing row`() {
        // select_role() finds that role's row (an older id) and makes it primary.
        val waiting = pending(role = "Data Analyst", before = CurrentPathBefore.Path(id = 5, updatedAt = "T5"))
        assertTrue(waiting.isCompletedBy(current(1, "Data Analyst", "T6")))
    }

    @Test
    fun `a path that is not primary is never success`() {
        assertFalse(pending(before = CurrentPathBefore.None).isCompletedBy(current(7, "Data Scientist", "T2", isPrimary = false)))
    }

    @Test
    fun `a path created without a custom role is not this app's request`() {
        // This app always sends custom_role; a role_id selection leaves it null.
        assertFalse(pending(before = CurrentPathBefore.None).isCompletedBy(current(7, null, "T2")))
    }

    @Test
    fun `surrounding whitespace is compared the way the server stores it`() {
        // SelectCareerRoleRequest strips custom_role before it is stored.
        assertTrue(pending(role = " Data Scientist ", before = CurrentPathBefore.None).isCompletedBy(current(7, "Data Scientist", "T2")))
    }

    @Test
    fun `an unknown prior state falls back to the requested role alone`() {
        val waiting = pending(before = CurrentPathBefore.Unknown)
        assertTrue(waiting.isCompletedBy(current(3, "Data Scientist", "T1")))
        assertFalse(waiting.isCompletedBy(current(3, "Data Analyst", "T1")))
    }
}
