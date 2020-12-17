package ch.rwicht.rbd.helper;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.annotation.Nullable;

@PublicEvolving
public class JSONKeyValueSerializationSchema implements KafkaSerializationSchema<ObjectNode> {
    private ObjectWriter writer;
    private String topic;

    public JSONKeyValueSerializationSchema(String topic) {
        this.topic = topic;
    }

    @Override
    public ProducerRecord<byte[], byte[]> serialize(ObjectNode jsonNodes, @Nullable Long aLong) {
        if (this.writer == null) {
            this.writer = new ObjectMapper().writer();
        }

        byte[] bytes = new byte[0];
        try {
            bytes = this.writer.writeValueAsBytes(jsonNodes);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return new ProducerRecord<>(topic, bytes);
    }
}