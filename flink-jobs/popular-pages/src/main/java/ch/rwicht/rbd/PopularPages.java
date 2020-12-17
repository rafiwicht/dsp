package ch.rwicht.rbd;

import ch.rwicht.rbd.helper.MissingArgumentException;
import com.datastax.driver.core.Cluster;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.typeutils.ResultTypeQueryable;
import org.apache.flink.api.java.typeutils.TupleTypeInfo;
import org.apache.flink.api.java.utils.MultipleParameterTool;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.connectors.cassandra.CassandraSink;
import org.apache.flink.streaming.connectors.cassandra.ClusterBuilder;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.util.serialization.JSONKeyValueDeserializationSchema;
import org.apache.flink.util.Collector;

import java.util.Date;
import java.util.Properties;

public class PopularPages {
	private static final String CONSUMER_GROUP_ID = "popular-pages";

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

		DataStream<Tuple3<Integer, String, Date>> stream = inputStream
				.map(new ObjectNodeConverter())
				.keyBy(0)
				.window(TumblingEventTimeWindows.of(Time.seconds(15))).apply(new WindowFunction<Tuple2<String, Long>, Tuple3<Integer, String, Date>, Tuple, TimeWindow>() {
					@Override
					public void apply(Tuple tuple, TimeWindow window, Iterable<Tuple2<String, Long>> input, Collector<Tuple3<Integer, String, Date>> out) {
						int counter = 0;
						String path = input.iterator().next().getFieldNotNull(0);
						for (Tuple2<String, Long> ignored : input) {
							counter++;
						}
						out.collect(new Tuple3<>(
								counter,
								path,
								new Date(window.getEnd())));
					}

		});

		CassandraSink.addSink(stream)
				.setQuery("UPDATE popular.result SET value = ? WHERE path = ? AND timestamp = ?;")
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

		env.execute("Popular Pages");
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

	private static final class ObjectNodeConverter implements ResultTypeQueryable<Tuple2<String, Long>>, MapFunction<ObjectNode, Tuple2<String, Long>> {

		@Override
		public Tuple2<String, Long> map(ObjectNode value) {
			JsonNode node = value.get("value");
			String[] pathParts = node.get("path").asText().split("/");
			String path = "default";
			if (pathParts.length >= 2) {
				path = pathParts[1];
			}
			return new Tuple2<>(path, node.get("timestamp").asLong());
		}

		@Override
		public TypeInformation<Tuple2<String, Long>> getProducedType() {
			return new TupleTypeInfo<>(BasicTypeInfo.STRING_TYPE_INFO, BasicTypeInfo.LONG_TYPE_INFO);
		}
	}
}
