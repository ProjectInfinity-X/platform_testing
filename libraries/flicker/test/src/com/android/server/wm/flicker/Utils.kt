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

package com.android.server.wm.flicker

import android.app.Instrumentation
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.datastore.CachedResultWriter
import com.android.server.wm.flicker.io.ResultReader
import com.android.server.wm.flicker.io.ResultWriter
import com.android.server.wm.flicker.io.WINSCOPE_EXT
import com.android.server.wm.flicker.monitor.EventLogMonitor
import com.android.server.wm.flicker.monitor.ITransitionMonitor
import com.android.server.wm.flicker.monitor.LayersTraceMonitor
import com.android.server.wm.flicker.monitor.ScreenRecorder
import com.android.server.wm.flicker.monitor.TransactionsTraceMonitor
import com.android.server.wm.flicker.monitor.TransitionsTraceMonitor
import com.android.server.wm.flicker.monitor.WindowManagerTraceMonitor
import com.android.server.wm.flicker.traces.FlickerSubjectException
import com.android.server.wm.traces.common.IScenario
import com.android.server.wm.traces.common.ScenarioBuilder
import com.android.server.wm.traces.common.Timestamp
import com.android.server.wm.traces.common.events.EventLog
import com.android.server.wm.traces.common.layers.LayersTrace
import com.android.server.wm.traces.common.parser.events.EventLogParser
import com.android.server.wm.traces.common.transactions.TransactionsTrace
import com.android.server.wm.traces.common.transition.TransitionsTrace
import com.android.server.wm.traces.common.windowmanager.WindowManagerTrace
import com.android.server.wm.traces.parser.layers.LayersTraceParser
import com.android.server.wm.traces.parser.transaction.TransactionsTraceParser
import com.android.server.wm.traces.parser.transition.TransitionsTraceParser
import com.android.server.wm.traces.parser.windowmanager.WindowManagerDumpParser
import com.android.server.wm.traces.parser.windowmanager.WindowManagerStateHelper
import com.android.server.wm.traces.parser.windowmanager.WindowManagerTraceParser
import com.google.common.io.ByteStreams
import com.google.common.truth.StringSubject
import com.google.common.truth.Truth
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import org.mockito.Mockito

internal val TEST_SCENARIO = ScenarioBuilder().forClass("test").build()

internal fun outputFileName(status: RunStatus) =
    File("/sdcard/flicker/${status.prefix}_test_ROTATION_0_GESTURAL_NAV.zip")

internal fun newTestResultWriter() =
    ResultWriter()
        .forScenario(TEST_SCENARIO)
        .withOutputDir(getDefaultFlickerOutputDir())
        .setRunComplete()

internal fun newTestCachedResultWriter() =
    CachedResultWriter()
        .forScenario(TEST_SCENARIO)
        .withOutputDir(getDefaultFlickerOutputDir())
        .setRunComplete()

internal fun readWmTraceFromFile(
    relativePath: String,
    from: Long = Long.MIN_VALUE,
    to: Long = Long.MAX_VALUE,
    addInitialEntry: Boolean = true,
    legacyTrace: Boolean = false,
): WindowManagerTrace {
    return try {
        WindowManagerTraceParser(legacyTrace)
            .parse(
                readAsset(relativePath),
                Timestamp(elapsedNanos = from),
                Timestamp(elapsedNanos = to),
                addInitialEntry,
                clearCache = false
            )
    } catch (e: Exception) {
        throw RuntimeException(e)
    }
}

internal fun readWmTraceFromDumpFile(relativePath: String): WindowManagerTrace {
    return try {
        WindowManagerDumpParser().parse(readAsset(relativePath), clearCache = false)
    } catch (e: Exception) {
        throw RuntimeException(e)
    }
}

internal fun readLayerTraceFromFile(
    relativePath: String,
    ignoreOrphanLayers: Boolean = true,
    legacyTrace: Boolean = false,
): LayersTrace {
    return try {
        LayersTraceParser(
                ignoreLayersStackMatchNoDisplay = false,
                ignoreLayersInVirtualDisplay = false,
                legacyTrace = legacyTrace,
            ) {
                ignoreOrphanLayers
            }
            .parse(readAsset(relativePath))
    } catch (e: Exception) {
        throw RuntimeException(e)
    }
}

internal fun readTransactionsTraceFromFile(relativePath: String): TransactionsTrace {
    return try {
        TransactionsTraceParser().parse(readAsset(relativePath))
    } catch (e: Exception) {
        throw RuntimeException(e)
    }
}

internal fun readTransitionsTraceFromFile(
    relativePath: String,
    transactionsTrace: TransactionsTrace
): TransitionsTrace {
    return try {
        TransitionsTraceParser().parse(readAsset(relativePath))
    } catch (e: Exception) {
        throw RuntimeException(e)
    }
}

internal fun readEventLogFromFile(relativePath: String): EventLog {
    return try {
        EventLogParser().parse(readAsset(relativePath))
    } catch (e: Exception) {
        throw RuntimeException(e)
    }
}

@Throws(Exception::class)
internal fun readAsset(relativePath: String): ByteArray {
    val context: Context = InstrumentationRegistry.getInstrumentation().context
    val inputStream = context.resources.assets.open("testdata/$relativePath")
    return ByteStreams.toByteArray(inputStream)
}

@Throws(IOException::class)
fun readAssetAsFile(relativePath: String): File {
    val context: Context = InstrumentationRegistry.getInstrumentation().context
    return File(context.cacheDir, relativePath).also {
        if (!it.exists()) {
            it.outputStream().use { cache ->
                context.assets.open("testdata/$relativePath").use { inputStream ->
                    inputStream.copyTo(cache)
                }
            }
        }
    }
}

/**
 * Runs `r` and asserts that an exception with type `expectedThrowable` is thrown.
 * @param r the [Runnable] which is run and expected to throw.
 * @throws AssertionError if `r` does not throw, or throws a runnable that is not an instance of
 * `expectedThrowable`.
 */
inline fun <reified ExceptionType> assertThrows(r: () -> Unit): ExceptionType {
    try {
        r()
    } catch (t: Throwable) {
        when {
            ExceptionType::class.java.isInstance(t) -> return t as ExceptionType
            t is Exception ->
                throw AssertionError(
                    "Expected ${ExceptionType::class.java}, but got '${t.javaClass}'",
                    t
                )
            // Re-throw Errors and other non-Exception throwables.
            else -> throw t
        }
    }
    error("Expected exception ${ExceptionType::class.java}, but nothing was thrown")
}

fun assertFailureFact(
    failure: FlickerSubjectException,
    factKey: String,
    factIndex: Int = 0
): StringSubject {
    val matchingFacts = failure.facts.filter { it.key == factKey }

    if (factIndex >= matchingFacts.size) {
        val message = buildString {
            appendLine("Cannot find failure fact with key '$factKey' and index $factIndex")
            appendLine()
            appendLine("Available facts:")
            failure.facts.forEach { appendLine(it.toString()) }
        }
        throw AssertionError(message)
    }

    return Truth.assertThat(matchingFacts[factIndex].value)
}

fun assertThatErrorContainsDebugInfo(error: Throwable, withBlameEntry: Boolean = true) {
    Truth.assertThat(error).hasMessageThat().contains("What?")
    Truth.assertThat(error).hasMessageThat().contains("Where?")
    Truth.assertThat(error).hasMessageThat().contains("Facts")
    Truth.assertThat(error).hasMessageThat().contains("Trace start")
    Truth.assertThat(error).hasMessageThat().contains("Trace end")

    if (withBlameEntry) {
        Truth.assertThat(error).hasMessageThat().contains("State")
    }
}

fun assertArchiveContainsFiles(archivePath: File, expectedFiles: List<String>) {
    Truth.assertWithMessage("Expected trace archive `$archivePath` to exist")
        .that(archivePath.exists())
        .isTrue()

    val archiveStream = ZipInputStream(FileInputStream(archivePath))

    val actualFiles = generateSequence { archiveStream.nextEntry }.map { it.name }.toList()

    Truth.assertWithMessage("Trace archive doesn't contain all expected traces")
        .that(actualFiles)
        .containsExactlyElementsIn(expectedFiles)
}

fun getScenarioTraces(scenario: String): FlickerBuilder.TraceFiles {
    val randomString = (1..10).map { (('A'..'Z') + ('a'..'z')).random() }.joinToString("")

    lateinit var wmTrace: File
    lateinit var layersTrace: File
    lateinit var transactionsTrace: File
    lateinit var transitionsTrace: File
    lateinit var eventLog: File
    val traces =
        mapOf<String, (File) -> Unit>(
            "wm_trace" to { wmTrace = it },
            "layers_trace" to { layersTrace = it },
            "transactions_trace" to { transactionsTrace = it },
            "transition_trace" to { transitionsTrace = it },
            "eventlog" to { eventLog = it }
        )
    for ((traceName, resultSetter) in traces.entries) {
        val traceBytes = readAsset("scenarios/$scenario/$traceName$WINSCOPE_EXT")
        val traceFile =
            getDefaultFlickerOutputDir().resolve("${traceName}_$randomString$WINSCOPE_EXT")
        traceFile.parentFile.mkdirs()
        traceFile.createNewFile()
        traceFile.writeBytes(traceBytes)
        resultSetter.invoke(traceFile)
    }

    return FlickerBuilder.TraceFiles(
        wmTrace,
        layersTrace,
        transactionsTrace,
        transitionsTrace,
        eventLog
    )
}

fun assertExceptionMessage(error: Throwable?, expectedValue: String) {
    Truth.assertWithMessage("Expected exception")
        .that(error)
        .hasMessageThat()
        .contains(expectedValue)
}

fun assertExceptionMessageCause(error: Throwable?, expectedValue: String) {
    Truth.assertWithMessage("Expected cause")
        .that(error)
        .hasCauseThat()
        .hasMessageThat()
        .contains(expectedValue)
}

fun createMockedFlicker(
    setup: List<IFlickerTestData.() -> Unit> = emptyList(),
    teardown: List<IFlickerTestData.() -> Unit> = emptyList(),
    transitions: List<IFlickerTestData.() -> Unit> = emptyList(),
    extraMonitor: ITransitionMonitor? = null
): IFlickerTestData {
    val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)
    val mockedFlicker = Mockito.mock(AbstractFlickerTestData::class.java)
    val monitors: MutableList<ITransitionMonitor> =
        mutableListOf(WindowManagerTraceMonitor(), LayersTraceMonitor())
    extraMonitor?.let { monitors.add(it) }
    Mockito.`when`(mockedFlicker.wmHelper).thenReturn(WindowManagerStateHelper())
    Mockito.`when`(mockedFlicker.device).thenReturn(uiDevice)
    Mockito.`when`(mockedFlicker.outputDir).thenReturn(getDefaultFlickerOutputDir())
    Mockito.`when`(mockedFlicker.traceMonitors).thenReturn(monitors)
    Mockito.`when`(mockedFlicker.transitionSetup).thenReturn(setup)
    Mockito.`when`(mockedFlicker.transitionTeardown).thenReturn(teardown)
    Mockito.`when`(mockedFlicker.transitions).thenReturn(transitions)
    return mockedFlicker
}

fun captureTrace(scenario: IScenario, actions: () -> Unit): ResultReader {
    if (scenario.isEmpty) {
        ScenarioBuilder().forClass("UNNAMED_CAPTURE").build()
    }
    val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    val writer =
        ResultWriter()
            .forScenario(scenario)
            .withOutputDir(getDefaultFlickerOutputDir())
            .setRunComplete()
    val monitors =
        listOf(
            ScreenRecorder(instrumentation.targetContext),
            EventLogMonitor(),
            TransactionsTraceMonitor(),
            TransitionsTraceMonitor(),
            WindowManagerTraceMonitor(),
            LayersTraceMonitor()
        )
    try {
        monitors.forEach { it.start() }
        actions.invoke()
    } finally {
        monitors.forEach { it.stop(writer) }
    }
    val result = writer.write()

    return ResultReader(result, DEFAULT_TRACE_CONFIG)
}
