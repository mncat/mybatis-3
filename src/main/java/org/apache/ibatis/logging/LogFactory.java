/**
 * Copyright 2009-2016 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.logging;

import java.lang.reflect.Constructor;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public final class LogFactory {

    /**
     * Marker to be used by logging implementations that support markers
     * 给支持marker功能的logger使用(目前有slf4j, log4j2)
     */
    public static final String MARKER = "MYBATIS";


    /**
     * 绑定哪个日志框架，就把这个日志框架所对应logger的构造函数放进来
     */
    private static Constructor<? extends Log> logConstructor;

    /**
     * 1.静态代码块，用来完成Mybatis和第三方日志框架的绑定过程
     * 2.优先级别是 slf4j > common logging > log4j2 > log4j > jdk logging > 没有日志
     * 3.执行逻辑是：按照优先级别的顺序，依次尝试绑定对应的日志组件，一旦绑定成功，后面的就不会再执行了。我们看tryImplementation方法，
     *   tryImplementation方法首先会判断logConstructor是否为空，为空则尝试绑定，不为空就什么都不做。
     *   假如第一次进来绑定slf4j，logConstructor肯定为空，那么在useSlf4jLogging方法的逻辑里面就会将slf4j的构造方法放到logConstructor里面去，
     *   后面再执行common logging的绑定流程时发现logConstructor不为空，说明前面已经成功初始化了，就不会执行了；
     *   反过来假如slf4j绑定失败，比如依赖包没有或者版本之类的报错，那么setImplementation抛出异常，在tryImplementation里面捕获到异常之后会直接
     *   忽略，然后就继续尝试绑定common logging,直到成功。这就是绑定的整体流程。
     * */
    static {

        tryImplementation(new Runnable() {
            @Override
            public void run() {
                useSlf4jLogging();
            }
        });
        tryImplementation(new Runnable() {
            @Override
            public void run() {
                useCommonsLogging();
            }
        });
        tryImplementation(new Runnable() {
            @Override
            public void run() {
                useLog4J2Logging();
            }
        });
        tryImplementation(new Runnable() {
            @Override
            public void run() {
                useLog4JLogging();
            }
        });
        tryImplementation(new Runnable() {
            @Override
            public void run() {
                useJdkLogging();
            }
        });
        tryImplementation(new Runnable() {
            @Override
            public void run() {
                useNoLogging();
            }
        });
    }

    private LogFactory() {
        // disable construction
    }

    /**
     * 对外提供2种获取日志实例的方法，类似于Slf4j的LoggerFactory.getLogger(XXX.class);
     */
    public static Log getLog(Class<?> aClass) {
        return getLog(aClass.getName());
    }

    public static Log getLog(String logger) {
        try {
            return logConstructor.newInstance(logger);
        } catch (Throwable t) {
            throw new LogException("Error creating logger for logger " + logger + ".  Cause: " + t, t);
        }
    }


    public static synchronized void useCustomLogging(Class<? extends Log> clazz) {
        setImplementation(clazz);
    }

    /**
     * 1.下面的方法都是类似的，对应于前面绑定几种日志组件的情况，就是把对应的类放到setImplementation方法里面去做
     * 具体的绑定细节，细节的处理流程时一样的。
     * 往下优先级降低
     */
    public static synchronized void useSlf4jLogging() {
        setImplementation(org.apache.ibatis.logging.slf4j.Slf4jImpl.class);
    }

    //2
    public static synchronized void useCommonsLogging() {
        setImplementation(org.apache.ibatis.logging.commons.JakartaCommonsLoggingImpl.class);
    }

    //4
    public static synchronized void useLog4JLogging() {
        setImplementation(org.apache.ibatis.logging.log4j.Log4jImpl.class);
    }

    //3
    public static synchronized void useLog4J2Logging() {
        setImplementation(org.apache.ibatis.logging.log4j2.Log4j2Impl.class);
    }

    //5
    public static synchronized void useJdkLogging() {
        setImplementation(org.apache.ibatis.logging.jdk14.Jdk14LoggingImpl.class);
    }

    //这个好像是测试用的，没看到代码中使用了
    public static synchronized void useStdOutLogging() {
        setImplementation(org.apache.ibatis.logging.stdout.StdOutImpl.class);
    }

    //6
    public static synchronized void useNoLogging() {
        setImplementation(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
    }

    /**
     * 所有尝试绑定的动作都会走这个方法，如果已经有绑定的了，就不会再尝试绑定了
     */
    private static void tryImplementation(Runnable runnable) {
        if (logConstructor == null) {
            try {
                runnable.run();
            } catch (Throwable t) {
                // ignore
            }
        }
    }

    /**
     * 绑定的细节
     */
    private static void setImplementation(Class<? extends Log> implClass) {
        try {
            //1.获取绑定类的构造方法
            Constructor<? extends Log> candidate = implClass.getConstructor(String.class);
            //2.通过构造方法插件一个实例赋值给Log，后面我们会发现传进来的类都是Log接口的子类，因此是一个多态的写法
            Log log = candidate.newInstance(LogFactory.class.getName());
            //3.这里第2步的赋值只是为了在这里打印日志
            if (log.isDebugEnabled()) {
                log.debug("Logging initialized using '" + implClass + "' adapter.");
            }
            //3.把绑定的日志组件的构造方法放到logConstructor里面，后面就不会再尝试绑定其他的日志组件了
            logConstructor = candidate;
        } catch (Throwable t) {
            throw new LogException("Error setting Log implementation.  Cause: " + t, t);
        }
    }

}
