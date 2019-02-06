/*
 * Copyright 2017 National Library of Norway.
 *
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
 */
package no.nb.nna.veidemann.commons.settings;

import no.nb.nna.veidemann.api.ConfigProto.LogLevels;
import no.nb.nna.veidemann.api.ConfigProto.LogLevels.LogLevel;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationListener;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.Reconfigurable;
import org.apache.logging.log4j.core.config.plugins.util.PluginType;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A log4j configuration implementation which reads an ordinary xml configuration and then adds loggers from a config
 * server.
 */
public class XmlPlusConfigServerOverridesConfiguration extends XmlConfiguration {

    private ScheduledFuture<?> future;

    int intervalSeconds;

    private LogLevels logLevels;

    public XmlPlusConfigServerOverridesConfiguration(final LoggerContext loggerContext,
            final ConfigurationSource configSource) {
        super(loggerContext, configSource);

        try {
            ConfigServer configServer = ConfigServerFactory.getConfigServer();
            intervalSeconds = configServer.getConfigReloadInterval();

            logLevels = configServer.getLogLevels();
        } catch (Exception ex) {
            LOGGER.error("Could not read config overrides from config server", ex);
        }
    }

    @Override
    public void setup() {
        super.setup();
        Node loggers = getLoggersNode();
        logLevels.getLogLevelList().stream().forEach(l -> processLogger(loggers, l));
    }

    Node getLoggersNode() {
        PluginType loggersType = pluginManager.getPluginType("loggers");
        for (Node n : getRootNode().getChildren()) {
            if (n.getType().equals(loggersType)) {
                return n;
            }
        }
        Node loggers = new Node(getRootNode(), "Loggers", loggersType);
        getRootNode().getChildren().add(loggers);
        return loggers;
    }

    void processLogger(Node loggers, LogLevel logger) {
        for (Node n : loggers.getChildren()) {
            if (n.getAttributes().containsKey("name") && logger.getLogger().equals(n.getAttributes().get("name"))) {
                n.getAttributes().put("level", logger.getLevel().name());
                return;
            }
        }

        // No node found, creating new
        PluginType loggerType = pluginManager.getPluginType("logger");
        PluginType appenderRefType = pluginManager.getPluginType("appenderref");
        Node l = new Node(loggers, "Logger", loggerType);
        l.getAttributes().put("name", logger.getLogger());
        l.getAttributes().put("level", logger.getLevel().name());
        l.getAttributes().put("additivity", "false");

        Node a = new Node(l, logger.getLogger(), appenderRefType);
        a.getAttributes().put("ref", "Console");

        l.getChildren().add(a);
        loggers.getChildren().add(l);
    }

    @Override
    public Configuration reconfigure() {
        try {
            final ConfigurationSource source = getConfigurationSource().resetInputStream();
            if (source == null) {
                return null;
            }
            LOGGER.info("Reloading configuration");
            final XmlPlusConfigServerOverridesConfiguration config
                    = new XmlPlusConfigServerOverridesConfiguration(getLoggerContext(), source);
            return config;
        } catch (final IOException ex) {
            LOGGER.error("Cannot locate file {}", getConfigurationSource(), ex);
        }
        return null;
    }

    @Override
    public boolean stop(long timeout, TimeUnit timeUnit) {
        stop(future);
        super.stop(timeout, timeUnit);
        getScheduler().decrementScheduledItems();
        return true;
    }

    @Override
    public void start() {
        getScheduler().incrementScheduledItems();

        super.start();

        if (intervalSeconds > 0) {
            try {
                future = getScheduler()
                        .scheduleWithFixedDelay(new WatchRunnable(), intervalSeconds, intervalSeconds,
                                TimeUnit.SECONDS);
            } catch (Exception ex) {
                LOGGER.error(ex.getMessage(), ex);
            }
        }
    }

    private class WatchRunnable implements Runnable {

        @Override
        public void run() {
            for (final ConfigurationListener configurationListener : listeners) {
                LoggerContext.getContext(false)
                        .submitDaemon(new ReconfigurationRunnable(configurationListener,
                                XmlPlusConfigServerOverridesConfiguration.this));
            }
        }

    }

    /**
     * Helper class for triggering a reconfiguration in a background thread.
     */
    private static class ReconfigurationRunnable implements Runnable {

        private final ConfigurationListener configurationListener;

        private final Reconfigurable reconfigurable;

        public ReconfigurationRunnable(final ConfigurationListener configurationListener,
                final Reconfigurable reconfigurable) {
            this.configurationListener = configurationListener;
            this.reconfigurable = reconfigurable;
        }

        @Override
        public void run() {
            configurationListener.onChange(reconfigurable);
        }

    }
}
