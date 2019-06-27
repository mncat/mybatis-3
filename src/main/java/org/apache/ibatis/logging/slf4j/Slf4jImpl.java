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
package org.apache.ibatis.logging.slf4j;

import org.apache.ibatis.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.spi.LocationAwareLogger;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class Slf4jImpl implements Log {

    //1.这里的Log是用于传入适配者
    //这里有点特殊，一般来说适配者不一定实现了目标接口，比如第三方支付的时候第三方支付接口和自己使用的目标接口一般就不一样，
    //因此这里持有的就是第三方的接口，但是在这里Slf4jLocationAwareLoggerImpl和Slf4jLoggerImpl这两个适配者也实现了Log
    //接口，因此就统一起来了，不管是哪一个适配者都是用同一个接口来接收，其实即便Slf4jLocationAwareLoggerImpl没有实现Log
    //接口也没有问题，他实现了A接口，这里用A接收就好了，调用的时候转换调用它的接口即可，整体来看就是适配器模式的经典运用
    private Log log;

    //2.通过构造方法传入是配置，对于slf4j来说，这里有2中可能，因此适配者有2种可能(看注释好像和版本有关)
    //分别对应Slf4jLocationAwareLoggerImpl和Slf4jLoggerImpl
    public Slf4jImpl(String clazz) {
        Logger logger = LoggerFactory.getLogger(clazz);

        //3.大于1.6的版本，适配者是Slf4jLocationAwareLoggerImpl
        if (logger instanceof LocationAwareLogger) {
            try {
                // check for slf4j >= 1.6 method signature
                logger.getClass().getMethod("log", Marker.class, String.class, int.class, String.class, Object[].class, Throwable.class);
                log = new Slf4jLocationAwareLoggerImpl((LocationAwareLogger) logger);
                return;
            } catch (SecurityException e) {
                // fail-back to Slf4jLoggerImpl
            } catch (NoSuchMethodException e) {
                // fail-back to Slf4jLoggerImpl
            }
        }

        // Logger is not LocationAwareLogger or slf4j version < 1.6
        //4.小于1.6的版本，适配者是Slf4jLoggerImpl
        log = new Slf4jLoggerImpl(logger);
    }

    /**
     * Slf4jImpl是适配器，因此
     * 1.实现Log接口，并重写对应的方法，便于对外调用
     * 2.在方法内部调用的是slf4j的日志实现，是Slf4jLoggerImpl或者Slf4jLocationAwareLoggerImpl
     * 我们最初说过Mybatis的Log接口只是定义了自己想要的功能而已，功能的实
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
