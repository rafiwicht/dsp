package ch.rwicht.rbd;

import ch.rwicht.rbd.helper.MissingArgumentException;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.LocalDate;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.utils.MultipleParameterTool;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.functions.windowing.AllWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.connectors.cassandra.CassandraSink;
import org.apache.flink.streaming.connectors.cassandra.ClusterBuilder;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.util.serialization.JSONKeyValueDeserializationSchema;
import org.apache.flink.util.Collector;
import org.pmml4s.model.Model;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class MaliciousAccess {
    private static final String CONSUMER_GROUP_ID = "malicious-access";

    public static void main(String[] args) throws Exception {

		final MultipleParameterTool params = MultipleParameterTool.fromArgs(args);

		// set up the streaming execution environment
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		env.enableCheckpointing(120000);
		env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
		env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

		// Validate input config
		env.getConfig().setGlobalJobParameters(params);

		if (!params.has("bootstrapServers") ||
				!params.has("consumerTopic") ||
				!params.has("cassandraServers") ||
				!params.has("cassandraPort") ||
				!params.has("cassandraUser") ||
				!params.has("cassandraPassword")) {
			throw new MissingArgumentException("Necessary variables: bootstrapServers, consumerTopic, cassandraServers, cassandraPort, cassandraUser, cassandraPassword");
		}
		final String bootstrapServers = params.get("bootstrapServers");
		final String consumerTopic = params.get("consumerTopic");
		final String cassandraServers = params.get("cassandraServers");
		final int cassandraPort = Integer.parseInt(params.get("cassandraPort"));
		final String cassandraUser = params.get("cassandraUser");
		final String cassandraPassword = params.get("cassandraPassword");


		Properties consumerProperties = new Properties();
		consumerProperties.setProperty("bootstrap.servers", bootstrapServers);
		consumerProperties.setProperty("group.id", CONSUMER_GROUP_ID);

		FlinkKafkaConsumer<ObjectNode> consumer = new FlinkKafkaConsumer<>(consumerTopic, new JSONKeyValueDeserializationSchema(false), consumerProperties);

		DataStream<ObjectNode> inputStream = env.addSource(consumer)
				.assignTimestampsAndWatermarks(new LogTimestampWatermark(Time.seconds(30)));

		DataStream<Tuple3<Integer, LocalDate, Date>> stream = inputStream
				.map(new Predict())
				.windowAll(TumblingEventTimeWindows.of(Time.seconds(15)))
				.apply(new AllWindowFunction<ObjectNode, Tuple3<Integer, LocalDate, Date>, TimeWindow>() {
					@Override
					public void apply(TimeWindow timeWindow, Iterable<ObjectNode> iterable, Collector<Tuple3<Integer, LocalDate, Date>> collector) throws Exception {
						long timestamp = timeWindow.getEnd();
						int counter = 0;
						for(ObjectNode ignored : iterable) {
							counter++;
						}
						collector.collect(new Tuple3<>(
								counter,
								LocalDate.fromMillisSinceEpoch(timestamp),
								new Date(timestamp)));
					}
				});

		CassandraSink.addSink(stream)
				.setQuery("UPDATE malicious.result SET value = ? WHERE date = ? AND timestamp = ?;")
				.setClusterBuilder(new ClusterBuilder() {
					@Override
					protected Cluster buildCluster(Cluster.Builder builder) {
						return Cluster
								.builder()
								.withCredentials(cassandraUser, cassandraPassword)
								.addContactPoints(cassandraServers.split(","))
								.withPort(cassandraPort)
								.build();
					}
				})
				.build();

		env.execute("Malicious Access");
    }

	private static final class Predict implements MapFunction<ObjectNode, ObjectNode> {
		private static final Model MODEL = Model.fromInputStream(MaliciousAccess.class.getClassLoader().getResourceAsStream("model.pmml"));

		@Override
		public ObjectNode map(ObjectNode value) {

			Map<String, Object> result = MODEL.predict(new HashMap<String, Object>() {{
				put("timestamp", value.get("value").get("timestamp").asLong());
				put("method", byteArrayToInt(value.get("value").get("method").asText().getBytes()));
				put("httpCode", value.get("value").get("httpCode").asInt());
				put("userAgent",  byteArrayToInt(value.get("value").get("userAgent").asText().getBytes()));
			}});


			value.with("value").put("malicious", ((double)result.get("probability(1)") > 0.5) ? 1 : 0);
			return value;
		}

		private static int byteArrayToInt(byte[] b)
		{
			if(b.length == 0) return 0;
			return   b[2] & 0xFF |
					(b[1] & 0xFF) << 8 |
					(b[0] & 0xFF) << 16;
		}
	}

	private static final class LogTimestampWatermark extends BoundedOutOfOrdernessTimestampExtractor<ObjectNode> {

		public LogTimestampWatermark(Time maxOutOfOrderness) {
			super(maxOutOfOrderness);
		}

		@Override
		public long extractTimestamp(ObjectNode jsonNodes) {
			return jsonNodes.get("value").get("timestamp").asLong();
		}
	}
}
