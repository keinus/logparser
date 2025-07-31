package org.keinus.logparser.dispatch;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keinus.logparser.config.TransformConfig;
import org.keinus.logparser.interfaces.ITransform;


public class TransformService {
    private static final Logger LOGGER = LoggerFactory.getLogger( TransformService.class );

    private Map<String, List<ITransform>> transformer = new HashMap<>();

    private ITransform loadLibrary(String calssName) {
        String className = "org.keinus.logparser.transform." + calssName;
        Class<?> testClass;
        try {
            testClass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            LOGGER.error(className + " not found", e);
            return null;
        }
        if (testClass == null || !ITransform.class.isAssignableFrom(testClass)) {
            LOGGER.error("{} is not a valid transform class", className);
            return null;
        }
        ITransform transformInterface;
        try {
            transformInterface = (ITransform) testClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            LOGGER.error("{} can not call instantiated", className);
            return null;
        }
        if (transformInterface == null || !ITransform.class.isAssignableFrom(transformInterface.getClass())) {
            LOGGER.error("{} is not a valid transform class", className);
            return null;
        }
        return transformInterface;
    }

    public TransformService(List<TransformConfig> transformList) {
        for(TransformConfig trans : transformList) {
            ITransform transformInterface = loadLibrary(trans.getType());
            if(transformInterface == null)
               continue;
            transformInterface.init(trans.getParam());
            var msgType = trans.getMessagetype();
            transformer.computeIfAbsent(msgType, k -> new ArrayList<>());
            transformer.get(msgType).add(transformInterface);
            LOGGER.info("Message Parser registerd {}", trans.getType());
        }
    }

    public boolean transform(Map<String, Object> parsedStr, String type) {
        for(ITransform trans : transformer.getOrDefault(type, new ArrayList<>())) {
            var ret = trans.parse(parsedStr);
            if(ret.isEmpty())
                return false;
        }
        return true;
    }
}
