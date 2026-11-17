package com.coplay.user.fake;

import com.coplay.user.api.MailService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory mail outbox used by tests.
 */
public final class FakeMailService implements MailService {

    private final List<Mail> mails = new CopyOnWriteArrayList<>();

    @Override
    public void sendVerificationCode(String email, String code) {
        mails.add(new Mail(email, code));
    }

    public List<Mail> mails() {
        return List.copyOf(mails);
    }

    public int sentCount() {
        return mails.size();
    }

    public String lastCode(String email) {
        return mails.stream()
                .filter(mail -> mail.email().equals(email))
                .reduce((first, second) -> second)
                .map(Mail::code)
                .orElseThrow(() -> new IllegalStateException("No verification mail sent to " + email));
    }

    public record Mail(String email, String code) {
    }
}
