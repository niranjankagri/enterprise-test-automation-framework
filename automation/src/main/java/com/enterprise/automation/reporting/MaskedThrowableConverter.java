package com.enterprise.automation.reporting;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Preserves stack diagnostics while applying the same secret rules as log messages. */
public class MaskedThrowableConverter extends ThrowableProxyConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return SecretMasker.mask(super.convert(event));
    }
}
