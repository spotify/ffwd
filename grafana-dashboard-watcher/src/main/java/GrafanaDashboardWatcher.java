package com.example.storage;

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

public class GrafanaDashboardWatcher {
    public static final int NUM_UNIQUE_TAG_PAIRINGS = 10_000;
    private static String bucketName = "ffwd-grafana-dashboard-used-tag-pairs";
    private static Storage storage = StorageOptions.getDefaultInstance().getService();


    public GrafanaDashboardWatcher() {
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

    public static boolean uploadNewGrafanaTagFile(Set<String> tagPairs) {
        try {
            Iterator<String> itr = tagPairs.iterator();
            StringBuilder sb = new StringBuilder(NUM_UNIQUE_TAG_PAIRINGS);
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
}
