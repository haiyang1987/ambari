/*
 * Copyright (c) 2010-2012 meituan.com
 * All rights reserved.
 * 
 */
package org.apache.ambari.log4j.hadoop.mapreduce.jobhistory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Category;
import org.apache.log4j.spi.LoggingEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

/**
 *
 * @author chenchun
 * @version 1.0
 * @created 2013-09-04
 */
public class Main {

  private static final Log LOG = LogFactory.getLog(Main.class);

  static final char JOB_HISTORY_LINE_DELIMITER_CHAR = '.';
  static final String LOG_FLAG = " DEBUG org.apache.hadoop.mapred.JobHistory$JobHistoryLogger: ";
  static int LOG_PREFIX_LENGTH = "2013-09-03 23:59:43,795".length() + LOG_FLAG.length();

  public static void main(String[] args) throws IOException, NoSuchMethodException,
          IllegalAccessException, InvocationTargetException, InstantiationException {
    if (args.length < 2) {
      LOG.error("must specify a jobtrackcer log file name and a config file name");
      System.exit(1);
    }
    String fileName = args[0], configFileName = args[1];
    File f = new File(fileName), configFile = new File(configFileName);
    if (!f.exists()) {
      LOG.error("file " + fileName +" not exist. ");
      System.exit(1);
    }
    if (!configFile.exists()) {
      LOG.error("file " + configFileName +" not exist. ");
      System.exit(1);
    }
    Properties config = loadProperties(configFileName);
    if (config == null) {
      LOG.error("can't load properties from " + configFileName +" .");
      System.exit(1);
    }
    long startTime = System.currentTimeMillis();
    String database = config.getProperty("database.url");
    String driver = config.getProperty("database.driverClassName");
    String user = config.getProperty("database.username");
    String password = config.getProperty("database.password");
    FileReader fr = new FileReader(f);
    BufferedReader br = new BufferedReader(fr);
    String log;
    JobHistoryAppender appender = new JobHistoryAppender();
    appender.setDatabase(database);
    appender.setDriver(driver);
    appender.setUser(user);
    appender.setPassword(password);
    appender.activateOptions();
    Constructor<?> constructor = Category.class.getDeclaredConstructor(String.class);
    constructor.setAccessible(true);
    Category c = (Category) constructor.newInstance("test");
    LOG.info("begin parse jobtracker log :");
    LOG.info("logFile=" + fileName);
    LOG.info("configFile=" + configFileName);
    LOG.info("database=" + database);
    LOG.info("driver=" + driver);
    LOG.info("user=" + user);
    LOG.info("password=" + password);
    StringBuilder sb = new StringBuilder();
    int i = 0;
    while ((log = br.readLine()) != null) {
      i++;
      if (i % 1000 == 0) {
        LOG.info("parse line " + i);
      }
      if (log.contains(LOG_FLAG)) {
        log = log.substring(LOG_PREFIX_LENGTH);
        if (log.charAt(log.length() - 1) == JOB_HISTORY_LINE_DELIMITER_CHAR) {
          LoggingEvent loggingEvent = newLoggingEvent(c, log);
          appender.append(loggingEvent);
        } else {
          sb.append(log);
        }
      } else {
        if (sb.length() != 0) {
          sb.append(log);
          if (log.length() > 1 && log.charAt(log.length() - 1) == JOB_HISTORY_LINE_DELIMITER_CHAR) {
            LoggingEvent loggingEvent = newLoggingEvent(c, sb.toString());
            appender.append(loggingEvent);
            sb = new StringBuilder();
          }
        }
      }
    }
    appender.close();
    LOG.info("end parse jobtracker time use = " + (System.currentTimeMillis() - startTime) / 1000 + " s");
    System.exit(0);
  }


  private static LoggingEvent newLoggingEvent(Category c, String message)
          throws IllegalAccessException, InvocationTargetException, InstantiationException {
    return new LoggingEvent(null, c, null, message, null);
  }

  /**
   * 加载配置项
   *
   * @param file
   * @return
   */
  public static Properties loadProperties(String file) {
    try {
      Properties properties = new Properties();
      InputStream stream = new FileInputStream(file);
      properties.load(stream);
      return properties;
    } catch (IOException e) {
      LOG.error(e.getMessage(), e);
    }
    return null;
  }

}
