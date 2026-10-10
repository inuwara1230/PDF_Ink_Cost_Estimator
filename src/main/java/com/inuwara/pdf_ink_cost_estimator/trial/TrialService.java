package com.inuwara.pdf_ink_cost_estimator.trial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.prefs.Preferences;

@Service
public class TrialService {

    public record Status(boolean expired, long daysLeft) {}
    private record Entry(long first, long last) {}

    private static final Logger log = LoggerFactory.getLogger(TrialService.class);
    private static final Status EXPIRED = new Status(true, 0);

    private static final double MAX_DAYS = 30;
    private static final String SECRET = "CHANGE-ME-to-a-long-random-string";
    private static final long ROLLBACK_TOLERANCE_SECONDS = 3600;   // clock set back > 1 hour = expired
    private static final long REFRESH_NANOS = 60_000_000_000L;     // re-check at most once a minute
    private static final String PREF_NODE = "pdfcostestimator";
    private static final String PREF_KEY = "trial";

    private final long trialSeconds;
    private final Path file;
    private final Preferences prefs = Preferences.userRoot().node(PREF_NODE);

    private Status cached;
    private long cachedAtNanos;


    public TrialService(@Value("${trial.days:30}") double configuredDays) {
        double days = Math.max(0, Math.min(configuredDays, MAX_DAYS));
        this.trialSeconds = Math.round(days * 86400);
        String base = System.getenv("APPDATA");
        Path dir = Paths.get(base != null ? base : System.getProperty("user.home"), "PDFCostEstimator");
        this.file = dir.resolve("trial.dat");
    }

    public synchronized Status check() {
        long nowNanos = System.nanoTime();
        if (cached != null && (cached.expired() || nowNanos - cachedAtNanos < REFRESH_NANOS)) {
            return cached;                       // an expired trial stays expired until restart
        }
        cached = compute(Instant.now().getEpochSecond());
        cachedAtNanos = nowNanos;
        return cached;
    }

    private Status compute(long now) {
        try {
            String rawFile = readFile();
            String rawReg = readRegistry();
            Entry f = parse(rawFile);
            Entry r = parse(rawReg);

            if (f == null && r == null) {
                if (rawFile == null && rawReg == null) {         // very first run
                    if (!save(now, now)) log.warn("Could not save the trial start date anywhere");
                    return statusFor(now, now);
                }
                return EXPIRED;                                   // data exists but is damaged
            }

            long first = Long.MAX_VALUE, last = 0;
            if (f != null) { first = Math.min(first, f.first()); last = Math.max(last, f.last()); }
            if (r != null) { first = Math.min(first, r.first()); last = Math.max(last, r.last()); }

            if (now < last - ROLLBACK_TOLERANCE_SECONDS) return EXPIRED;   // clock set back

            long newLast = Math.max(last, now);
            boolean copiesDiffer = f == null || r == null
                    || f.first() != r.first() || f.last() != r.last();
            if (copiesDiffer || newLast - last >= 60) save(first, newLast);

            return statusFor(first, now);

        } catch (Exception e) {
            log.error("Trial check failed", e);
            return new Status(false, (trialSeconds + 86399) / 86400);                  // our bug should not lock anyone out
        }
    }

    private Status statusFor(long first, long now) {
        long remaining = trialSeconds - Math.max(0, now - first);
        if (remaining <= 0) return EXPIRED;
        return new Status(false, (remaining + 86399) / 86400);   // rounds up to whole days
    }

    private String readFile() {
        try {
            return Files.exists(file) ? Files.readString(file).trim() : null;
        } catch (IOException e) {
            return "unreadable";                                  // present but unusable
        }
    }

    private String readRegistry() {
        try {
            return prefs.get(PREF_KEY, null);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean save(long first, long last) {
        String data = first + "|" + last;
        String value = data + "|" + sign(data);
        boolean ok = false;
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("trial.dat.tmp");     // write then replace: a power cut can't corrupt it
            Files.writeString(tmp, value);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            ok = true;
        } catch (IOException e) {
            log.warn("Could not write trial file", e);
        }
        try {
            prefs.put(PREF_KEY, value);
            prefs.flush();
            ok = true;
        } catch (Exception e) {
            log.warn("Could not write trial registry entry", e);
        }
        return ok;
    }

    private Entry parse(String raw) {
        if (raw == null) return null;
        try {
            String[] p = raw.split("\\|");
            if (p.length != 3) return null;
            String expected = sign(p[0] + "|" + p[1]);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                                       p[2].getBytes(StandardCharsets.UTF_8))) return null;
            return new Entry(Long.parseLong(p[0]), Long.parseLong(p[1]));
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}