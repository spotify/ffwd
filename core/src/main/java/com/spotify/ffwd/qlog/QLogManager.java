/*-
 * -\-\-
 * FastForward Core
 * --
 * Copyright (C) 2016 - 2018 Spotify AB
 * --
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
 * -/-/-
 */

package com.spotify.ffwd.qlog;

import eu.toolchain.async.AsyncFuture;
import java.io.IOException;
import java.nio.ByteBuffer;

public interface QLogManager {
    public long position();

    /**
     * Trim the head of the on-disk log (if necessary).
     *
     * @param position The position to trim to.
     */
    public void trim(long position);

    public void trim();

    public long write(ByteBuffer buffer) throws IOException;

    public void update(String id, long position);

    public AsyncFuture<Void> start();

    public AsyncFuture<Void> stop();
}
