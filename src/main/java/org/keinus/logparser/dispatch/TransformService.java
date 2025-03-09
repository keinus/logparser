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

    public TransformService(List<TransformConfig> transformList) {
        if(transformList != null) {
            for(TransformConfig trans : transformList) {
                String className = "org.keinus.logparser.transform." + trans.getType();
                Class<?> testClass;
                try {
                    testClass = Class.forName(className);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                    continue;
                }
                ITransform transformInterface;
                try {
                    transformInterface = (ITransform) testClass.getDeclaredConstructor().newInstance();
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    e.printStackTrace();
                    continue;
                }
                transformInterface.init(trans.getParam());
                var typeList = trans.getMessagetype().split(",");
                for(String type : typeList) {
                    if(transformer.get(type) == null) {
                        transformer.put(type, new ArrayList<>());
                    } 
                    transformer.get(type).add(transformInterface);
                }
                
                LOGGER.info("Message Parser registerd {}", className);
            }
        }
    }

    public boolean transform(Map<String, Object> parsedStr, String type) {
        List<ITransform> transList = transformer.get(type);
        for(ITransform trans : transList) {
            var ret = trans.parse(parsedStr);
            if(ret == null)
                return false;
        }
        return true;
    }
}
