/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.platform.test.flag.junit;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@code DeviceFlagsValueProvider}. */
@RunWith(JUnit4.class)
public final class DeviceFlagsValueProviderTest {
    private final DeviceFlagsValueProvider mFlagsValueProvider = new DeviceFlagsValueProvider();

    @Test
    public void getBoolean_invalidFormat_throwException() {
        assertThrows(
                FlagReadException.class, () -> mFlagsValueProvider.getBoolean("invalid_flag_name"));
    }

    @Test
    public void getBoolean_classNotFound_throwException() {
        assertThrows(
                FlagReadException.class,
                () -> mFlagsValueProvider.getBoolean("android.platform.test.flagName1"));
    }

    @Test
    public void getBoolean_noSuchMethod_throwException() {
        assertThrows(
                FlagReadException.class,
                () -> mFlagsValueProvider.getBoolean("android.platfrm.test.flag.junit.flag1"));
    }

    @Test
    public void getBoolean_validFlag() throws Exception {
        assertTrue(mFlagsValueProvider.getBoolean("android.platform.test.flag.junit.flag_name_1"));
    }

    @Test
    public void getBoolean_notBooleanFlag_throwException() {
        assertThrows(
                FlagReadException.class,
                () ->
                        mFlagsValueProvider.getBoolean(
                                "android.platform.test.flag.junit.flag_name_2"));
    }
}
