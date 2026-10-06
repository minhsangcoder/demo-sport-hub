package com.sporthub.identity.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

public class GoogleOAuthConfiguredCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        return StringUtils.hasText(environment.getProperty("GOOGLE_CLIENT_ID"))
                && StringUtils.hasText(environment.getProperty("GOOGLE_CLIENT_SECRET"));
    }
}
