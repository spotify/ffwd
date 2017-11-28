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
package com.spotify.ffwd.http;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

@Slf4j
@Data
public class SrvServerList implements ServerList<Server> {
    private final String record;

    @Override
    public List<Server> getInitialListOfServers() {
        return lookup();
    }

    @Override
    public List<Server> getUpdatedListOfServers() {
        return lookup();
    }

    private List<Server> lookup() {
        final Lookup lookup;
        try {
            lookup = new Lookup(record, Type.SRV, DClass.IN);
        } catch (TextParseException e) {
            throw new RuntimeException(e);
        }

        final Record[] result = lookup.run();

        if (lookup.getResult() != Lookup.SUCCESSFUL) {
            throw new RuntimeException(
                "DNS lookup failed: " + lookup.getErrorString() + ": " + record);
        }

        final List<Server> results = new ArrayList<>();

        if (result != null) {
            for (final Record a : result) {
                final SRVRecord srv = (SRVRecord) a;
                results.add(new Server(srv.getTarget().canonicalize().toString(), srv.getPort()));
            }
        }

        return Collections.unmodifiableList(results);
    }
}
