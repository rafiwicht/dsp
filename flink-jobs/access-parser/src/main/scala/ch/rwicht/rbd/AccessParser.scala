/*
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

package ch.rwicht.rbd

import java.util.Properties

import ch.rwicht.rbd.schemas.JSONKeyValueSerializationSchema
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, FlinkKafkaProducer}
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.flink.streaming.util.serialization.JSONKeyValueDeserializationSchema

/**
 * Skeleton for a Flink Streaming Job.
 *
 * For a tutorial how to write a Flink streaming application, check the
 * tutorials and examples on the <a href="https://flink.apache.org/docs/stable/">Flink Website</a>.
 *
 * To package your application into a JAR file for execution, run
 * 'mvn clean package' on the command line.
 *
 * If you change the name of the main class (with the public static void main(String[] args))
 * method, change the respective entry in the POM.xml file (simply search for 'mainClass').
 */
object AccessParser {
  def main(args: Array[String]) {
    // variables
    val bootstrapServers = "rbd-cluster-kafka-brokers.kafka.svc:9092"
    val consumerGroupId = "access-parser"
    val consumerTopic = "webserver-access"
    val producerTopic = "webserver-access-cleaned"

    // set up the streaming execution environment
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.enableCheckpointing(5000)

    // Kafka Consumer
    val consumerProperties = new Properties()
    consumerProperties.setProperty("bootstrap.servers", bootstrapServers)
    consumerProperties.setProperty("group.id", consumerGroupId)
    val consumer = new FlinkKafkaConsumer[ObjectNode](consumerTopic, new JSONKeyValueDeserializationSchema(false), consumerProperties)

    val stream = env.addSource(consumer)

    // Parsing
    stream.map {t => {
      val message = t.get("value").get("message").asText()

      val fields = message.replace("\"", "").replace("[", "").split(" ")
      val oN = new ObjectMapper().createObjectNode()

      oN.put("ip", fields(0))
      oN.put("user", fields(2))
      oN.put("timestamp", fields(3) + " " + fields(4))
      oN.put("method", fields(5))
      oN.put("path", fields(6))
      oN.put("httpVersion", fields(7))
      oN.put("httpCode", fields(8))
      oN.put("bytes", fields(9))
      oN.put("referer", fields(10))
      oN.put("userAgent", message.split("\"")(5))
    }}

    // Kafka Producer
    val producerProperties = new Properties()
    producerProperties.setProperty("bootstrap.servers", bootstrapServers)
    val producer = new FlinkKafkaProducer[ObjectNode](producerTopic, new JSONKeyValueSerializationSchema[ObjectNode](producerTopic), producerProperties, FlinkKafkaProducer.Semantic.EXACTLY_ONCE)
    producer.setWriteTimestampToKafka(true)

    stream.addSink(producer)

    // execute program
    env.execute("Webserver Access Parser")
  }
}
