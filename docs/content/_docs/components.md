---
title: Components
---

# FFWD Components

This document covers some of the major components in FastForward.

## Module

A [module](https://github.com/spotify/ffwd/blob/master/api/src/main/java/com/spotify/ffwd/module/FastForwardModule.java) is a dynamically loaded component that extends the functionality of FastForward.
When a module is [loaded](https://github.com/spotify/ffwd/blob/17de95af58f221ad655dce18515835b2818c7241/core/src/main/java/com/spotify/ffwd/AgentCore.java#L400) it typically [registers a plugin](https://github.com/spotify/ffwd/blob/17de95af58f221ad655dce18515835b2818c7241/modules/pubsub/src/main/java/com/spotify/ffwd/pubsub/PubsubOutputModule.java#L36).

## Plugin

Either an [input](https://github.com/spotify/ffwd/blob/master/api/src/main/java/com/spotify/ffwd/input/PluginSource.java) or an [output](https://github.com/spotify/ffwd/blob/master/api/src/main/java/com/spotify/ffwd/output/PluginSink.java) plugin.

This provides an implementation which either ingests or emits data from the agent.

A plugin can have multiple _instances_ with different configurations (like which port to listen to).

## Early Injector

The [early injector](https://github.com/spotify/ffwd/blob/17de95af58f221ad655dce18515835b2818c7241/core/src/main/java/com/spotify/ffwd/AgentCore.java#L212) is setup by AgentCore and is intended to provide the basic facilities to perform module setup.

The following is a list of components that are given access to and their
purpose.

* [_com.spotify.ffwd.module.PluginContext_](https://github.com/spotify/ffwd/blob/master/api/src/main/java/com/spotify/ffwd/module/PluginContext.java)
  Register input and output plugins.
* _com.fasterxml.jackson.databind.ObjectMapper (config)_
  ObjectMapper used to parse provided configuration file.

## Primary Injector

The [primary injector](https://github.com/spotify/ffwd/blob/17de95af58f221ad655dce18515835b2818c7241/core/src/main/java/com/spotify/ffwd/AgentCore.java#L100) contains the dependencies which are available after modules have been registered and the initial bootstrap is done.

It contains all the components of the early injector, with the following
additions.

* _eu.toolchain.async.AsyncFramework_ - Framework implementation to use for async operations.
* _io.netty.channel.EventLoopGroup (boss)_ Event loop group used for boss
  threads in ServerBootstrap's.
* _io.netty.channel.EventLoopGroup (worker)_ Event loop group used for
  worker threads in {Server}Bootstrap's.
* _com.spotify.ffwd.protocol.ProtocolServers_ - Framework for setting up
  servers in a simple manner.
* _com.spotify.ffwd.protocol.ProtocolClients_ - Framework for setting up
  clients in a simple manner.
* _io.netty.util.Timer_ - A timer implementation.
* _com.fasterxml.jackson.databind.ObjectMapper (application/json)_
  Used to decode/encode JSON.
