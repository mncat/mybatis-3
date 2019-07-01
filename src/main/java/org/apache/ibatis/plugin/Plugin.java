/**
 * Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.plugin;

import org.apache.ibatis.reflection.ExceptionUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 插件的代理类，主要负责：
 * 1.生成插件拦截器对象Interceptor的代理对象(wrap方法)。简化插件开发者自行实现插件代理对象的逻辑,(自行实现的话，需要根
 * 据插件的Intercepts注解里面的信息，根据需要代理的类，方法来插件一个代理对象，这个过程比较繁琐，因此Mybatis简化了这个逻辑，
 * 在wrap方法中帮助我们实现了)
 * 2.插件的调用。插件的调用就是调用interceptor的intercept方法，在invoke方法中实现。invoke调用方法时会判断，方法被拦截的情
 * 况则会调用插件的interceptor方法，方法不需要被拦截那就直接调用目标对象的方法。在Plugin实例创建的时候，就已经把需要拦截的全
 * 部方法保存到signatureMap里面了，因此很容易判断出来。
 * @author Clinton Begin
 */
public class Plugin implements InvocationHandler {

    private Object target;
    private Interceptor interceptor;
    private Map<Class<?>, Set<Method>> signatureMap;

    private Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
        this.target = target;
        this.interceptor = interceptor;
        this.signatureMap = signatureMap;
    }

    /**
     * 返回一个代理增强后的对象，这个对象有以下特点:
     * 1.这个对象内部持有的InvocationHandler是一个Plugin，表面来看是对这个Plugin进行的代理增强，但是实际上Plugin只是
     * 一个壳子，Plugin内部封装了3个信息，一个target代表真正被代理的目标对象，interceptor代表了插件对象，signatureMap
     * 代表了插件要拦截的方法集合（key是四大对象的类对象，value是需要拦截的方法集合）。
     * 2.返回的代理对象真正代理的是target对象，从classLoad和interfaces参数可以知道。
     * 3.这个代理对象内部即持有目标对象target，又持有插件对象interceptor(因为Plugin其实就是对这二者的封装)
     * */
    public static Object wrap(Object target, Interceptor interceptor) {
        //1.signatureMap包含插件需要拦截的全部方法，Class对象为key，方法集合为value
        Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);
        Class<?> type = target.getClass();
        //2.获取目标类的需要被代理的接口。比如type是StatementHandler实现类，它一共实现了100个接口，但是我需要拦截的只有1个接口，
        // 那么只返回1个。为什么呢?因为动态代理生成的代理对象是需要实现这些接口的(目标类实现了什么接口，代理类也要实现对应的接口，
        // 才能实现动态代理)，既然只需要增强1个接口的方法那就只实现这1个接口就好了，没有必要全部实现
        Class<?>[] interfaces = getAllInterfaces(type, signatureMap);
        if (interfaces.length > 0) {
            return Proxy.newProxyInstance(
                    type.getClassLoader(),
                    interfaces,
                    new Plugin(target, interceptor, signatureMap));
        }
        return target;
    }

    /**
     * 代理对象被调用时走的方法。
     * 因为代理对象即持有目标对象(比如StatementHandler或者Executor)，又持有插件和插件要拦截的方法列表，因此
     * 调用方法的时候要判断是不是要拦截这个方法，如果在拦截列表就要。
     * */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            //1.signatureMap是插件的方法集合，先判断需要调用的方法是不是插件的方法。比如method是StatementHandler的一个方法A，那么
            //这里就会是get(StatementHandler.class),如果拦截器声明了拦截了StatementHandler的B，C方法，那么就会获取到这个包含B,C
            //方法的集合，再来判断，发现不包含A，那么就执行最后面的逻辑直接目标对象调用，如果method是B方法，那么就会走if的逻辑，在
            //intercept方法里面调用我们拦截器的逻辑，至于拦截器里面还要不要调用目标方法，这个是在拦截器里面控制的，因此2和3的逻辑是
            //互斥的，并不需要调用2之后还调用3
            Set<Method> methods = signatureMap.get(method.getDeclaringClass());
            //2.这说明是插件的方法，那么就调用插件的
            if (methods != null && methods.contains(method)) {
                return interceptor.intercept(new Invocation(target, method, args));
            }
            //3.如果不是插件的方法，说明是目标对象自己的方法，比如是StatementHandler对象自己的一个方法，直接调用即可
            return method.invoke(target, args);
        } catch (Exception e) {
            throw ExceptionUtil.unwrapThrowable(e);
        }
    }

    /**
     * 获取Interceptor插件对象的方法列表。以Class对象为key，方法集合的set为value保存到Map中。
     * 这个set里面保存的方法是需要插件拦截的方法
     * */
    private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
        Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);
        // issue #251
        //1.插件需要注解，没有注解抛异常
        if (interceptsAnnotation == null) {
            throw new PluginException("No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());
        }
        //2.获取Signature数组
        Signature[] sigs = interceptsAnnotation.value();
        Map<Class<?>, Set<Method>> signatureMap = new HashMap<Class<?>, Set<Method>>();
        //3.遍历处理每一个Signature
        for (Signature sig : sigs) {
            //4.第一次get肯定没有，就初始化里面的方法集合
            Set<Method> methods = signatureMap.get(sig.type());
            if (methods == null) {
                methods = new HashSet<Method>();
                signatureMap.put(sig.type(), methods);
            }
            //5.初始化完了到这一步，会获取对应的类型的全部方法和方法参数，封装成一个Method对象保存到method集合
            try {
                Method method = sig.type().getMethod(sig.method(), sig.args());
                methods.add(method);
            } catch (NoSuchMethodException e) {
                throw new PluginException("Could not find method on " + sig.type() + " named " + sig.method() + ". Cause: " + e, e);
            }
        }
        //6.最后返回的signatureMap就是以Class对象为key，方法列表为value的一个Map，这里的方法都是插件上的注解声明需要拦截的方法
        return signatureMap;
    }

    /**
     *返回type类型的所有方法中，那些需要被拦截的方法
     */
    private static Class<?>[] getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) {
        Set<Class<?>> interfaces = new HashSet<Class<?>>();
        while (type != null) {
            //1.遍历type类型的全部方法，包括父类的方法
            for (Class<?> c : type.getInterfaces()) {
                //2.只有需要拦截的方法，才返回
                if (signatureMap.containsKey(c)) {
                    interfaces.add(c);
                }
            }
            //3.处理完子类，递归处理父类
            type = type.getSuperclass();
        }
        //4.返回
        return interfaces.toArray(new Class<?>[interfaces.size()]);
    }
}
