package au.edu.uow.csci318.planning.infrastructure.stream;

import au.edu.uow.csci318.planning.domain.stream.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.stereotype.Component;

/**
 * Actual Kafka Streams DSL computations, independent of the binder and testable with
 * TopologyTestDriver.
 */
@Component
public class PlanningStreamTopology {
  public static final String WORKLOAD_STORE = "account-workload-v2-store";
  public static final String PROGRESS_STORE = "subject-weekly-progress-v2-store";
  public static final String REJECTED_TOPIC = "planning-rejected-events";
  private final EventDecoder decoder;
  private final ObjectMapper json;

  public PlanningStreamTopology(EventDecoder decoder, ObjectMapper json) {
    this.decoder = decoder;
    this.json = json;
  }

  public KStream<String, String> workload(KStream<String, String> input) {
    KStream<String, EventDecoder.Decoded<AssessmentChange>> decoded =
        input.mapValues(decoder::assessment);
    decoded
        .filter((key, value) -> !value.valid())
        .mapValues(EventDecoder.Decoded::rejection)
        .to(REJECTED_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
    return decoded
        .filter((key, value) -> value.valid())
        .mapValues(EventDecoder.Decoded::change)
        .selectKey((key, change) -> change.ownerId().toString())
        .groupByKey(Grouped.with(Serdes.String(), serde(AssessmentChange.class)))
        .aggregate(
            WorkloadState::empty,
            (key, change, state) -> state.apply(change),
            Materialized.<String, WorkloadState, KeyValueStore<Bytes, byte[]>>as(WORKLOAD_STORE)
                .withKeySerde(Serdes.String())
                .withValueSerde(serde(WorkloadState.class)))
        .toStream()
        .mapValues(this::write);
  }

  public KStream<String, String> progress(
      KStream<String, String> activity, KStream<String, String> subjects) {
    KStream<String, EventDecoder.Decoded<ProgressChange>> decoded =
        activity.merge(subjects).mapValues(decoder::progress);
    decoded
        .filter((key, value) -> !value.valid())
        .mapValues(EventDecoder.Decoded::rejection)
        .to(REJECTED_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
    return decoded
        .filter((key, value) -> value.valid())
        .mapValues(EventDecoder.Decoded::change)
        .selectKey((key, change) -> change.key())
        .groupByKey(Grouped.with(Serdes.String(), serde(ProgressChange.class)))
        .aggregate(
            ProgressState::empty,
            (key, change, state) -> state.apply(change),
            Materialized.<String, ProgressState, KeyValueStore<Bytes, byte[]>>as(PROGRESS_STORE)
                .withKeySerde(Serdes.String())
                .withValueSerde(serde(ProgressState.class)))
        .toStream()
        .mapValues(this::write);
  }

  private <T> JsonSerde<T> serde(Class<T> type) {
    return new JsonSerde<>(type, json).noTypeInfo();
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Projection serialization failed", exception);
    }
  }
}
