package org.keinus.logparser.util;

import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import java.io.IOException;
import java.util.Properties;

public class YamlPropertySourceFactory implements PropertySourceFactory {
    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource)
            throws IOException {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        if (resource.getResource().getFilename() != null) {
            factory.setResources(resource.getResource());
            factory.setResources(resource.getResource());
            Properties properties = factory.getObject();
            String filename = resource.getResource().getFilename();
            if (filename != null && properties != null) {
                return new PropertiesPropertySource(filename, properties);
            } else {
                throw new IOException("Failed to load properties from resource: " + resource.getResource());
            }
        } else {
            throw new IOException("Resource not found: " + resource.getResource());
        }

    }
}
