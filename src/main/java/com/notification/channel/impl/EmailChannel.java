package com.notification.channel.impl;

import com.notification.channel.ChannelSendContext;
import com.notification.channel.NotificationChannel;
import com.notification.model.enums.ChannelTypeEnum;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Email channel using JavaMailSender.
 * Sends HTML emails to configured recipient lists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailChannel implements NotificationChannel {

    private final JavaMailSender mailSender;

    @Value("${channel.email.from:noreply@notification.com}")
    private String from;

    @Override
    public String channelName() {
        return "email";
    }

    @Override
    public ChannelTypeEnum channelType() {
        return ChannelTypeEnum.BATCH;
    }

    @Override
    public void send(ChannelSendContext context) {
        List<String> emails = context.getOriginalMessage().getRecipients() != null
                ? context.getOriginalMessage().getRecipients().getEmails()
                : List.of();

        if (emails.isEmpty()) {
            log.warn("No email recipients, skipping");
            return;
        }

        for (String email : emails) {
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                helper.setFrom(from);
                helper.setTo(email);
                helper.setSubject(context.getRenderedTitle());
                helper.setText(context.getRenderedContent(), true);
                mailSender.send(mimeMessage);
                log.debug("Email sent to: {}", email);
            } catch (Exception e) {
                log.error("Failed to send email to {}: {}", email, e.getMessage());
                throw new RuntimeException("Email send failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean supports(ChannelSendContext context) {
        return "email".equals(context.getChannel());
    }
}
