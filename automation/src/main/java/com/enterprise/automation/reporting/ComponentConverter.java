package com.enterprise.automation.reporting;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** {@code %component} in {@code logback.xml}: the {@link LogComponent} of the logger (UI, API, DB...). */
public class ComponentConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return LogComponent.of(event.getLoggerName());
    }
}
