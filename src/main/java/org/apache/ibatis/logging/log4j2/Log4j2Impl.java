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
package org.apache.ibatis.logging.log4j2;

import org.apache.ibatis.logging.Log;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.spi.AbstractLogger;

/**
 * @author Eduardo Macarron
 */
public class Log4j2Impl implements Log {

    private Log log;

    /**
     * 绑定log4j2之后，会将这个构造方法放到LogFactory的logConstructor里面，使用的时候通过LogFactory.getLog
     * 方法创建日志实例的时候，就会通过构造方法logConstructor.newInstance创建实例返回，就是使用这段构造方法创建实例
     * */
    public Log4j2Impl(String clazz) {
        Logger logger = LogManager.getLogger(clazz);

        if (logger instanceof AbstractLogger) {
            log = new Log4j2AbstractLoggerImpl((AbstractLogger) logger);
        } else {
            log = new Log4j2LoggerImpl(logger);
        }
    }

    /**
     * 实现Log接口，并重写对应的方法，在方法内部调用的是slf4j的日志实现，
     * 我们最初说过mybatis的Log接口只是定义了自己想要的功能而已，功能的实
     * 现自己并不会去做，而是绑定第三方日志组件之后交由第三方组件去做，这里
     * 看的很清楚了，就是交给slf4j组件去做。
     */
    @Override
    public boolean isDebugEnabled() {
        return log.isDebugEnabled();
    }

    @Override
    public boolean isTraceEnabled() {
        return log.isTraceEnabled();
    }

    @Override
    public void error(String s, Throwable e) {
        log.error(s, e);
    }

    @Override
    public void error(String s) {
        log.error(s);
    }

    @Override
    public void debug(String s) {
        log.debug(s);
    }

    @Override
    public void trace(String s) {
        log.trace(s);
    }

    @Override
    public void warn(String s) {
        log.warn(s);
    }

}
