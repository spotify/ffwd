/*-
 * -\-\-
 * FastForward HTTP Module
 * --
 * Copyright (C) 2019 Spotify AB
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

package com.spotify.ffwd.http

import com.google.common.collect.ImmutableList
import com.netflix.loadbalancer.Server
import com.netflix.loadbalancer.ServerList
import org.xbill.DNS.*


data class SrvServerList(val record: String) : ServerList<Server> {
    override fun getUpdatedListOfServers(): List<Server> {
        return lookup()
    }

    override fun getInitialListOfServers(): List<Server> {
        return lookup()
    }

    private fun lookup(): List<Server> {
        val lookup: Lookup
        try {
            lookup = Lookup(record, Type.SRV, DClass.IN)
        } catch (e: TextParseException) {
            throw RuntimeException(e)
        }

        val result = lookup.run()

        if (lookup.result != Lookup.SUCCESSFUL) {
            throw RuntimeException(
                    "DNS lookup failed: " + lookup.errorString + ": " + record)
        }


        val results = ImmutableList.builder<Server>()

        if (result != null) {
            for (a in result) {
                val srv = a as SRVRecord
                results.add(Server(a.target.canonicalize().toString(), srv.port))
            }
        }

        return results.build()
    }
}
