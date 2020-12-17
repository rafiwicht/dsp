package ch.rwicht.rbd.schemas

import java.lang

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import org.apache.kafka.clients.producer.ProducerRecord

class JSONKeyValueSerializationSchema[ObjectNode](topic: String) extends KafkaSerializationSchema[ObjectNode] {
  override def serialize(t: ObjectNode, aLong: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
    val writer = new ObjectMapper().writer()
    val bytes = writer.writeValueAsBytes(t)
    new ProducerRecord[Array[Byte], Array[Byte]](topic, bytes)
  }
}
