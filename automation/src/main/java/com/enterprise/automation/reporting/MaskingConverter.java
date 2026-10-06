package com.enterprise.automation.reporting;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback's {@code %msg} with secrets masked: declared as {@code %maskedMsg} in {@code logback.xml}
 * and used by the console and the file log, so no log line can carry a password or token,
 * whoever wrote it (framework, test or a library).
 */
public class MaskingConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SecretMasker.mask(super.convert(event));
    }
}
