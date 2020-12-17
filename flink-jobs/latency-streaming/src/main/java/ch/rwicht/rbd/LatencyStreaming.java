package ch.rwicht.rbd;

import ch.rwicht.rbd.helper.MissingArgumentException;
import ch.rwicht.rbd.scalar.CalculateDifference;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.LocalDate;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple5;
import org.apache.flink.api.java.typeutils.TupleTypeInfo;
import org.apache.flink.api.java.utils.MultipleParameterTool;
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
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.java.StreamTableEnvironment;
import org.apache.flink.table.descriptors.Json;
import org.apache.flink.table.descriptors.Kafka;
import org.apache.flink.table.descriptors.Rowtime;
import org.apache.flink.table.descriptors.Schema;
import org.apache.flink.types.Row;
import org.apache.flink.util.Collector;

import java.util.Date;

/**
 * Skeleton for a Flink Streaming Job.
 *
 * <p>For a tutorial how to write a Flink streaming application, check the
 * tutorials and examples on the <a href="https://flink.apache.org/docs/stable/">Flink Website</a>.
 *
 * <p>To package your application into a JAR file for execution, run
 * 'mvn clean package' on the command line.
 *
 * <p>If you change the name of the main class (with the public static void main(String[] args))
 * method, change the respective entry in the POM.xml file (simply search for 'mainClass').
 */
public class LatencyStreaming {
    private static final String CONSUMER_GROUP_ID_WEB = "latency-web";
    private static final String WEB_TABLE = "web ";
    private static final String CONSUMER_GROUP_ID_CLIENT = "latency-client";
    private static final String CLIENT_TABLE = "client";

    public static void main(String[] args) throws Exception {

        final MultipleParameterTool params = MultipleParameterTool.fromArgs(args);

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(120000);
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        // Validate cluster config
        if (!params.has("bootstrapServers") ||
                !params.has("consumerTopicWeb") ||
                !params.has("consumerTopicClient") ||
                !params.has("cassandraServers") ||
                !params.has("cassandraPort") ||
                !params.has("cassandraUser") ||
                !params.has("cassandraPassword")) {
            throw new MissingArgumentException("Necessary variables: bootstrapServers, consumerTopicWeb, consumerTopicClient, cassandraServers, cassandraPort, cassandraUser, cassandraPassword");
        }

        final String bootstrapServers = params.get("bootstrapServers");
        final String consumerTopicWeb = params.get("consumerTopicWeb");
        final String consumerTopicClient = params.get("consumerTopicClient");
        final String cassandraServers = params.get("cassandraServers");
        final int cassandraPort = Integer.parseInt(params.get("cassandraPort"));
        final String cassandraUser = params.get("cassandraUser");
        final String cassandraPassword = params.get("cassandraPassword");

        EnvironmentSettings settings = EnvironmentSettings.newInstance()
                .useBlinkPlanner()
                .inStreamingMode()
                .build();

        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, settings);

        // Connect web topic
        tableEnv.connect(new Kafka()
                .version("universal")
                .topic(consumerTopicWeb)
                .property("bootstrap.servers", bootstrapServers)
                .property("group.id", CONSUMER_GROUP_ID_WEB)
                .property("zookeeper.connect", "")
                .startFromGroupOffsets()
        )
                .withFormat(new Json()
                        .failOnMissingField(false))
                .withSchema(new Schema()
                        .field("ip", DataTypes.STRING())
                        .field("user", DataTypes.STRING())
                        .field("timestamp", DataTypes.BIGINT())
                        .rowtime(new Rowtime().timestampsFromField("timestamp").watermarksPeriodicBounded(30000))
                        .field("method", DataTypes.STRING())
                        .field("path", DataTypes.STRING())
                        .field("httpVersion", DataTypes.STRING())
                        .field("httpCode", DataTypes.INT())
                        .field("bytes", DataTypes.INT())
                        .field("referer", DataTypes.STRING())
                        .field("userAgent", DataTypes.STRING()))
                .inAppendMode()
                .createTemporaryTable(WEB_TABLE);

        // Clean web table
        Table web = tableEnv.from(WEB_TABLE).dropColumns("ip, method, path, httpVersion, httpCode, bytes, referer, userAgent").renameColumns("timestamp as timestamp_server, user as user_server");

        // Connect client topic
        tableEnv.connect(new Kafka()
                .version("universal")
                .topic(consumerTopicClient)
                .property("bootstrap.servers", bootstrapServers)
                .property("group.id", CONSUMER_GROUP_ID_CLIENT)
                .property("zookeeper.connect", "")
                .startFromGroupOffsets()
        )
                .withFormat(new Json()
                        .failOnMissingField(false))
                .withSchema(new Schema()
                        .field("timestamp", DataTypes.BIGINT())
                        .rowtime(new Rowtime().timestampsFromField("timestamp").watermarksPeriodicBounded(30000))
                        .field("javaFile", DataTypes.STRING())
                        .field("thread", DataTypes.INT())
                        .field("logLevel", DataTypes.STRING())
                        .field("user", DataTypes.STRING())
                        .field("resource", DataTypes.STRING()))
                .inAppendMode()
                .createTemporaryTable(CLIENT_TABLE);

        // Clean web table
        Table client = tableEnv.from(CLIENT_TABLE).dropColumns("javaFile, thread, logLevel, resource").renameColumns("timestamp as timestamp_client, user as user_client");

        // Define aggregate function
        tableEnv.registerFunction("calculateDifference", new CalculateDifference());

        Table table = web.join(client, "user_server = user_client").select("timestamp_server, calculateDifference(timestamp_server, timestamp_client) as latency");

        DataStream<Tuple2<Long, Long>> stream = tableEnv
                .toAppendStream(table, Row.class)
                .map((MapFunction<Row, Tuple2<Long, Long>>) row -> new Tuple2<>((Long)row.getField(0), (Long)row.getField(1)))
                .returns(new TupleTypeInfo<>(BasicTypeInfo.LONG_TYPE_INFO, BasicTypeInfo.LONG_TYPE_INFO))
                .assignTimestampsAndWatermarks(new LogTimestampWatermark(Time.seconds(30)));

        DataStream<Tuple5<Long, Long, Double, LocalDate, Date>> result = stream
                .windowAll(TumblingEventTimeWindows.of(Time.seconds(15)))
				.apply(new AllWindowFunction<Tuple2<Long, Long>, Tuple5<Long, Long, Double, LocalDate, Date>, TimeWindow>() {
                    @Override
                    public void apply(TimeWindow timeWindow, Iterable<Tuple2<Long, Long>> iterable, Collector<Tuple5<Long, Long, Double, LocalDate, Date>> collector) {
                        long timestamp = timeWindow.getEnd();
                        double counter = 0;
                        long sum = 0;
                        Long max = null;
                        Long min = null;
                        for (Tuple2<Long, Long> i : iterable) {
                            Long latency = i.getField(1);
                            counter++;
                            sum += latency;
                            if(max == null || latency > max) max = latency;
                            else if(min == null || latency < min) min = latency;
                        }
                        collector.collect(new Tuple5<>(
                                min,
                                max,
                                sum / counter,
                                LocalDate.fromMillisSinceEpoch(timestamp),
                                new Date(timestamp)));
                    }
                });

        CassandraSink.addSink(result)
                .setQuery("UPDATE latency.result SET min = ?, max = ?, avg = ? WHERE date = ? AND timestamp = ?;")
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
        // execute program
        env.execute("Latency streaming");
    }

    protected static final class LogTimestampWatermark extends BoundedOutOfOrdernessTimestampExtractor<Tuple2<Long, Long>> {

        public LogTimestampWatermark(Time maxOutOfOrderness) {
            super(maxOutOfOrderness);
        }

        @Override
        public long extractTimestamp(Tuple2<Long, Long> value) {
            return value.getField(0);
        }
    }
}
