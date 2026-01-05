package org.keinus.logparser.domain.service.transform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

@Service
public class ConditionEvaluator {
    private static final Logger log = LoggerFactory.getLogger(ConditionEvaluator.class);
    private final ExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    public boolean evaluate(String conditionExpression, Map<String, Object> data) {
        if (conditionExpression == null || conditionExpression.trim().isEmpty()) {
            // Empty condition means "Always apply" or "Catch-all".
            return true;
        }

        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariables(data);
            
            // Allow accessing map keys directly as variables is tricky in SpEL without # prefix.
            // But we can set the root object to the map.
            context.setRootObject(data);
            
            // However, SpEL map access is usually ['key'].
            // To support "dst_port == 80", we ideally want property access.
            // StandardEvaluationContext with MapAccessor allows this.
            context.addPropertyAccessor(new org.springframework.context.expression.MapAccessor());

            Expression exp = expressionCache.computeIfAbsent(conditionExpression, 
                key -> parser.parseExpression(key));
                
            Boolean result = exp.getValue(context, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            log.warn("Failed to evaluate condition: '{}' with data: {}", conditionExpression, data, e);
            return false;
        }
    }
}
