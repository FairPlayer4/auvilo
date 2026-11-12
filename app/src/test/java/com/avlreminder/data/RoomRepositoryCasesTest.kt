package com.avlreminder.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.avlreminder.core.at
import com.avlreminder.core.scenarios.RepositoryCases
import com.avlreminder.core.scenarios.Simulation
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * [RepositoryCases] on Room, the same cases as `FakeRepositoryCasesTest` in core: where the
 * fake stores differently from Room, one of the two fails (Review-Lauf 4, E14).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class RoomRepositoryCasesTest(private val name: String) {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AvlDatabase::class.java)
        .allowMainThreadQueries().build()

    @After
    fun close() = db.close()

    @Test
    fun case() = runTest {
        val case = RepositoryCases.all.single { it.first == name }.second
        case(Simulation(at(26, 6), RoomReminderRepository(db)))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun names() = RepositoryCases.all.map { arrayOf<Any>(it.first) }
    }
}
