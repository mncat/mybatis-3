/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.datasource.unpooled;

import org.apache.ibatis.datasource.DataSourceException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * @author Clinton Begin
 * 工厂模式，创建UnpooledDataSource的工厂，工厂继承DataSourceFactory
 * 整体逻辑和创建PooledDataSource是一样的，因此PooledDataSource直接继承复用
 */
public class UnpooledDataSourceFactory implements DataSourceFactory {

    //配置前缀
    private static final String DRIVER_PROPERTY_PREFIX = "driver.";
    //配置前缀长度
    private static final int DRIVER_PROPERTY_PREFIX_LENGTH = DRIVER_PROPERTY_PREFIX.length();

    //Factory内部包含的数据源，可以是UnpooledDataSource或者PooledDataSource
    protected DataSource dataSource;

    /**
     * 将UnpooledDataSource传到工厂里面来
     */
    public UnpooledDataSourceFactory() {
        this.dataSource = new UnpooledDataSource();
    }

    /**
     * 设置属性的方法
     */
    @Override
    public void setProperties(Properties properties) {
        Properties driverProperties = new Properties();
        //1.获取dataSource的MetaObject，便于修改属性，这里可以参考反射模块
        MetaObject metaDataSource = SystemMetaObject.forObject(dataSource);
        //2.遍历所有属性
        for (Object key : properties.keySet()) {
            String propertyName = (String) key;
            //3.如果属性以driver.开头，那么就设置到driverProperties属性对象里面去
            if (propertyName.startsWith(DRIVER_PROPERTY_PREFIX)) {
                String value = properties.getProperty(propertyName);
                driverProperties.setProperty(propertyName.substring(DRIVER_PROPERTY_PREFIX_LENGTH), value);
            } else if (metaDataSource.hasSetter(propertyName)) {
                //4.如果不是以driver.开头，就检查是否有这个属性的setter方法，如果有就进一步去设置，没有就需要抛异常，底层由反射模块封装实现
                String value = (String) properties.get(propertyName);
                //5.进行必要的属性值类型转换，比如转换成整型或者Long类型等
                Object convertedValue = convertValue(metaDataSource, propertyName, value);
                //6.设置值，key不变，但是value是转换后的
                metaDataSource.setValue(propertyName, convertedValue);
            } else {
                //3.前缀不对也没有set方法，属于未知属性，抛异常
                throw new DataSourceException("Unknown DataSource property: " + propertyName);
            }
        }
        //4.将处理后得到的属性对象设置到UnpooledDataSource的driverProperties属性
        if (driverProperties.size() > 0) {
            metaDataSource.setValue("driverProperties", driverProperties);
        }
    }

    //获取数据源
    @Override
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * 将需要设置的key对应的value的属性进行转换
     */
    private Object convertValue(MetaObject metaDataSource, String propertyName, String value) {
        Object convertedValue = value;
        Class<?> targetType = metaDataSource.getSetterType(propertyName);
        if (targetType == Integer.class || targetType == int.class) {
            convertedValue = Integer.valueOf(value);
        } else if (targetType == Long.class || targetType == long.class) {
            convertedValue = Long.valueOf(value);
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            convertedValue = Boolean.valueOf(value);
        }
        return convertedValue;
    }

}
