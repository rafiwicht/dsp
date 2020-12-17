package ch.rwicht.rbd;

import ch.rwicht.rbd.ScalarFunctions.CalculateDifference;
import ch.rwicht.rbd.ScalarFunctions.ConvertRow;
import ch.rwicht.rbd.helper.MissingArgumentException;
import com.datastax.driver.core.Cluster;
import org.apache.commons.io.IOUtils;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.utils.MultipleParameterTool;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.cassandra.CassandraAppendTableSink;
import org.apache.flink.streaming.connectors.cassandra.ClusterBuilder;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.Tumble;
import org.apache.flink.table.api.java.StreamTableEnvironment;
import org.apache.flink.table.descriptors.Json;
import org.apache.flink.table.descriptors.Kafka;
import org.apache.flink.table.descriptors.Rowtime;
import org.apache.flink.table.descriptors.Schema;
import org.apache.flink.types.Row;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class LatencyMeasurement {
    private static final String CONSUMER_GROUP_ID_WEB = "latency-web";
    private static final String WEB_TABLE = "web ";
    private static final String CONSUMER_GROUP_ID_CLIENT = "latency-client";
    private static final String CLIENT_TABLE = "client";
    private static final String CASSANDRA_TABLE_SINK = "latencyTableSink";

    public static void main(String[] args) throws Exception {

        final MultipleParameterTool params = MultipleParameterTool.fromArgs(args);

        // set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(120000);
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        env.getConfig().setGlobalJobParameters(params);
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        EnvironmentSettings settings = EnvironmentSettings.newInstance()
                .useBlinkPlanner()
                .inStreamingMode()
                .build();

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
                        .field("timestamp", DataTypes.TIMESTAMP(3))
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
                        .field("timestamp", DataTypes.TIMESTAMP(3))
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
        tableEnv.registerFunction("convertRow", new ConvertRow());

        Table result = web
                .join(client, "user_server = user_client").select("timestamp_server, calculateDifference(timestamp_server, timestamp_client) as latency")
                .window(Tumble.over("15.seconds").on("timestamp_server").as("w"))
                .groupBy("w")
                .select("latency.min as min, latency.max as max, latency.avg as avg, w.start as timestamp");
                //.map("convertRow(min, max, avg, timestamp)").as("min, max, avg, date, timestamp");

        tableEnv.toAppendStream(result, Row.class).print();

        /*ClusterBuilder builder = new ClusterBuilder() {
            @Override
            protected Cluster buildCluster(Cluster.Builder builder) {
                return Cluster
                        .builder()
                        .withCredentials(cassandraUser, cassandraPassword)
                        .addContactPoints(cassandraServers.split(","))
                        .withPort(cassandraPort)
                        .build();
            }
        };

        CassandraAppendTableSink sink = new CassandraAppendTableSink(
                builder,
                // the query must match the schema of the table
                "UPDATE latency.result SET min = ?, max = ?, avg = ? WHERE date = ? AND timestamp = ?;");

        tableEnv.registerTableSink(
                CASSANDRA_TABLE_SINK,
                new String[]{"min", "max", "avg", "date", "timestamp"},
                new TypeInformation[]{Types.LONG, Types.LONG, Types.LONG, Types.INT, Types.LONG},
                sink);

        result.insertInto(CASSANDRA_TABLE_SINK);*/

        // execute program
        env.execute("Latency Measurement");
    }
}
