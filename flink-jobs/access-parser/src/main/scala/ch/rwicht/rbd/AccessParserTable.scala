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

import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.table.api.scala.StreamTableEnvironment
import org.apache.flink.table.descriptors.{Json, Kafka, Schema}


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
object AccessParserTable {
  def main(args: Array[String]) {
    // variables
    val bootstrapServers = "rbd-cluster-kafka-brokers.kafka.svc:9092"
    val consumerGroupId = "access-parser"
    val consumerTopic = "webserver-access"
    val producerTopic = "webserver-access-cleaned"
    val sourceTable = "sourceTopic"
    val sinkTable = "sinkTopic"

    val inputJsonSchema = """
     {
      "$schema": "http://json-schema.org/draft-07/schema#",
      "$id": "http://rwicht.ch/webserver-access.schema.json",
      "title": "Event",
      "description": "An event for the from webserver access logs",
      "type": "object",
      "properties": {
        "@timestamp": {
          "type": "string"
        },
        "tags":{
          "type": "array",
          "items": {
            "type": "string"
          }
        },
        "@version": {
          "type": "string"
        },
        "message": {
          "type": "string"
        },
        "path": {
          "type": "string"
        },
        "host": {
          "type": "string"
        }
      },
      "required": [ "@timestamp", "tags", "@version", "message", "path", "host" ]
    }"""

    // set up the streaming execution environment
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.enableCheckpointing(5000)

    val tableEnv = StreamTableEnvironment.create(env)

    val consumerProperties = new Properties()
    consumerProperties.setProperty("bootstrap.servers", bootstrapServers)
    consumerProperties.setProperty("group.id", consumerGroupId)

    val producerProperties = new Properties()
    producerProperties.setProperty("bootstrap.servers", bootstrapServers)

    tableEnv.connect(new Kafka()
      .version("universal")
      .topic(consumerTopic)
      .properties(consumerProperties)
      .startFromGroupOffsets()
    )
    .withFormat(new Json()
      .jsonSchema(inputJsonSchema)
      .failOnMissingField(false))
    .withSchema(new Schema()
      .field("timestamp", "STRING").from("@timestamp")
      .field("tags", "ARRAY<STRING>")
      .field("version", "STRING").from("@version")
      .field("message", "STRING")
      .field("path", "STRING")
    .field("host", "STRING"))
    .inAppendMode()
    .createTemporaryTable(sourceTable)

    tableEnv.connect(new Kafka()
      .version("universal")
      .topic(producerTopic)
      .properties(producerProperties)
      .sinkPartitionerRoundRobin()
    )
    .withFormat(new Json()
      .jsonSchema(inputJsonSchema)
      .failOnMissingField(false))
    .withSchema(new Schema()
      .field("timestamp", "STRING")
      .field("tags", "ARRAY<STRING>")
      .field("version", "STRING")
      .field("message", "STRING")
      .field("path", "STRING")
      .field("host", "STRING"))
    .inAppendMode()
    .createTemporaryTable(sinkTable)

    tableEnv.from(sourceTable).insertInto(sinkTable)

    // execute program
    tableEnv.execute("Webserver Access Parser")
  }
}
