package org.keinus.logparser.domain.service.transform;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.keinus.logparser.domain.transformation.service.ConditionEvaluator;

public class ConditionEvaluatorTest {

    @Test
    void testExpressionCaching() {
        ConditionEvaluator evaluator = new ConditionEvaluator();
        Map<String, Object> data = new HashMap<>();
        data.put("port", 80);

        String expression = "['port'] == 80";

        // First call - parses and caches
        boolean result1 = evaluator.evaluate(expression, data);
        assertTrue(result1);

        // Second call - uses cache (hard to verify internally without reflection, but functional test)
        // If logic was broken, it might fail or throw exception.
        boolean result2 = evaluator.evaluate(expression, data);
        assertTrue(result2);
        
        // Performance test (micro-benchmark style)
        long start = System.nanoTime();
        for(int i=0; i<10000; i++) {
            evaluator.evaluate(expression, data);
        }
        long end = System.nanoTime();
        System.out.println("10k evaluations took: " + (end - start) / 1000000.0 + " ms");
    }
}
