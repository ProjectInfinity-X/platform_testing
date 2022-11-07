/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wm.flicker.datastore

import android.annotation.SuppressLint
import com.android.server.wm.flicker.AssertionTag
import com.android.server.wm.flicker.RunStatus
import com.android.server.wm.flicker.TEST_SCENARIO
import com.android.server.wm.flicker.assertExceptionMessage
import com.android.server.wm.flicker.assertions.AssertionData
import com.android.server.wm.flicker.assertions.FlickerSubject
import com.android.server.wm.flicker.newTestResultWriter
import com.android.server.wm.flicker.traces.eventlog.EventLogSubject
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Test

/** Tests for [CachedAssertionRunner] */
@SuppressLint("VisibleForTests")
class CachedAssertionRunnerTest {
    private var executionCount = 0

    private val assertionSuccess = newAssertionData { executionCount++ }
    private val assertionFailure = newAssertionData {
        executionCount++
        error(com.android.server.wm.flicker.assertions.Consts.FAILURE)
    }

    @Before
    fun setup() {
        executionCount = 0
        DataStore.clear()
        val result = newTestResultWriter().addEventLogResult(emptyList()).write()
        DataStore.addResult(TEST_SCENARIO, result)
    }

    @Test
    fun executes() {
        val runner = CachedAssertionRunner(TEST_SCENARIO)
        val firstAssertionResult = runner.runAssertion(assertionSuccess)
        val lastAssertionResult = runner.runAssertion(assertionSuccess)
        val result = DataStore.getResult(TEST_SCENARIO)

        Truth.assertWithMessage("Executed").that(executionCount).isEqualTo(2)
        Truth.assertWithMessage("Run status")
            .that(result.runStatus)
            .isEqualTo(RunStatus.ASSERTION_SUCCESS)
        verifyExceptionMessage(firstAssertionResult, expectSuccess = true)
        verifyExceptionMessage(lastAssertionResult, expectSuccess = true)
    }

    @Test
    fun executesFailure() {
        val runner = CachedAssertionRunner(TEST_SCENARIO)
        val firstAssertionResult = runner.runAssertion(assertionFailure)
        val lastAssertionResult = runner.runAssertion(assertionFailure)
        val result = DataStore.getResult(TEST_SCENARIO)

        Truth.assertWithMessage("Executed").that(executionCount).isEqualTo(2)
        Truth.assertWithMessage("Run status")
            .that(result.runStatus)
            .isEqualTo(RunStatus.ASSERTION_FAILED)

        verifyExceptionMessage(firstAssertionResult, expectSuccess = false)
        verifyExceptionMessage(lastAssertionResult, expectSuccess = false)
        Truth.assertWithMessage("Same exception")
            .that(firstAssertionResult)
            .hasMessageThat()
            .isEqualTo(lastAssertionResult?.message)
    }

    @Test
    fun updatesRunStatusFailureFirst() {
        val runner = CachedAssertionRunner(TEST_SCENARIO)
        val firstAssertionResult = runner.runAssertion(assertionFailure)
        val lastAssertionResult = runner.runAssertion(assertionSuccess)
        val result = DataStore.getResult(TEST_SCENARIO)

        Truth.assertWithMessage("Executed").that(executionCount).isEqualTo(2)
        verifyExceptionMessage(firstAssertionResult, expectSuccess = false)
        verifyExceptionMessage(lastAssertionResult, expectSuccess = true)
        Truth.assertWithMessage("Run status")
            .that(result.runStatus)
            .isEqualTo(RunStatus.ASSERTION_FAILED)
    }

    @Test
    fun updatesRunStatusFailureLast() {
        val runner = CachedAssertionRunner(TEST_SCENARIO)
        val firstAssertionResult = runner.runAssertion(assertionSuccess)
        val lastAssertionResult = runner.runAssertion(assertionFailure)
        val result = DataStore.getResult(TEST_SCENARIO)

        Truth.assertWithMessage("Executed").that(executionCount).isEqualTo(2)
        verifyExceptionMessage(firstAssertionResult, expectSuccess = true)
        verifyExceptionMessage(lastAssertionResult, expectSuccess = false)
        Truth.assertWithMessage("Run status")
            .that(result.runStatus)
            .isEqualTo(RunStatus.ASSERTION_FAILED)
    }

    private fun verifyExceptionMessage(actual: Throwable?, expectSuccess: Boolean) {
        if (expectSuccess) {
            Truth.assertWithMessage("Expected exception").that(actual).isNull()
        } else {
            assertExceptionMessage(actual, com.android.server.wm.flicker.assertions.Consts.FAILURE)
        }
    }

    companion object {
        private fun newAssertionData(assertion: (FlickerSubject) -> Unit) =
            AssertionData.newTestInstance(AssertionTag.ALL, EventLogSubject::class, assertion)
    }
}
