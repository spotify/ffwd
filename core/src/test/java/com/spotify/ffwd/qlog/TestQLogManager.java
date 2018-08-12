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

import eu.toolchain.async.AsyncFramework;
import eu.toolchain.async.TinyAsync;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Ignore;
import org.junit.Test;

public class TestQLogManager {
    @Test
    @Ignore
    public void testBasic() throws InterruptedException, ExecutionException, IOException {
        final ExecutorService executor = Executors.newFixedThreadPool(1);
        final AsyncFramework async = TinyAsync.builder().executor(executor).build();

        final QLogManager log = new QLogManagerImpl(Paths.get("./qlogtest"), async, 1024 * 10);

        log.start().get();

        final ByteBuffer buf = ByteBuffer.allocate(1024);

        while (buf.remaining() > 0) {
            buf.put((byte) 0x77);
        }

        buf.flip();

        final long position = log.position();

        log.update("foo", position - 500);

        for (int i = 0; i < 1000; i++) {
            log.write(buf.asReadOnlyBuffer());
        }

        log.trim();

        log.stop().get();

        executor.shutdown();
    }
}
