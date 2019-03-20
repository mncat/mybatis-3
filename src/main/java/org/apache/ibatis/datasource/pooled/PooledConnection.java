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
package org.apache.ibatis.datasource.pooled;

import org.apache.ibatis.reflection.ExceptionUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author Clinton Begin
 * 这个类使用了动态代理封装了真正的数据库连接对象，
 * 可以理解为PooledConnection对数据库连接对象进行了增强，这里使用了代理模式，从他实现了InvocationHandler接口也能够看出来
 * 这里的增强主要在于获取连接和归还连接的动作，通过代理之后，归还连接并不是真正的将连接销毁，而且放到对应的连接池中(队列)。
 */
class PooledConnection implements InvocationHandler {

    private static final String CLOSE = "close";
    private static final Class<?>[] IFACES = new Class<?>[]{Connection.class};

    private int hashCode = 0;
    //数据源
    private PooledDataSource dataSource;
    //里面包装的真正的连接对象
    private Connection realConnection;
    //代理连接对象
    private Connection proxyConnection;
    private long checkoutTimestamp;
    //连接对象创建的时间戳
    private long createdTimestamp;
    //连接对象最近一次被使用的时间戳
    private long lastUsedTimestamp;
    //连接类型码
    private int connectionTypeCode;
    //连接是否有效
    private boolean valid;

    /*
     * Constructor for SimplePooledConnection that uses the Connection and PooledDataSource passed in
     *
     * @param connection - the connection that is to be presented as a pooled connection
     * @param dataSource - the dataSource that the connection is from
     */
    public PooledConnection(Connection connection, PooledDataSource dataSource) {
        this.hashCode = connection.hashCode();
        this.realConnection = connection;
        this.dataSource = dataSource;
        this.createdTimestamp = System.currentTimeMillis();
        this.lastUsedTimestamp = System.currentTimeMillis();
        this.valid = true;
        this.proxyConnection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), IFACES, this);
    }

    /**
     * Invalidates the connection
     * 让连接无效
     */
    public void invalidate() {
        valid = false;
    }

    /**
     * Method to see if the connection is usable
     *
     * @return True if the connection is usable
     * 判断连接是否可用，标志为true并且不为null，并且可以ping通服务器则认为可用
     */
    public boolean isValid() {
        return valid && realConnection != null && dataSource.pingConnection(this);
    }

    /**
     * Getter for the *real* connection that this wraps
     * 返回真正的连接
     *
     * @return The connection
     */
    public Connection getRealConnection() {
        return realConnection;
    }

    /**
     * Getter for the proxy for the connection
     * 返回代理连接
     *
     * @return The proxy
     */
    public Connection getProxyConnection() {
        return proxyConnection;
    }

    /**
     * Gets the hashcode of the real connection (or 0 if it is null)
     * 返回真正连接的hashCode
     *
     * @return The hashcode of the real connection (or 0 if it is null)
     */
    public int getRealHashCode() {
        return realConnection == null ? 0 : realConnection.hashCode();
    }

    /**
     * Getter for the connection type (based on url + user + password)
     * 返回连接类型代码
     *
     * @return The connection type
     */
    public int getConnectionTypeCode() {
        return connectionTypeCode;
    }

    /**
     * Setter for the connection type
     * 设置连接类型码
     *
     * @param connectionTypeCode - the connection type
     */
    public void setConnectionTypeCode(int connectionTypeCode) {
        this.connectionTypeCode = connectionTypeCode;
    }

    /**
     * Getter for the time that the connection was created
     * 返回连接的创建时间
     *
     * @return The creation timestamp
     */
    public long getCreatedTimestamp() {
        return createdTimestamp;
    }

    /**
     * Setter for the time that the connection was created
     * 设置连接的创建时间
     *
     * @param createdTimestamp - the timestamp
     */
    public void setCreatedTimestamp(long createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    /**
     * Getter for the time that the connection was last used
     * 获取连接最后一次被使用的时间戳
     *
     * @return - the timestamp
     */
    public long getLastUsedTimestamp() {
        return lastUsedTimestamp;
    }

    /**
     * Setter for the time that the connection was last used
     * 设置连接最后一次被使用的时间戳
     *
     * @param lastUsedTimestamp - the timestamp
     */
    public void setLastUsedTimestamp(long lastUsedTimestamp) {
        this.lastUsedTimestamp = lastUsedTimestamp;
    }

    /**
     * Getter for the time since this connection was last used
     * 获取连接最后一次被使用到现在的时长
     *
     * @return - the time since the last use
     */
    public long getTimeElapsedSinceLastUse() {
        return System.currentTimeMillis() - lastUsedTimestamp;
    }

    /**
     * Getter for the age of the connection
     * 获取连接创建到现在过去的时长
     *
     * @return the age
     */
    public long getAge() {
        return System.currentTimeMillis() - createdTimestamp;
    }

    /**
     * Getter for the timestamp that this connection was checked out
     * 获取连接被检查的时间
     *
     * @return the timestamp
     */
    public long getCheckoutTimestamp() {
        return checkoutTimestamp;
    }

    /**
     * Setter for the timestamp that this connection was checked out
     * 设置连接被检查的时间
     *
     * @param timestamp the timestamp
     */
    public void setCheckoutTimestamp(long timestamp) {
        this.checkoutTimestamp = timestamp;
    }

    /**
     * Getter for the time that this connection has been checked out
     * 获取连接被检查到现在过去的时长
     *
     * @return the time
     */
    public long getCheckoutTime() {
        return System.currentTimeMillis() - checkoutTimestamp;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    /**
     * Allows comparing this connection to another
     *
     * @param obj - the other connection to test for equality
     * @see Object#equals(Object)
     * 判断两个连接是否为一个连接
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PooledConnection) {
            return realConnection.hashCode() == (((PooledConnection) obj).realConnection.hashCode());
        } else if (obj instanceof Connection) {
            return hashCode == obj.hashCode();
        } else {
            return false;
        }
    }

    /**
     * Required for InvocationHandler implementation.
     *
     * @param proxy  - not used
     * @param method - the method to be executed
     * @param args   - the parameters to be passed to the method
     * @see java.lang.reflect.InvocationHandler#invoke(Object, java.lang.reflect.Method, Object[])
     * 总结代理模式对真正的连接对象所做的增强：
     * 1.如果是调用close方法，那么就把连接回收
     * 2.如果调用的不是close，并且是自己第一的方法(不是Object类定义的方法)，那么就在调用这个方法之前检查下连接是否合法，合法再调用这个方法，不合法就抛异常
     * 3.如果方法是Object定义的，那么就不检查是否合法，直接调用
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        //1.对于PooledDataSource
        if (CLOSE.hashCode() == methodName.hashCode() && CLOSE.equals(methodName)) {
            dataSource.pushConnection(this);
            return null;
        } else {
            try {
                //2.如果不是close方法，也不是Object类定义的方法，那么在执行这个方法之前检查下连接还是否合法，只通过valid检查，
                //如果不合法的话，就跑出异常了，
                if (!Object.class.equals(method.getDeclaringClass())) {
                    // issue #579 toString() should never fail
                    // throw an SQLException instead of a Runtime
                    checkConnection();
                }
                //3.如果是Object类定义的方法，那么就直接执行，比如toString方法，不会检查valid，总会成功的。
                return method.invoke(realConnection, args);
            } catch (Throwable t) {
                throw ExceptionUtil.unwrapThrowable(t);
            }
        }
    }

    private void checkConnection() throws SQLException {
        if (!valid) {
            throw new SQLException("Error accessing PooledConnection. Connection is invalid.");
        }
    }

}
