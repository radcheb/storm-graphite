/*
* Copyright 2014 VeriSign, Inc.
*
* VeriSign licenses this file to you under the Apache License, version 2.0 (the "License"); you may not
* use this file except in compliance with the License. You may obtain a copy of the License at:
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software distributed under the License is
* distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and limitations under the License.
*
* See the NOTICE file distributed with this work for additional information regarding copyright ownership.
*/
package com.verisign.storm.metrics;

import backtype.storm.metric.api.IMetricsConsumer.DataPoint;
import backtype.storm.metric.api.IMetricsConsumer.TaskInfo;
import backtype.storm.task.IErrorReporter;
import backtype.storm.task.TopologyContext;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.verisign.storm.metrics.reporters.GraphiteReporter;
import com.verisign.storm.metrics.reporters.KafkaReporter;
import org.mockito.Mockito;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class GraphiteMetricsConsumerTest {

  private final Long currentTime = System.currentTimeMillis();
  private final Random rng = new Random(currentTime);

  private String testStormComponentID = "testStormComponentID";
  private String testStormSrcWorkerHost = "testStormSrcWorkerHost";
  private Integer testStormSrcWorkerPort = 6700;
  private Integer testStormSrcTaskId = 3008;

  private String testTopologyName = "Example-Storm-Topology-Name-13-1425495763";
  private String testReporterName = "com.verisign.storm.metrics.reporters.GraphiteReporter";
  private String testGraphiteHost = "127.0.0.1";
  private String testGraphitePort = "2003";
  private String testPrefix = "unittest";

  private final GraphiteMetricsConsumer consumer = new GraphiteMetricsConsumer();
  private final TaskInfo taskInfo = new TaskInfo(testStormSrcWorkerHost, testStormSrcWorkerPort, testStormComponentID,
      testStormSrcTaskId, currentTime, 10);

  @Test public void shouldInitializeGraphiteReporter() {
    // Given a Graphite configuration and topology context
    Map<String, String> stormConfig = Maps.newHashMap();
    stormConfig.put("metrics.graphite.host", testGraphiteHost);
    stormConfig.put(GraphiteMetricsConsumer.REPORTER_NAME, testReporterName);


    Map<String, String> registrationArgument = Maps.newHashMap();
    registrationArgument.put("metrics.graphite.port", testGraphitePort);
    registrationArgument.put("metrics.graphite.prefix", testPrefix);

    TopologyContext topologyContext = mock(TopologyContext.class);
    when(topologyContext.getStormId()).thenReturn(testTopologyName);
    IErrorReporter errorReporter = mock(IErrorReporter.class);

    // When the metrics consumer initializes
    consumer.prepare(stormConfig, registrationArgument, topologyContext, errorReporter);

    // Then the reporter should be initialized with a Graphite reporter
    verify(topologyContext).getStormId();
    assertThat(consumer.getGraphitePrefix()).isEqualTo(testPrefix);
    assertThat(consumer.getStormId()).isEqualTo(testTopologyName);
    assertThat(consumer.adapter).isInstanceOf(GraphiteReporter.class);
    assertThat(consumer.adapter).isNotNull();
  }

  @Test public void shouldInitializeKafkaReporter() {
    // Given a Kafka configuration and topology context
    Map<String, String> stormConfig = Maps.newHashMap();
    stormConfig.put(KafkaReporter.KAFKA_BROKER_LIST_FIELD, "127.0.0.1:9092");
    stormConfig.put(GraphiteMetricsConsumer.REPORTER_NAME, "com.verisign.storm.metrics.reporters.KafkaReporter");

    Map<String, String> registrationArgument = Maps.newHashMap();
    registrationArgument.put(KafkaReporter.KAFKA_TOPIC_NAME_FIELD, "testTopic");
    registrationArgument.put("metrics.graphite.prefix", testPrefix);

    TopologyContext topologyContext = mock(TopologyContext.class);
    when(topologyContext.getStormId()).thenReturn(testTopologyName);
    IErrorReporter errorReporter = mock(IErrorReporter.class);

    // When the metrics consumer initializes
    consumer.prepare(stormConfig, registrationArgument, topologyContext, errorReporter);

    // Then the reporter should be initialized with a Kafka reporter
    verify(topologyContext).getStormId();
    assertThat(consumer.getGraphitePrefix()).isEqualTo(testPrefix);
    assertThat(consumer.getStormId()).isEqualTo(testTopologyName);
    assertThat(consumer.adapter).isInstanceOf(KafkaReporter.class);
    assertThat(consumer.adapter).isNotNull();

  }

  @Test(dataProvider = "generateDataPointsInt") public void shouldReadDataAndSendInt(Collection<DataPoint> dataPoints) {
    // Given a consumer
    GraphiteMetricsConsumer consumer = spy(new GraphiteMetricsConsumer());
    Map stormConf = ImmutableMap
        .of("metrics.graphite.host", testGraphiteHost, "metrics.graphite.port", testGraphitePort,
            "metrics.graphite.prefix", testPrefix, "metrics.reporter.name", testReporterName);

    Object obj = mock(Object.class);
    TopologyContext context = mock(TopologyContext.class);
    when(context.getStormId()).thenReturn(testTopologyName);
    IErrorReporter errorReporter = mock(IErrorReporter.class);
    consumer.prepare(stormConf, obj, context, errorReporter);
    doNothing().when(consumer).graphiteConnect();
    doNothing().when(consumer).appendToBuffer(Mockito.anyString(), Mockito.anyMap(), Mockito.anyLong());
    doNothing().when(consumer).sendMetrics();

    // and a collection of data points containing Integer data (already injected via data provider)
    // When the reporter processes each data point
    consumer.handleDataPoints(taskInfo, dataPoints);

    // Then the reporter should send properly formatted metric messages to Graphite
    HashMap<String, Double> expMap = buildExpectedMetricMap(dataPoints);
    verify(consumer).appendToBuffer(getExpectedMetricPrefix(), expMap, taskInfo.timestamp);
  }

  @Test(dependsOnMethods = { "shouldInitializeGraphiteReporter" }, dataProvider = "generateDataPointsLong")
  public void shouldReadDataAndSendLong(Collection<DataPoint> dataPoints) {
    //Given a consumer
    GraphiteMetricsConsumer consumer = spy(new GraphiteMetricsConsumer());

    Map stormConf = ImmutableMap
        .of("metrics.graphite.host", testStormSrcWorkerHost, "metrics.graphite.port", testStormSrcWorkerPort.toString(),
            "metrics.graphite.prefix", testPrefix, "metrics.reporter.name", testReporterName);
    Object obj = mock(Object.class);

    TopologyContext context = mock(TopologyContext.class);
    when(context.getStormId()).thenReturn(testTopologyName);
    IErrorReporter errorReporter = mock(IErrorReporter.class);
    consumer.prepare(stormConf, obj, context, errorReporter);
    doNothing().when(consumer).graphiteConnect();

    // and a collection of data points containing Integer data (already injected via data provider)
    // When the reporter processes each data point
    consumer.handleDataPoints(taskInfo, dataPoints);

    // Then the reporter should send properly formatted metric messages to Graphite
    HashMap<String, Double> expMap = buildExpectedMetricMap(dataPoints);
    verify(consumer).appendToBuffer(getExpectedMetricPrefix(), expMap, taskInfo.timestamp);
  }

  @Test(dependsOnMethods = { "shouldInitializeGraphiteReporter" }, dataProvider = "generateDataPointsFloat")
  public void shouldReadDataAndSendFloat(Collection<DataPoint> dataPoints) {
    //Given a consumer
    GraphiteMetricsConsumer consumer = spy(new GraphiteMetricsConsumer());

    Map stormConf = ImmutableMap
        .of("metrics.graphite.host", testStormSrcWorkerHost, "metrics.graphite.port", testStormSrcWorkerPort.toString(),
            "metrics.graphite.prefix", testPrefix, "metrics.reporter.name", testReporterName);
    Object obj = mock(Object.class);

    TopologyContext context = mock(TopologyContext.class);
    when(context.getStormId()).thenReturn(testTopologyName);
    IErrorReporter errorReporter = mock(IErrorReporter.class);
    consumer.prepare(stormConf, obj, context, errorReporter);
    doNothing().when(consumer).graphiteConnect();

    // and a collection of data points containing Integer data (already injected via data provider)
    // When the reporter processes each data point
    consumer.handleDataPoints(taskInfo, dataPoints);

    // Then the reporter should send properly formatted metric messages to Graphite
    HashMap<String, Double> expMap = buildExpectedMetricMap(dataPoints);
    verify(consumer).appendToBuffer(getExpectedMetricPrefix(), expMap, taskInfo.timestamp);
  }

  @Test(dependsOnMethods = { "shouldInitializeGraphiteReporter" }, dataProvider = "generateDataPointsDouble")
  public void shouldReadDataAndSendDouble(Collection<DataPoint> dataPoints) {
    //Given a consumer
    GraphiteMetricsConsumer consumer = spy(new GraphiteMetricsConsumer());

    Map stormConf = ImmutableMap
        .of("metrics.graphite.host", testStormSrcWorkerHost, "metrics.graphite.port", testStormSrcWorkerPort.toString(),
            "metrics.graphite.prefix", testPrefix, "metrics.reporter.name", testReporterName);

    Object obj = mock(Object.class);
    TopologyContext context = mock(TopologyContext.class);
    when(context.getStormId()).thenReturn(testTopologyName);
    IErrorReporter errorReporter = mock(IErrorReporter.class);
    consumer.prepare(stormConf, obj, context, errorReporter);
    doNothing().when(consumer).graphiteConnect();

    // and a collection of data points containing Integer data (already injected via data provider)
    // When the reporter processes each data point
    consumer.handleDataPoints(taskInfo, dataPoints);

    // Then the reporter should send properly formatted metric messages to Graphite
    HashMap<String, Double> expMap = buildExpectedMetricMap(dataPoints);
    verify(consumer).appendToBuffer(getExpectedMetricPrefix(), expMap, taskInfo.timestamp);
  }

  private HashMap<String, Double> buildExpectedMetricMap(Collection<DataPoint> dataPoints) {
    HashMap<String, Double> expMap = new HashMap<String, Double>();
    for (DataPoint dp : dataPoints) {

      Map<String, Object> datamap = (Map<String, Object>) dp.value;

      for (String key : datamap.keySet()) {
        expMap.put(key, ((Number) datamap.get(key)).doubleValue());
      }
    }
    return expMap;
  }

  private String getExpectedMetricPrefix() {
    String testSimpleTopologyName = "Example-Storm-Topology-Name";
    return testPrefix + "." +
        testSimpleTopologyName + "." +
        testStormComponentID + "." +
        testStormSrcWorkerHost + "." +
        testStormSrcWorkerPort + "." +
        testStormSrcTaskId;
  }

  @DataProvider(name = "generateDataPointsInt") public Object[][] generateDataPointsInt() {

    DataPoint dpInt = new DataPoint(Integer.toString(rng.nextInt(320), 10), new HashMap<String, Object>());
    for (int i = 0; i < 5; i++) {
      ((Map) dpInt.value).put(Integer.toString(rng.nextInt(320), 10), rng.nextInt());
    }
    Collection<DataPoint> dpList = new ArrayList<DataPoint>();
    dpList.add(dpInt);
    return new Object[][] { new Object[] { dpList } };
  }

  @DataProvider(name = "generateDataPointsLong") public Object[][] generateDataPointsLong() {
    DataPoint dpLong = new DataPoint(Integer.toString(rng.nextInt(320), 10), new HashMap<String, Object>());
    for (int i = 0; i < 5; i++) {
      ((Map) dpLong.value).put(Integer.toString(rng.nextInt(320), 10), rng.nextLong());
    }
    Collection<DataPoint> dpList = new ArrayList<DataPoint>();
    dpList.add(dpLong);
    return new Object[][] { new Object[] { dpList } };
  }

  @DataProvider(name = "generateDataPointsFloat") public Object[][] generateDataPointsFloat() {
    DataPoint dpFloat = new DataPoint(Integer.toString(rng.nextInt(320), 10), new HashMap<String, Object>());
    for (int i = 0; i < 5; i++) {
      ((Map) dpFloat.value).put(Integer.toString(rng.nextInt(320), 10), rng.nextFloat());
    }
    Collection<DataPoint> dpList = new ArrayList<DataPoint>();
    dpList.add(dpFloat);
    return new Object[][] { new Object[] { dpList } };
  }

  @DataProvider(name = "generateDataPointsDouble") public Object[][] generateDataPointsDouble() {
    DataPoint dpDouble = new DataPoint(Integer.toString(rng.nextInt(320), 10), new HashMap<String, Object>());
    for (int i = 0; i < 5; i++) {
      ((Map) dpDouble.value).put(Integer.toString(rng.nextInt(320), 10), rng.nextDouble());
    }
    Collection<DataPoint> dpList = new ArrayList<DataPoint>();
    dpList.add(dpDouble);
    return new Object[][] { new Object[] { dpList } };
  }
}