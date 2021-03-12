/*-
 * -\-\-
 * FastForward Agent
 * --
 * Copyright (C) 2016 - 2019 Spotify AB
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

package com.spotify.metrics.dashboard.writer;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

/**
 * Class that takes a set of Strings and writes them to a preconfigured file
 * in a preconfigured GCP bucket.
 * @see GrafanaDashboardWatcher
 */
public class GrafanaDashboardWriter {
    public static final int NUM_UNIQUE_TAG_PAIRINGS_GUESSTIMATE = 10_000;
    private static String bucketName = "ffwd-grafana-dashboard-used-tag-pairs";
    private static Storage storage = StorageOptions.getDefaultInstance().getService();


    public GrafanaDashboardWriter() {
        try {
            createNewBucket();
        } catch (Exception err) {
            // it's already been created or something
        }
    }

    // this probably is not needed, it's just providing context
    private static void createNewBucket() {
        storage.create(BucketInfo.of(bucketName));
    }

    private static boolean uploadNewGrafanaTagFile(Set<String> tagPairs) {
        try {
            Iterator<String> itr = tagPairs.iterator();
            StringBuilder sb = new StringBuilder(NUM_UNIQUE_TAG_PAIRINGS_GUESSTIMATE);
            while (itr.hasNext()) {
                sb.append(itr);
            }

            long currentTimeMillis = System.currentTimeMillis();
            Date now = new Date(currentTimeMillis);
            String nowReadable = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss-z").format(now);

            BlobId blobId = BlobId.of(bucketName,
                    "dashboard-tags-" + nowReadable + "-" + currentTimeMillis + ".json");
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/json").build();
            storage.create(blobInfo, sb.toString().getBytes(StandardCharsets.UTF_8));

            return true;
        } catch (Exception err) {
            // do something sensible
            return false;
        }
    }

    // TODO. This could be obviated by using TTL on the object storage bucket/dir
    private static boolean removeOldGrafanaTagFiles() {
        return true;
    }
}
