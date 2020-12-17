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

public class AccessParser {
	private static final String CONSUMER_GROUP_ID = "access-parser";
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
		SimpleDateFormat formatter = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss.SSS Z");

		// Parsing
		DataStream<ObjectNode> outputStream =  inputStream.map((MapFunction<ObjectNode, ObjectNode>) value -> {
			String message = value.get("value").get("message").asText();

			String[] fields = message.replace("\"", "").replace("[", "").replace("]", "").split(" ");
			ObjectNode node = mapper.createObjectNode();
			node.put("ip", fields[0]);
			node.put("user", fields[2]);
			node.put("timestamp", formatter.parse(fields[3] + " " + fields[4]).getTime());
			node.put("method", fields[5]);
			node.put("path", fields[6]);
			node.put("httpVersion", fields[7]);
			node.put("httpCode", Integer.parseInt(fields[8]));
			node.put("bytes", Integer.parseInt(fields[9]));
			node.put("referer", fields[10]);
			node.put("userAgent", message.split("\"")[5]);
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
		env.execute("Access Logs Parser");
	}
}
