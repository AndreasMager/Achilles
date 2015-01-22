/*
 * Copyright (C) 2012-2014 DuyHai DOAN
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package info.archinnov.achilles.test.integration.utils;

import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.split;
import static org.fest.assertions.api.Assertions.assertThat;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import com.google.common.base.Joiner;
import info.archinnov.achilles.type.ConsistencyLevel;
import org.junit.ComparisonFailure;
import org.slf4j.LoggerFactory;

public class CassandraLogAsserter {
    private static final String DRIVER_CONNECTION_LOGGER = "com.datastax.driver.core.Connection";
    private Logger logger = (Logger)LoggerFactory.getLogger(DRIVER_CONNECTION_LOGGER);
    private OutputStreamAppender<ILoggingEvent> writerAppender;
    protected ByteArrayOutputStream logStream;
    private Pattern pattern = Pattern.compile("writing request [A-Z]+");

    public void prepareLogLevelForDriverConnection() {
       prepareLogLevel(DRIVER_CONNECTION_LOGGER);
    }

    public void prepareLogLevel(String loggerName) {
        logger = (Logger)LoggerFactory.getLogger(loggerName);
        final LoggerContext loggerContext = logger.getLoggerContext();
        PatternLayoutEncoder ple = createPatternLayoutEncoder(loggerContext);
        writerAppender = new OutputStreamAppender();
        writerAppender.setContext(loggerContext);
        writerAppender.setEncoder(ple);
        logStream = new ByteArrayOutputStream();
        writerAppender.setOutputStream(logStream);
        writerAppender.start();

        logger.addAppender(writerAppender);
        logger.setLevel(Level.TRACE);
        logger.setAdditive(false);
    }

    public void assertContains(String text) {
        asssertPatternToBe(text,true);
    }

    public void assertNotContains(String text) {
        asssertPatternToBe(text,false);
    }

    private void asssertPatternToBe(String text, boolean present) {
        final Iterator<String> standardOutputs = asList(split(logStream.toString(), "\n")).iterator();
        try {
            boolean textFound = false;
            while (standardOutputs.hasNext()) {
                final String logLine = standardOutputs.next();
                if (logLine.contains(text)) {
                    textFound = true;
                    break;
                }
            }
            assertThat(textFound).describedAs("Expected '" + text + "' to be found in the logs").isEqualTo(present);

        } finally {
            logStream = null;
            logger.setLevel(Level.WARN);
            logger.detachAppender(writerAppender);
        }
    }

    public void assertConsistencyLevels(ConsistencyLevel... consistencyLevels) {
        final List<ConsistencyLevel> expectedConsistencyLevels = asList(consistencyLevels);
        final Iterator<ConsistencyLevel> clIterator = expectedConsistencyLevels.iterator();
        final Iterator<String> standardOutputs = asList(split(logStream.toString(), "\n")).iterator();

        List<ConsistencyLevel> founds = new LinkedList<>();
        List<String> logs = new LinkedList<>();
        logs.add("\n");
        ConsistencyLevel consistencyLevel = clIterator.next();

        try {
            while (standardOutputs.hasNext()) {
                final String logLine = standardOutputs.next();
                if (pattern.matcher(logLine).find()) {
                    logs.add(logLine);
                    if(checkForConsistency(consistencyLevel, logLine)) {
                        founds.add(consistencyLevel);
                        if (clIterator.hasNext()) {
                            consistencyLevel = clIterator.next();
                        } else {
                            break;
                        }
                    }
                }
            }

            final String joinedLogs = Joiner.on("\n").join(logs);
            assertThat(founds).describedAs(joinedLogs + " expected consistency levels").isEqualTo(expectedConsistencyLevels);

        } finally {
            logStream = null;
            logger.setLevel(Level.WARN);
            logger.detachAppender(writerAppender);
        }
    }

    public void assertSerialConsistencyLevels(ConsistencyLevel serialConsistencyLevel, ConsistencyLevel... consistencyLevels) {
        final List<ConsistencyLevel> expectedConsistencyLevels = consistencyLevels != null ? asList(consistencyLevels) : Arrays.<ConsistencyLevel>asList();
        final Iterator<ConsistencyLevel> clIterator = expectedConsistencyLevels.iterator();
        final Iterator<String> standardOutputs = asList(split(logStream.toString(), "\n")).iterator();

        try {

            if (expectedConsistencyLevels.isEmpty()) {
                boolean foundSerialCL = false;
                while (standardOutputs.hasNext()) {
                    final String logLine = standardOutputs.next();

                    if (pattern.matcher(logLine).find() && logLine.contains("serialCl=" + serialConsistencyLevel.name())) {
                        foundSerialCL |= true;
                    }
                }

                if (!foundSerialCL) {
                    throw new ComparisonFailure("Cannot find serialConsistencyLevel", serialConsistencyLevel.name(), "nothing found");
                }
            } else {

                List<ConsistencyLevel> founds = new LinkedList<>();
                ConsistencyLevel consistencyLevel = clIterator.next();
                boolean foundSerialCL = false;

                while (standardOutputs.hasNext()) {
                    final String logLine = standardOutputs.next();

                    if (pattern.matcher(logLine).find() && logLine.contains("cl=" + consistencyLevel.name())) {
                        founds.add(consistencyLevel);

                        if (logLine.contains("serialCl=" + serialConsistencyLevel.name())) {
                            foundSerialCL |= true;
                        }

                        if (clIterator.hasNext()) {
                            consistencyLevel = clIterator.next();
                        } else {
                            break;
                        }

                    }
                }

                assertThat(foundSerialCL).describedAs("serialConsistencyLevel " + serialConsistencyLevel.name() + " has been found").isTrue();
                assertThat(founds).describedAs("expected consistency levels").isEqualTo(expectedConsistencyLevels);
            }


        } finally {
            logStream = null;
            logger.setLevel(Level.WARN);
            logger.detachAppender(writerAppender);
        }
    }

    protected boolean checkForConsistency(ConsistencyLevel consistencyLevel, String logLine) {
        return logLine.contains("cl=" + consistencyLevel.name()) || logLine.contains("at consistency "+consistencyLevel.name());
    }

    private PatternLayoutEncoder createPatternLayoutEncoder(LoggerContext loggerContext) {
        PatternLayoutEncoder ple = new PatternLayoutEncoder();
        ple.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        ple.setContext(loggerContext);
        ple.start();
        return ple;
    }
}
