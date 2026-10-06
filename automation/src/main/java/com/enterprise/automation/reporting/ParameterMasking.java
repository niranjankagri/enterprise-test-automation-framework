package com.enterprise.automation.reporting;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Masks test parameters that hold secrets, by parameter name ({@code password}, {@code token},
 * {@code secret}...). Needs the {@code -parameters} compiler flag (set in the parent POM), which
 * keeps real parameter names in the bytecode.
 */
public final class ParameterMasking {

    private ParameterMasking() {
    }

    /** Whether a parameter name suggests a secret (password, token, secret, apiKey). */
    public static boolean isSecret(String parameterName) {
        // Same rule as everywhere else: one definition of "secret" for logs, reports and parameters
        return SecretMasker.isSecretName(parameterName);
    }

    /** A copy of {@code values} with the secret parameters of {@code method} replaced by {@code ****}. */
    public static Object[] mask(Method method, Object[] values) {
        if (values == null) {
            return new Object[0];
        }
        // A copy: TestNG still passes the real values to the test method
        Object[] masked = values.clone();
        // Names are real (e.g. "password") because of the -parameters compiler flag
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < masked.length && i < parameters.length; i++) {
            if (isSecret(parameters[i].getName()) && masked[i] != null) {
                masked[i] = "****";
            }
        }
        return masked;
    }
}
