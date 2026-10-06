package com.enterprise.automation.data;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import net.datafaker.Faker;

/**
 * Realistic, unique test values.
 *
 * <p>Uniqueness: every value that must be unique (email, SKU, username) carries a suffix made of
 * a run id (start time of this JVM, base 36) and a counter shared by all threads. Two parallel
 * tests, or two runs against the same environment, never produce the same email.
 *
 * <p>Realism: names and cities come from Datafaker; one {@link Faker} per thread, because Faker
 * is not documented as thread-safe.
 */
public final class RandomDataGenerator {

    private static final String RUN_ID = Long.toString(System.currentTimeMillis(), 36);
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ThreadLocal<Faker> FAKER = ThreadLocal.withInitial(() -> new Faker(Locale.US));

    private RandomDataGenerator() {
    }

    /** A suffix unique in this run and across runs, e.g. {@code mfx3k2q1-7}. */
    public static String uniqueSuffix() {
        return RUN_ID + "-" + COUNTER.incrementAndGet();
    }

    public static String firstName() {
        return lettersOnly(FAKER.get().name().firstName());
    }

    public static String lastName() {
        return lettersOnly(FAKER.get().name().lastName());
    }

    public static String city() {
        return FAKER.get().address().city();
    }

    /** Unique, valid email on a reserved test domain, e.g. {@code maria.lopez.mfx3k2q1-7@test.example.com}. */
    public static String email(String firstName, String lastName) {
        return (firstName + "." + lastName).toLowerCase(Locale.ROOT) + "." + uniqueSuffix() + "@test.example.com";
    }

    /** Phone in the format the application accepts, e.g. {@code +1-555-0142}. */
    public static String phone() {
        return "+1-555-" + String.format(Locale.ROOT, "%04d", ThreadLocalRandom.current().nextInt(10_000));
    }

    /** Unique SKU, e.g. {@code TST-MFX3K2Q1-7}. */
    public static String sku() {
        return "TST-" + uniqueSuffix().toUpperCase(Locale.ROOT);
    }

    public static String productName() {
        return FAKER.get().commerce().productName();
    }

    /** Unique login name, e.g. {@code user-mfx3k2q1-7}. */
    public static String username() {
        return "user-" + uniqueSuffix();
    }

    /** Names such as "O'Connor" are kept readable but limited to letters, so emails stay valid. */
    private static String lettersOnly(String value) {
        String letters = value.replaceAll("[^A-Za-z]", "");
        return letters.isEmpty() ? "Test" : letters;
    }
}
