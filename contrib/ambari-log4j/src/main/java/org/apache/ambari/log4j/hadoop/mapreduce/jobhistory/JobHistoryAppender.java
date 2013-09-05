/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ambari.log4j.hadoop.mapreduce.jobhistory;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.ambari.log4j.common.LogParser;
import org.apache.ambari.log4j.common.LogStore;
import org.apache.ambari.log4j.common.LoggingThreadRunnable;
import org.apache.ambari.log4j.common.store.DatabaseStore;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.tools.rumen.HistoryEvent;
import org.apache.hadoop.util.StringUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

public class JobHistoryAppender extends AppenderSkeleton implements Appender {

  private static final Log LOG = LogFactory.getLog(JobHistoryAppender.class);

  public static final int QUEUE_CAPACITY = 1024;
  
  private final Queue<LoggingEvent> events;
  private LoggingThreadRunnable logThreadRunnable;
  private Thread logThread;
  private static long WAIT_EMPTY_QUEUE = 2000;

  private final LogParser logParser;

  private final LogStore nullStore =
      new LogStore() {
        @Override
        public void persist(LoggingEvent originalEvent, Object parsedEvent) 
            throws IOException {
          LOG.info(((HistoryEvent)parsedEvent).toString());
        }

        @Override
        public void close() throws IOException {}
  };

  private String driver;
  private String database;
  private String user;
  private String password;
  
  private LogStore logStore;
  
  public JobHistoryAppender() {
    events = new LinkedBlockingQueue<LoggingEvent>(QUEUE_CAPACITY);
    logParser = new MapReduceJobHistoryParser();
    logStore = nullStore;
  }
  
  /* Getters & Setters for log4j */
  
  public String getDatabase() {
    return database;
  }

  public void setDatabase(String database) {
    this.database = database;
  }
  
  public String getDriver() {
    return driver;
  }

  public void setDriver(String driver) {
    this.driver = driver;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  /* --------------------------- */

  @Override
  public void activateOptions() {
    synchronized (this) {
      //if (true) { 
      if (database.equals("none")) {
        logStore = nullStore;
        LOG.info("database set to 'none'");
      } else {
        try {
          logStore = 
              new DatabaseStore(driver, database, user, password, 
                  new MapReduceJobHistoryUpdater());
          logThreadRunnable =
                  new LoggingThreadRunnable(events, logParser, logStore);
          logThread = new Thread(logThreadRunnable);
          logThread.setDaemon(true);
          logThread.start();
        } catch (IOException ioe) {
          LOG.warn("Failed to connect to db " + database, ioe);
          System.err.println("Failed to connect to db " + database + 
              " as user " + user + " password " + password + 
              " and driver " + driver + " with " + 
              StringUtils.stringifyException(ioe));
        } catch (Exception e) {
          LOG.warn("Failed to connect to db " + database +
                  " as user " + user + " password " + password +
                  " and driver " + driver, e);
          System.err.println("Failed to connect to db " + database + 
              " as user " + user + " password " + password + 
              " and driver " + driver + " with " + 
              StringUtils.stringifyException(e));
        }
      }
      super.activateOptions();
    }
  }

  @Override
  public void close() {
    try {
      logThreadRunnable.close();
    } catch (IOException ioe) {
      LOG.info("Failed to close logThreadRunnable", ioe);
    } catch (Exception e) {
      LOG.info("Failed to close logThreadRunnable", e);
    }
    try {
      logThread.join();
    } catch (InterruptedException ie) {
      LOG.info("logThread interrupted", ie);
    } catch (Exception e) {
      LOG.info("logThread interrupted", e);
    }
  }

  @Override
  public boolean requiresLayout() {
    return false;
  }

  @Override
  public void append(LoggingEvent event) {
    if (events.size() >= QUEUE_CAPACITY - 20) {
      try {
        Thread.sleep(WAIT_EMPTY_QUEUE);
      } catch (InterruptedException e) {
      }
    }
    if (!events.offer(event)) {
      //signal fail queue is full, there is a chance database is down
      LOG.warn("workflow event queue full, there is a chance database is down");
      System.exit(1);
    }
  }

  public Queue<LoggingEvent> getEvents() {
    return events;
  }
}
