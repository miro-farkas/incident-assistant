package dev.mfa.incident.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mfa.incident.config.IncidentStreamConfig;
import dev.mfa.incident.dto.StreamEventDto;
import dev.mfa.incident.exception.StreamLimitExceededException;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import io.smallrye.mutiny.subscription.BackPressureFailure;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class IncidentStreamServiceTest {

  private static final int BUFFER = 4;
  private static final int MAX_STREAMS = 2;
  private static final Duration RETRY_AFTER = Duration.ofSeconds(7);

  private final IncidentStreamService service = new IncidentStreamService(new TestConfig());

  @Test
  void everyOpenStreamReceivesEventsInOrder() {
    AssertSubscriber<StreamEventDto> first =
        service.open().subscribe().withSubscriber(subscriber());
    AssertSubscriber<StreamEventDto> second =
        service.open().subscribe().withSubscriber(subscriber());
    List<StreamEventDto> events = events(3);

    events.forEach(service::publish);

    assertThat(first.awaitItems(3).getItems()).containsExactlyElementsOf(events);
    assertThat(second.awaitItems(3).getItems()).containsExactlyElementsOf(events);
  }

  @Test
  void streamOnlyGetsEventsPublishedAfterItWasOpened() {
    service.publish(events(1).getFirst());
    AssertSubscriber<StreamEventDto> late = service.open().subscribe().withSubscriber(subscriber());
    StreamEventDto next = events(1).getFirst();

    service.publish(next);

    assertThat(late.awaitItems(1).getItems()).containsExactly(next);
  }

  @Test
  void clientThatFallsBehindLosesItsStreamWithoutAffectingOthers() {
    AssertSubscriber<StreamEventDto> slow =
        service.open().subscribe().withSubscriber(AssertSubscriber.create(0));
    AssertSubscriber<StreamEventDto> fast = service.open().subscribe().withSubscriber(subscriber());
    List<StreamEventDto> events = events(BUFFER + 2);

    events.forEach(service::publish);

    slow.awaitFailure().assertFailedWith(BackPressureFailure.class);
    assertThat(fast.awaitItems(events.size()).getItems()).containsExactlyElementsOf(events);
    fast.assertNotTerminated();
    assertThat(service.openStreams()).isEqualTo(1);
  }

  @Test
  void refusesStreamsAboveLimitUntilOneCloses() {
    AssertSubscriber<StreamEventDto> first =
        service.open().subscribe().withSubscriber(subscriber());
    service.open().subscribe().withSubscriber(subscriber());

    assertThatThrownBy(service::open)
        .isInstanceOfSatisfying(
            StreamLimitExceededException.class,
            e -> assertThat(e.retryAfter()).isEqualTo(RETRY_AFTER));
    assertThat(service.openStreams()).isEqualTo(MAX_STREAMS);

    first.cancel();

    assertThat(service.openStreams()).isEqualTo(MAX_STREAMS - 1);
    service.open().subscribe().withSubscriber(subscriber());
    assertThat(service.openStreams()).isEqualTo(MAX_STREAMS);
  }

  private static AssertSubscriber<StreamEventDto> subscriber() {
    return AssertSubscriber.create(Long.MAX_VALUE);
  }

  private static List<StreamEventDto> events(int count) {
    return IntStream.range(0, count)
        .mapToObj(
            i -> {
              UUID id = UUID.randomUUID();
              return new StreamEventDto(
                  id, "incident.state-changed", "{\"eventId\":\"" + id + "\"}");
            })
        .toList();
  }

  private static final class TestConfig implements IncidentStreamConfig {
    @Override
    public Duration heartbeatInterval() {
      return Duration.ofSeconds(15);
    }

    @Override
    public int clientBufferSize() {
      return BUFFER;
    }

    @Override
    public int maxStreams() {
      return MAX_STREAMS;
    }

    @Override
    public Duration retryAfter() {
      return RETRY_AFTER;
    }
  }
}
