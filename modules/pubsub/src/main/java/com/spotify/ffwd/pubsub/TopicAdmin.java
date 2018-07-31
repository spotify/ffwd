/*
 * Copyright 2013-2018 Spotify AB. All rights reserved.
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
package com.spotify.ffwd.pubsub;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;


public class TopicAdmin {

  @Provides
  @Singleton
  public TopicAdminClient getClient() throws IOException {
    final TopicAdminSettings.Builder adminSetting = TopicAdminSettings.newBuilder();

    final String emulatorHost = System.getenv("PUBSUB_EMULATOR_HOST");
    if (emulatorHost != null) {
      ManagedChannel channel =
        ManagedChannelBuilder.forTarget(emulatorHost).usePlaintext().build();
      TransportChannelProvider channelProvider =
        FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));

      adminSetting.setTransportChannelProvider(channelProvider);
      adminSetting.setCredentialsProvider(NoCredentialsProvider.create());
    }

    return TopicAdminClient.create(adminSetting.build());
  }

}
