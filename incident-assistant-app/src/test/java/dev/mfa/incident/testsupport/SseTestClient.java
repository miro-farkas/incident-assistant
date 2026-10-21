package dev.mfa.incident.testsupport;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Reads a Server-Sent Events stream in the background and parses it into events and comments, so
 * tests can wait for what the server sent.
 */
public final class SseTestClient implements AutoCloseable {

  /**
   * One dispatched SSE event; {@code data} lines joined with newlines.
   *
   * @param otherFields names of fields other than {@code event}, {@code id} and {@code data}
   */
  public record Event(String name, String id, String data, List<String> otherFields) {}

  private final BlockingQueue<Event> events = new LinkedBlockingQueue<>();
  private final BlockingQueue<String> comments = new LinkedBlockingQueue<>();
  private final CompletableFuture<HttpResponse<Stream<String>>> response;

  private SseTestClient(CompletableFuture<HttpResponse<Stream<String>>> response) {
    this.response = response;
  }

  public static SseTestClient open(URI uri) {
    HttpRequest request =
        HttpRequest.newBuilder(uri).header("Accept", "text/event-stream").GET().build();
    CompletableFuture<HttpResponse<Stream<String>>> response =
        HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofLines());
    SseTestClient client = new SseTestClient(response);
    response.thenAcceptAsync(
        r -> client.read(r.body()), command -> Thread.ofVirtual().start(command));
    return client;
  }

  /** HTTP status of the stream response. */
  public int status(Duration timeout) throws Exception {
    return response.get(timeout.toMillis(), TimeUnit.MILLISECONDS).statusCode();
  }

  /** Waits for the next event matching {@code filter}; earlier non-matching events are dropped. */
  public Event next(Predicate<Event> filter, Duration timeout) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (true) {
      Event event = events.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
      if (event == null) {
        throw new AssertionError("No matching event within " + timeout);
      }
      if (filter.test(event)) {
        return event;
      }
    }
  }

  public String nextComment(Duration timeout) throws InterruptedException {
    String comment = comments.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    if (comment == null) {
      throw new AssertionError("No comment within " + timeout);
    }
    return comment;
  }

  @Override
  public void close() {
    response.thenAccept(r -> r.body().close());
    response.cancel(true);
  }

  private void read(Stream<String> lines) {
    String[] name = {null};
    String[] id = {null};
    List<String> data = new ArrayList<>();
    List<String> otherFields = new ArrayList<>();
    try (lines) {
      lines.forEach(
          line -> {
            if (line.isEmpty()) {
              if (!data.isEmpty()) {
                events.add(
                    new Event(name[0], id[0], String.join("\n", data), List.copyOf(otherFields)));
              }
              name[0] = null;
              id[0] = null;
              data.clear();
              otherFields.clear();
            } else if (line.startsWith(":")) {
              comments.add(line.substring(1).strip());
            } else if (line.startsWith("event:")) {
              name[0] = value(line);
            } else if (line.startsWith("id:")) {
              id[0] = value(line);
            } else if (line.startsWith("data:")) {
              data.add(value(line));
            } else {
              otherFields.add(line.substring(0, Math.max(line.indexOf(':'), 0)));
            }
          });
    } catch (RuntimeException e) {
      if (!(e.getCause() instanceof IOException)) {
        throw e;
      }
    }
  }

  private static String value(String line) {
    String value = line.substring(line.indexOf(':') + 1);
    return value.startsWith(" ") ? value.substring(1) : value;
  }
}
