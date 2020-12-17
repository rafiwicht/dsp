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

package ch.rwicht.rbd;

import ch.rwicht.rbd.helper.JSONKeyValueSerializationSchema;
import ch.rwicht.rbd.helper.MissingArgumentException;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.utils.MultipleParameterTool;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.streaming.util.serialization.JSONKeyValueDeserializationSchema;

import java.text.SimpleDateFormat;
import java.util.Properties;
import java.util.UUID;

public class ClientParser {
	private static final String CONSUMER_GROUP_ID = "client-parser";

	public static void main(String[] args) throws Exception {

		final MultipleParameterTool params = MultipleParameterTool.fromArgs(args);

		// set up the streaming execution environment
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		env.enableCheckpointing(120000);
		env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);

		// Validate input config
		env.getConfig().setGlobalJobParameters(params);

		if (!params.has("bootstrapServers") ||
				!params.has("consumerTopic") ||
				!params.has("producerTopic")) {
			throw new MissingArgumentException("Necessary variables: bootstrapServers, consumerTopic, producerTopic");
		}
		final String bootstrapServers = params.get("bootstrapServers");
		final String consumerTopic = params.get("consumerTopic");
		final String producerTopic = params.get("producerTopic");

		// Kafka Consumer
		Properties consumerProperties = new Properties();
		consumerProperties.setProperty("bootstrap.servers", bootstrapServers);
		consumerProperties.setProperty("group.id", CONSUMER_GROUP_ID);
		FlinkKafkaConsumer<ObjectNode> consumer = new FlinkKafkaConsumer<>(consumerTopic, new JSONKeyValueDeserializationSchema(false), consumerProperties);

		DataStream<ObjectNode> inputStream = env.addSource(consumer);

		ObjectMapper mapper = new ObjectMapper();
		SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss.SSS");

		// Parsing
		DataStream<ObjectNode> outputStream =  inputStream.map((MapFunction<ObjectNode, ObjectNode>) value -> {
			String message = value.get("value").get("message").asText();
			String[] fields = message.split(" ");
			ObjectNode node = mapper.createObjectNode();
			node.put("timestamp", formatter.parse(fields[0] + " " + fields[1]).getTime());
			node.put("javaFile", fields[2]);
			node.put("thread", fields[3]);
			node.put("logLevel", fields[4].replace(":", ""));
			node.put("user", fields[5].split("=")[1]);
			node.put("resource", fields[6].split("=")[1]);
			return node;
		});

		// Kafka Producer
		Properties producerProperties = new Properties();
		producerProperties.setProperty("bootstrap.servers", bootstrapServers);
		producerProperties.setProperty("enable.idempotence", "true");
		producerProperties.setProperty("transactional.id", UUID.randomUUID().toString());
		FlinkKafkaProducer<ObjectNode> producer = new FlinkKafkaProducer<>(producerTopic, new JSONKeyValueSerializationSchema(producerTopic) , producerProperties, FlinkKafkaProducer.Semantic.EXACTLY_ONCE);

		outputStream.addSink(producer);

		// execute program
		env.execute("Client Logs Parser");
	}
}
