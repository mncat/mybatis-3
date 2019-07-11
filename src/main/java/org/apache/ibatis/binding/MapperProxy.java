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
package org.apache.ibatis.binding;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.SqlSession;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * MapperProxy实现了InvocationHandler接口，是Mapper接口的代理，对接口功能进行了增强
 */
public class MapperProxy<T> implements InvocationHandler, Serializable {

    private static final long serialVersionUID = -6424540398559729838L;
    //1.关联的SqlSession对象
    private final SqlSession sqlSession;
    //2.Mapper接口对应的class对象
    private final Class<T> mapperInterface;
    //3.key是Mapper接口中对应的Method对象，value是MapperMethod，MapperMethod不存储任何信息，因此可以在多个代理对象之间共享
    private final Map<Method, MapperMethod> methodCache;

    public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethod> methodCache) {
        this.sqlSession = sqlSession;
        this.mapperInterface = mapperInterface;
        this.methodCache = methodCache;
    }

    /**
     * ItemMapper mapper = sqlSession.getMapper(ItemMapper.class);
     *  int rowAffected =  mapper.deleteItemById(1);
     * 从代理对象的invoke方法我们可以看到，当我们显示调用类似于上面的mapper.xx方法的时候，底层是调用
     * mapperMethod.execute(sqlSession, args);mapperMethod里面封装了接口方法和sql语句的信息，实际上
     * 会去执行关联的sql语句
     * */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //1.如果是Object类的方法，那么就直接调用即可
        if (Object.class.equals(method.getDeclaringClass())) {
            try {
                return method.invoke(this, args);
            } catch (Throwable t) {
                throw ExceptionUtil.unwrapThrowable(t);
            }
        }
        //2.获取缓存的MapperMethod映射方法，缓存中没有则会创建一个并加到缓存
        final MapperMethod mapperMethod = cachedMapperMethod(method);
        //3.执行sql(MapperMethod内部包含接口方法和参数，sql等信息，可以直接执行sql)
        return mapperMethod.execute(sqlSession, args);
    }

    private MapperMethod cachedMapperMethod(Method method) {
        //1.如果缓存有则直接返回，如果没有就根据接口信息和配置文件信息生成MapperMethod
        MapperMethod mapperMethod = methodCache.get(method);
        if (mapperMethod == null) {
            mapperMethod = new MapperMethod(mapperInterface, method, sqlSession.getConfiguration());
            methodCache.put(method, mapperMethod);
        }
        return mapperMethod;
    }
}
