package com.krizaka.sovkora.e2e;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Live AMQP infrastructure accessor for E2E assertions.
 *
 * <p>Connects directly to the provisioned RabbitMQ container via system properties injected by
 * Failsafe. No mocking, no Spring context — raw amqp-client against the live RabbitMQ instance.
 */
final class E2eAmqpClient {

  private static final String AMQP_HOST = System.getProperty("amqp.host", "localhost");
  private static final int AMQP_PORT = Integer.parseInt(System.getProperty("amqp.port", "5672"));

  private E2eAmqpClient() {}

  /**
   * Checks if RabbitMQ is connectable by opening and immediately closing a connection.
   *
   * @return true if the TCP handshake and AMQP protocol negotiation succeed.
   */
  static boolean isConnectable() {
    ConnectionFactory factory = createFactory();
    try (Connection conn = factory.newConnection()) {
      return conn.isOpen();
    } catch (IOException | TimeoutException e) {
      return false;
    }
  }

  /**
   * Checks if a queue with the given name exists in RabbitMQ.
   *
   * @param queueName The queue name to probe.
   * @return true if the queue exists and is declared.
   */
  static boolean queueExists(String queueName) {
    ConnectionFactory factory = createFactory();
    try (Connection conn = factory.newConnection();
        Channel channel = conn.createChannel()) {
      channel.queueDeclarePassive(queueName);
      return true;
    } catch (IOException | TimeoutException e) {
      return false;
    }
  }

  /**
   * Returns the message count of a queue.
   *
   * @param queueName The queue name to inspect.
   * @return Number of messages, or -1 if the queue does not exist.
   */
  static long getQueueMessageCount(String queueName) {
    ConnectionFactory factory = createFactory();
    try (Connection conn = factory.newConnection();
        Channel channel = conn.createChannel()) {
      return channel.queueDeclarePassive(queueName).getMessageCount();
    } catch (IOException | TimeoutException e) {
      return -1;
    }
  }

  /**
   * Checks that an exchange exists <b>and</b> has the expected type: passive declare proves
   * existence, then an active declare with the expected type/durability collapses idempotently on a
   * match and fails the channel (406) on a mismatch.
   *
   * @param exchangeName the exchange to probe
   * @param expectedType {@code topic} / {@code direct}
   * @return true if the exchange exists with the expected type.
   */
  static boolean exchangeIsDeclared(String exchangeName, String expectedType) {
    ConnectionFactory factory = createFactory();
    try (Connection conn = factory.newConnection();
        Channel channel = conn.createChannel()) {
      channel.exchangeDeclarePassive(exchangeName);
      channel.exchangeDeclare(exchangeName, expectedType, true, false, null);
      return true;
    } catch (IOException | TimeoutException e) {
      return false;
    }
  }

  /**
   * Declares a uniquely named, non-durable tap queue bound to the given exchange/pattern for
   * capturing published messages. Non-exclusive so later connections can poll it; callers must
   * {@link #deleteQueue(String)} it afterwards.
   *
   * @return the generated tap queue name.
   */
  static String declareTapQueue(String exchange, String bindingKey) throws IOException {
    ConnectionFactory factory = createFactory();
    try (Connection conn = factory.newConnection();
        Channel channel = conn.createChannel()) {
      String name = "e2e.tap." + java.util.UUID.randomUUID();
      // Durable + auto-delete, not transient: RabbitMQ 4 removed `transient_nonexcl_queues` and
      // refuses the whole connection on one. Auto-delete keeps the tap disposable — it goes when
      // the test's consumer does — which is the property this queue actually needs.
      channel.queueDeclare(name, true, false, true, null);
      channel.queueBind(name, exchange, bindingKey);
      return name;
    } catch (TimeoutException e) {
      throw new IOException(e);
    }
  }

  /**
   * Polls a queue for the next message body, waiting up to {@code timeoutMs}.
   *
   * @return the message body as UTF-8, or {@code null} on timeout.
   */
  static String pollMessage(String queueName, long timeoutMs) throws IOException {
    ConnectionFactory factory = createFactory();
    long deadline = System.currentTimeMillis() + timeoutMs;
    try (Connection conn = factory.newConnection();
        Channel channel = conn.createChannel()) {
      while (System.currentTimeMillis() < deadline) {
        var response = channel.basicGet(queueName, true);
        if (response != null) {
          return new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        }
        try {
          Thread.sleep( // NOSONAR java:S2925 - AMQP channel lifecycle prevents Awaitility
              // restructuring
              200);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return null;
        }
      }
      return null;
    } catch (TimeoutException e) {
      throw new IOException(e);
    }
  }

  /** Best-effort deletion of a tap queue. */
  static void deleteQueue(String queueName) {
    ConnectionFactory factory = createFactory();
    try (Connection conn = factory.newConnection();
        Channel channel = conn.createChannel()) {
      channel.queueDelete(queueName);
    } catch (IOException | TimeoutException ignored) {
      // teardown is best-effort
    }
  }

  private static ConnectionFactory createFactory() {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(AMQP_HOST);
    factory.setPort(AMQP_PORT);
    factory.setUsername("guest");
    factory.setPassword("guest");
    factory.setConnectionTimeout(5000);
    return factory;
  }
}
