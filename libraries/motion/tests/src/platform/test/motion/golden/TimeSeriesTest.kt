/*
 * Copyright (C) 2024 The Android Open Source Project
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

package platform.test.motion.golden

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.IllegalArgumentException
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimeSeriesTest {

    @Test
    fun moreDataPointsThanFrames_throws() {
        val frameIds = listOf(TimestampFrameId(0))
        val properties =
            listOf(Feature("foo", dataPoints = listOf(1.asDataPoint(), 2.asDataPoint())))

        assertThrows(IllegalArgumentException::class.java) { TimeSeries(frameIds, properties) }
    }

    @Test
    fun lessDataPointsThanFrames_throws() {
        val frameIds = listOf(TimestampFrameId(0), TimestampFrameId(1))
        val properties = listOf(Feature("foo", dataPoints = listOf(1.asDataPoint())))

        assertThrows(IllegalArgumentException::class.java) { TimeSeries(frameIds, properties) }
    }
}
