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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;


/**
 * Class that produces a set of tags from the grafana.dashboard.data DB column.
 * @see GrafanaDashboardReader
 */
public class GrafanaDashboardWatcher {


    public static final String APP_NAME = "grafana-dashboard-watcher";
    private static Connection conn;

    private static boolean connectToGrafanaDb() {
        try {
            conn = DriverManager.getConnection("http://someurl");
            return true;
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            // do something sensible
            return false;
        }
    }

    private static Date getLastRunTimestamp() {
        try {
            conn.createStatement().executeQuery(
                    "CREATE TABLE IF NOT EXISTS grafana_dashboard_watcher(last_run TIMESTAMP)");
            ResultSet lastRun = conn.createStatement().executeQuery(
                    "SELECT MAX(last_run) FROM grafana_dashboard_watcher");

            // TODO extract timestamp from result set
            return new Date();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            // do something sensible
            return null;
        }
    }

    // TODO decide whether we instead just always read all the dashboards. That
    // would lead a a much simpler implementation - always just writing out all
    // the role-what tag pairs found.
    private static Set<String> buildTagStrings() {
        Date lastRun = getLastRunTimestamp();
        try {
            ResultSet data = conn.createStatement().executeQuery(
                    "SELECT data FROM dashboard d " +
                    "WHERE d.updated < " + lastRun + " OR d.created < " + lastRun);

            // TODO extract the `what` and `role` from each dashboard.data cell and compile
            // into Set
            return new HashSet<>();

        } catch (SQLException throwables) {
            throwables.printStackTrace();
            // do something sensible
            return null;
        }
    }
}
