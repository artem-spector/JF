package com.jflop.server.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;
import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 5/7/17
 */
public class JacksonSerdes extends Serdes {

    private static ObjectMapper mapper = new ObjectMapper();

    public static class JacksonSerializer<T> implements Serializer<T> {

        @Override
        public void configure(Map<String, ?> configs, boolean isKey) { }

        @Override
        public byte[] serialize(String topic, Object data) {
            try {
                return mapper.writeValueAsBytes(data);
            } catch (JsonProcessingException e) {
                throw new SerializationException("Failed serializing object of class " + data.getClass().getName(), e);
            }
        }

        @Override
        public void close() { }
    }

    public static class JacksonDeserializer<T> implements Deserializer<T> {

        private TypeReference<T> type = new TypeReference<T>() {};

        public JacksonDeserializer() {
        }

        public JacksonDeserializer(TypeReference<T> type) {
            this.type = type;
        }

        @Override
        public void configure(Map<String, ?> configs, boolean isKey) { }

        @Override
        public T deserialize(String topic, byte[] data) {
            try {
                return mapper.readValue(data, type);
            } catch (IOException e) {
                throw new SerializationException("Failed deserializing object of type " + type.getType().getTypeName(), e);
            }
        }

        @Override
        public void close() { }
    }

    public static <T> Serde<T> jacksonSerde(TypeReference<T> type) {
        return serdeFrom(new JacksonSerializer<T>(), new JacksonDeserializer<T>(type));
    }

    public static Serde<AgentJVM> AgentJVM() {
        return jacksonSerde(new TypeReference<AgentJVM>() {});
    }

    public static Serde<Map> Map() {
        return jacksonSerde(new TypeReference<Map>() {});
    }
}
