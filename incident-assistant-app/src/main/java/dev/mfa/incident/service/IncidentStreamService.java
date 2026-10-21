package dev.mfa.incident.service;

import dev.mfa.incident.config.IncidentStreamConfig;
import dev.mfa.incident.dto.StreamEventDto;
import dev.mfa.incident.exception.StreamLimitExceededException;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.logging.Logger;

/**
 * Fans out incident events to the open streams of this instance (P-0018, A-0009). Events go to
 * every stream open at the time, in the order they were received. Each stream has its own bounded
 * buffer: a client that falls behind loses its stream, the others are not slowed down.
 */
@ApplicationScoped
public class IncidentStreamService {

  private static final Logger LOG = Logger.getLogger(IncidentStreamService.class);

  private final IncidentStreamConfig config;
  private final Flow.Processor<StreamEventDto, StreamEventDto> events;
  private final Multi<StreamEventDto> broadcast;
  private final AtomicInteger openStreams = new AtomicInteger();

  public IncidentStreamService(IncidentStreamConfig config) {
    this.config = config;
    BroadcastProcessor<StreamEventDto> processor = BroadcastProcessor.create();
    this.broadcast = processor;
    this.events = processor.serialized();
  }

  /** Sends the event to every open stream; never blocks. */
  public void publish(StreamEventDto event) {
    events.onNext(event);
  }

  /**
   * Opens a stream of the events published from now on. The slot is released when the stream ends
   * for any reason: cancellation by the client, overflow of its buffer, or shutdown.
   *
   * @throws StreamLimitExceededException if {@link IncidentStreamConfig#maxStreams()} streams are
   *     open
   */
  public Multi<StreamEventDto> open() {
    int maxStreams = config.maxStreams();
    if (openStreams.incrementAndGet() > maxStreams) {
      openStreams.decrementAndGet();
      throw new StreamLimitExceededException(maxStreams, config.retryAfter());
    }
    return broadcast
        .onOverflow()
        .buffer(config.clientBufferSize())
        .onFailure()
        .invoke(failure -> LOG.debugf("Event stream closed: %s", failure.getMessage()))
        .onTermination()
        .invoke(openStreams::decrementAndGet);
  }

  /** Number of streams currently open on this instance. */
  public int openStreams() {
    return openStreams.get();
  }
}
