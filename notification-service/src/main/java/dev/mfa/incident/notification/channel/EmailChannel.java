package dev.mfa.incident.notification.channel;

import dev.mfa.incident.notification.dto.NotificationMessageDto;
import dev.mfa.incident.notification.model.ChannelType;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * E-mail through SMTP ({@code quarkus.mailer.*}, A-0011), plain text with an HTML alternative. The
 * blocking mailer waits at most {@code quarkus.mailer.timeout}.
 */
@ApplicationScoped
public class EmailChannel implements NotificationChannel {

  private final Mailer mailer;

  public EmailChannel(Mailer mailer) {
    this.mailer = mailer;
  }

  @Override
  public ChannelType type() {
    return ChannelType.EMAIL;
  }

  @Override
  public void send(NotificationMessageDto message) {
    mailer.send(
        Mail.withText(message.recipient(), message.subject(), message.text())
            .setHtml(message.html()));
  }
}
