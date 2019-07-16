/*-
 * -\-\-
 * FastForward Riemann Module
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

package com.spotify.ffwd.riemann;

import com.google.inject.Inject;
import com.spotify.ffwd.module.FastForwardModule;
import com.spotify.ffwd.module.PluginContext;

public class RiemannModule implements FastForwardModule {
    @Inject
    private PluginContext context;

    @Override
    public void setup() throws Exception {
        context.registerInput("riemann", RiemannInputPlugin.class);
    }
}
