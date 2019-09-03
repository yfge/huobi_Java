package com.huobi.client.v2.impl;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import com.huobi.client.v2.HuobiWebSocketOptions;
import com.huobi.client.v2.impl.HuobiWebSocketConnection.ConnectionState;

@Slf4j
class HuobiWebSocketWatchDog {

  private final CopyOnWriteArrayList<HuobiWebSocketConnection> TIME_HELPER = new CopyOnWriteArrayList<>();
  private final HuobiWebSocketOptions options;

  HuobiWebSocketWatchDog(HuobiWebSocketOptions subscriptionOptions) {
    this.options = Objects.requireNonNull(subscriptionOptions);
    long t = 1_000;
    ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);
    exec.scheduleAtFixedRate(() -> {
      TIME_HELPER.forEach(connection -> {
        if (connection.getState() == ConnectionState.CONNECTED) {
          // Check response
          if (options.isAutoReconnect()) {
            long ts = System.currentTimeMillis() - connection.getLastReceivedTime();
            if (ts > options.getReceiveLimitMs()) {
              log.warn("[HuobiWebSocketWatchDog] No response from server");
              connection.reConnect(options.getConnectionDelayOnFailure());
            }
          }
        } else if (connection.getState() == ConnectionState.DELAY_CONNECT) {
          connection.reConnect();
        } else if (connection.getState() == ConnectionState.CLOSED_ON_ERROR) {
          if (options.isAutoReconnect()) {
            connection.reConnect(options.getConnectionDelayOnFailure());
          }
        }
      });
    }, t, t, TimeUnit.MILLISECONDS);
    Runtime.getRuntime().addShutdownHook(new Thread(exec::shutdown));
  }

  void onConnectionCreated(HuobiWebSocketConnection connection) {
    TIME_HELPER.addIfAbsent(connection);
  }

  void onClosedNormally(HuobiWebSocketConnection connection) {
    TIME_HELPER.remove(connection);
  }
}