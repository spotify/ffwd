---
title: Pubsub
---

# FastForward Pubsub

This modules provides a [Google Pubsub](https://cloud.google.com/pubsub/docs/overview) output plugin.


## Configuration

* `project` - the google project where the topic exists.
* `topic` - the google pubsub topic to publish to.


The default settings the publisher uses for batching requests can be overriden. The API allows publishing
based on request size, message count and time since last publish. The client by default automatically retries failed requests.

* `requestBytesThreshold` - number of bytes to wait for.
* `messageCountBatchSize` - number of messages to wait for.
* `publishDelayThresholdMs` - amount of type to wait for.


Amount of metrics allowed on input can be configured using `maxInputMetrics`.


Here is an example config of setting up the pubsub plugin.

```
output:
  maxInputMetrics: 50000 # Default: 500_000
  plugins:
    - type: pubsub
      project: google-test-project
      topic: test-topic
      serializer:
        type: spotify100proto
      
      batching:
        flushInterval: 10000
        batchSizeLimit: 10000  # Default: 10000
        maxPendingFlushes: 10 # Default: 10 
```


### Write Cache

There is an optional write cache that can be enabled. At Spotify this is being used as a "cheap" method
to deduplicate the metadata around timeseries when using the Heroic TSDB. The way this feature is used is by publishing from ffwd twice. 
Once for "metrics" that get written to bigtable every batch interval and another for metadata and suggestion 
writes to elasticsearch. Only the metadata publisher would enabled the write cache. This would only publish metadata every cacheDuration.

* `writeCacheDurationMinutes` - defaults to 0 minutes - which disables the write cache
* `writeCacheMaxSize` - defaults to 10,000


## Authentication

If running ffwd from within GCE the default application credentials can be used for authentication.

If you have multiple google projects and would rather distribute a service account to each instance, ensure the environment variable `GOOGLE_APPLICATION_CREDENTIALS` is set to the path of the JSON file.

To learn how to setup these credentials or generate a service account read https://cloud.google.com/docs/authentication/production.

## Pubsub Emulator

Instead of using real Google pubsub resources, the pubsub emulator can be used for local development.

Follow https://cloud.google.com/pubsub/docs/emulator to setup and start the emulator. 

Start ffwd after exporting an environment variable like below to point at the emulator.
`export PUBSUB_EMULATOR_HOST=localhost:8085` 

Note - no topics or subscriptions are created by default. You can use the python pubsub library like so...

TODO - Move this to a utility class in Java.


```python
from google.cloud import pubsub_v1

project = 'google-test-project'
p = pubsub_v1.PublisherClient()
tp = p.topic_path(project, 'test-topic')
p.create_topic(tp)

c = pubsub_v1.SubscriberClient()
sp = c.subscription_path(project, 'test-subscription')
c.create_subscription(sp, tp)

c.pull(sp, 1)

```
