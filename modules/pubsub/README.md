# FastForward Pubsub

This modules provides a [Google Pubsub](https://cloud.google.com/pubsub/docs/overview) output plugin.


## Configuration

* `serviceAccount` - an optional path to a json service account to authenticate to Google.
See [authentication](#authentication) for more info.
* `project` - the google project where the topic exists.
* `topic` - the google pubsub topic to publish to.



The default settings the publisher uses for batching requests can be overriden. The API allows publishing
based on request size, message count and time since last publish. The client by default automatically retries failed requests.

* `requestBytesThreshold` - number of bytes to wait for.
* `messageCountBatchSize` - number of messages to wait for.
* `publishDelayThresholdMs` - amount of type to wait for.


Here is an example config of setting up the pubsub plugin.
```
output:
  plugins:
    - type: pubsub
      flushInterval: 10000
      project: google-test-project
      topic: test-topic
      #serviceAccount: "path to service account json"
```


## Authentication

If no `serviceAccount` is provided the Google Pubsub library will try to use the default credentials that are exported as part of the
`GOOGLE_APPLICATION_CREDENTIALS` environment variable.


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