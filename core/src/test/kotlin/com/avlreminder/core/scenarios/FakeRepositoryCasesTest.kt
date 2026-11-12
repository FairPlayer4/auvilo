package com.avlreminder.core.scenarios

import com.avlreminder.core.at
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** [RepositoryCases] on the fake; the same cases run on Room in app (`RoomRepositoryCasesTest`). */
class FakeRepositoryCasesTest {
    @TestFactory
    fun cases() = RepositoryCases.all.map { (name, case) ->
        DynamicTest.dynamicTest(name) { runTest { case(Simulation(at(26, 6))) } }
    }
}
