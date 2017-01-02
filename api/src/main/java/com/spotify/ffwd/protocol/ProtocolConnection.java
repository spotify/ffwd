/*
 * Copyright 2013-2017 Spotify AB. All rights reserved.
 *
 * The contents of this file are licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.spotify.ffwd.protocol;

import eu.toolchain.async.AsyncFuture;

import java.util.Collection;

public interface ProtocolConnection {
    public void send(Object message);

    public AsyncFuture<Void> stop();

    public AsyncFuture<Void> sendAll(Collection<? extends Object> batch);

    public boolean isConnected();
}
