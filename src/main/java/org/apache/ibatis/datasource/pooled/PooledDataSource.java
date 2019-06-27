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
package org.apache.ibatis.datasource.pooled;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * This is a simple, synchronous, thread-safe database connection pool.
 *
 * @author Clinton Begin
 */
public class PooledDataSource implements DataSource {

    private static final Log log = LogFactory.getLog(PooledDataSource.class);

    private final PoolState state = new PoolState(this);

    //真正用于创建连接的数据源
    private final UnpooledDataSource dataSource;

    // OPTIONAL CONFIGURATION FIELDS
    //最大活跃连接数
    protected int poolMaximumActiveConnections = 10;
    //最大空闲连接数
    protected int poolMaximumIdleConnections = 5;
    //最大使用时长
    protected int poolMaximumCheckoutTime = 20000;
    //无法获取连接时最大等待时长
    protected int poolTimeToWait = 20000;
    //测试连接是否有效的sql语句
    protected String poolPingQuery = "NO PING QUERY SET";
    //是否允许测试连接
    protected boolean poolPingEnabled = false;
    //当连接在一段时间未被使用，才允许测试连接是否有效
    protected int poolPingConnectionsNotUsedFor = 0;
    //标示一个连接池，由一个连接池创建的连接都会带上这个
    private int expectedConnectionTypeCode;

    public PooledDataSource() {
        dataSource = new UnpooledDataSource();
    }

    public PooledDataSource(UnpooledDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public PooledDataSource(String driver, String url, String username, String password) {
        dataSource = new UnpooledDataSource(driver, url, username, password);
        expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
    }

    public PooledDataSource(String driver, String url, Properties driverProperties) {
        dataSource = new UnpooledDataSource(driver, url, driverProperties);
        expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
    }

    public PooledDataSource(ClassLoader driverClassLoader, String driver, String url, String username, String password) {
        dataSource = new UnpooledDataSource(driverClassLoader, driver, url, username, password);
        expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
    }

    public PooledDataSource(ClassLoader driverClassLoader, String driver, String url, Properties driverProperties) {
        dataSource = new UnpooledDataSource(driverClassLoader, driver, url, driverProperties);
        expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
    }

    @Override
    public Connection getConnection() throws SQLException {
        return popConnection(dataSource.getUsername(), dataSource.getPassword()).getProxyConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return popConnection(username, password).getProxyConnection();
    }

    @Override
    public void setLoginTimeout(int loginTimeout) throws SQLException {
        DriverManager.setLoginTimeout(loginTimeout);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return DriverManager.getLoginTimeout();
    }

    @Override
    public void setLogWriter(PrintWriter logWriter) throws SQLException {
        DriverManager.setLogWriter(logWriter);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return DriverManager.getLogWriter();
    }

    public void setDriver(String driver) {
        dataSource.setDriver(driver);
        forceCloseAll();
    }

    public void setUrl(String url) {
        dataSource.setUrl(url);
        forceCloseAll();
    }

    public void setUsername(String username) {
        dataSource.setUsername(username);
        forceCloseAll();
    }

    public void setPassword(String password) {
        dataSource.setPassword(password);
        forceCloseAll();
    }

    public void setDefaultAutoCommit(boolean defaultAutoCommit) {
        dataSource.setAutoCommit(defaultAutoCommit);
        forceCloseAll();
    }

    public void setDefaultTransactionIsolationLevel(Integer defaultTransactionIsolationLevel) {
        dataSource.setDefaultTransactionIsolationLevel(defaultTransactionIsolationLevel);
        forceCloseAll();
    }

    public void setDriverProperties(Properties driverProps) {
        dataSource.setDriverProperties(driverProps);
        forceCloseAll();
    }

    /*
     * The maximum number of active connections
     *
     * @param poolMaximumActiveConnections The maximum number of active connections
     */
    public void setPoolMaximumActiveConnections(int poolMaximumActiveConnections) {
        this.poolMaximumActiveConnections = poolMaximumActiveConnections;
        forceCloseAll();
    }

    /*
     * The maximum number of idle connections
     *
     * @param poolMaximumIdleConnections The maximum number of idle connections
     */
    public void setPoolMaximumIdleConnections(int poolMaximumIdleConnections) {
        this.poolMaximumIdleConnections = poolMaximumIdleConnections;
        forceCloseAll();
    }

    /*
     * The maximum time a connection can be used before it *may* be
     * given away again.
     *
     * @param poolMaximumCheckoutTime The maximum time
     */
    public void setPoolMaximumCheckoutTime(int poolMaximumCheckoutTime) {
        this.poolMaximumCheckoutTime = poolMaximumCheckoutTime;
        forceCloseAll();
    }

    /*
     * The time to wait before retrying to get a connection
     *
     * @param poolTimeToWait The time to wait
     */
    public void setPoolTimeToWait(int poolTimeToWait) {
        this.poolTimeToWait = poolTimeToWait;
        forceCloseAll();
    }

    /*
     * The query to be used to check a connection
     *
     * @param poolPingQuery The query
     */
    public void setPoolPingQuery(String poolPingQuery) {
        this.poolPingQuery = poolPingQuery;
        forceCloseAll();
    }

    /*
     * Determines if the ping query should be used.
     *
     * @param poolPingEnabled True if we need to check a connection before using it
     */
    public void setPoolPingEnabled(boolean poolPingEnabled) {
        this.poolPingEnabled = poolPingEnabled;
        forceCloseAll();
    }

    /*
     * If a connection has not been used in this many milliseconds, ping the
     * database to make sure the connection is still good.
     *
     * @param milliseconds the number of milliseconds of inactivity that will trigger a ping
     */
    public void setPoolPingConnectionsNotUsedFor(int milliseconds) {
        this.poolPingConnectionsNotUsedFor = milliseconds;
        forceCloseAll();
    }

    public String getDriver() {
        return dataSource.getDriver();
    }

    public String getUrl() {
        return dataSource.getUrl();
    }

    public String getUsername() {
        return dataSource.getUsername();
    }

    public String getPassword() {
        return dataSource.getPassword();
    }

    public boolean isAutoCommit() {
        return dataSource.isAutoCommit();
    }

    public Integer getDefaultTransactionIsolationLevel() {
        return dataSource.getDefaultTransactionIsolationLevel();
    }

    public Properties getDriverProperties() {
        return dataSource.getDriverProperties();
    }

    public int getPoolMaximumActiveConnections() {
        return poolMaximumActiveConnections;
    }

    public int getPoolMaximumIdleConnections() {
        return poolMaximumIdleConnections;
    }

    public int getPoolMaximumCheckoutTime() {
        return poolMaximumCheckoutTime;
    }

    public int getPoolTimeToWait() {
        return poolTimeToWait;
    }

    public String getPoolPingQuery() {
        return poolPingQuery;
    }

    public boolean isPoolPingEnabled() {
        return poolPingEnabled;
    }

    public int getPoolPingConnectionsNotUsedFor() {
        return poolPingConnectionsNotUsedFor;
    }

    /*
     * Closes all active and idle connections in the pool
     * 强制关闭所有的活跃队列连接和空闲队列连接
     */
    public void forceCloseAll() {
        //1.同步加锁
        synchronized (state) {
            expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
            //2.关闭活跃队列内的连接
            for (int i = state.activeConnections.size(); i > 0; i--) {
                try {
                    PooledConnection conn = state.activeConnections.remove(i - 1);
                    //2.1销毁PooledConnection
                    conn.invalidate();
                    //2.2回滚事物
                    Connection realConn = conn.getRealConnection();
                    if (!realConn.getAutoCommit()) {
                        realConn.rollback();
                    }
                    //2.3关闭realConnection
                    realConn.close();
                } catch (Exception e) {
                    // ignore
                }
            }
            //3.关闭空闲队列内的连接
            for (int i = state.idleConnections.size(); i > 0; i--) {
                try {
                    PooledConnection conn = state.idleConnections.remove(i - 1);
                    //3.1销毁PooledConnection
                    conn.invalidate();
                    //3.2回滚事物
                    Connection realConn = conn.getRealConnection();
                    if (!realConn.getAutoCommit()) {
                        realConn.rollback();
                    }
                    //3.3关闭realConnection
                    realConn.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("PooledDataSource forcefully closed/removed all connections.");
        }
    }

    public PoolState getPoolState() {
        return state;
    }

    private int assembleConnectionTypeCode(String url, String username, String password) {
        return ("" + url + username + password).hashCode();
    }


    /**
     * 数据源归还连接的方法
     */
    protected void pushConnection(PooledConnection conn) throws SQLException {

        synchronized (state) {
            //1.加锁，PooledDataSource是线程安全的数据库连接池
            //2.如果活跃的连接池包含这个连接，则先移除
            state.activeConnections.remove(conn);
            //3.连接合法才有继续操作的必要
            if (conn.isValid()) {
                //4.空闲连接队列未满，并且connectionTypeCode和预期的一致，那么就尝试将连接归还(expectedConnectionTypeCode标识了一个连接池的连接)
                if (state.idleConnections.size() < poolMaximumIdleConnections && conn.getConnectionTypeCode() == expectedConnectionTypeCode) {
                    state.accumulatedCheckoutTime += conn.getCheckoutTime();
                    //5.手动回滚未完成的事物
                    if (!conn.getRealConnection().getAutoCommit()) {
                        conn.getRealConnection().rollback();
                    }
                    //6.将连接加到空闲连接里面去，其实并没有创建新的连接，而是基于这个连接里面的realConnection创建了一个PooledConnection对象
                    PooledConnection newConn = new PooledConnection(conn.getRealConnection(), this);
                    state.idleConnections.add(newConn);
                    //7.修改这个连接里面的状态变量
                    newConn.setCreatedTimestamp(conn.getCreatedTimestamp());
                    newConn.setLastUsedTimestamp(conn.getLastUsedTimestamp());
                    //8.销毁旧的PooledConnection
                    conn.invalidate();
                    if (log.isDebugEnabled()) {
                        log.debug("Returned connection " + newConn.getRealHashCode() + " to pool.");
                    }
                    //9.唤醒其他的等待获取连接的线程
                    state.notifyAll();
                } else {
                    //10.不满足4的条件，那么这个连接就会被销毁(比如空闲队列满了或者状态不对)
                    state.accumulatedCheckoutTime += conn.getCheckoutTime();
                    //11.回滚事务
                    if (!conn.getRealConnection().getAutoCommit()) {
                        conn.getRealConnection().rollback();
                    }
                    //12.关闭连接
                    conn.getRealConnection().close();
                    if (log.isDebugEnabled()) {
                        log.debug("Closed connection " + conn.getRealHashCode() + ".");
                    }
                    //13.将连接置为不合法
                    conn.invalidate();
                }
            } else {
                //14.不合法连接打印一下日志
                if (log.isDebugEnabled()) {
                    log.debug("A bad connection (" + conn.getRealHashCode() + ") attempted to return to the pool, discarding connection.");
                }
                //15.状态变量加一
                state.badConnectionCount++;
            }
        }
    }

    /**
     * 数据源获取连接的方法
     * 根据账号密码获取连接，获取的是PooledConnection对象，这个对象是一个代理对象，他代理了真正的连接对象，
     * 并对真正的连接对象做了一定的增强，具体可以参考PooledConnection#invoke方法的注释
     */
    private PooledConnection popConnection(String username, String password) throws SQLException {
        boolean countedWait = false;
        PooledConnection conn = null;
        long t = System.currentTimeMillis();
        int localBadConnectionCount = 0;

        while (conn == null) {
            //1.加锁，PooledDataSource是线程安全的数据库连接池
            synchronized (state) {
                //2.如果有空闲连接队列中有连接，直接拿队列的首个连接对象
                if (!state.idleConnections.isEmpty()) {
                    // Pool has available connection
                    conn = state.idleConnections.remove(0);
                    if (log.isDebugEnabled()) {
                        log.debug("Checked out connection " + conn.getRealHashCode() + " from pool.");
                    }
                } else {
                    //3.没有空闲连接，就走这一步
                    // Pool does not have available connection
                    //4.没有空闲连接，并且活跃的连接小于允许活跃的连接数上限，那么就可以创建新的连接
                    if (state.activeConnections.size() < poolMaximumActiveConnections) {
                        // Can create new connection
                        //5.创建新的连接，创建后直接跳到commentA处的代码逻辑
                        conn = new PooledConnection(dataSource.getConnection(), this);
                        if (log.isDebugEnabled()) {
                            log.debug("Created connection " + conn.getRealHashCode() + ".");
                        }
                    } else {
                        //6.没有空闲连接，并且活跃的连接处已经大于允许活跃的连接数上限，就会走到这里，此时不允许创建新的连接了
                        // Cannot create new connection
                        //7.不允许创建，那么就从活跃连接队列中拿一个连接（等第一个超时了就可以拿出来使用，第一个没超时后面的更加不会超时了）
                        PooledConnection oldestActiveConnection = state.activeConnections.get(0);
                        //8.获取这个拿到的连接上一次检查到现在所经过的时间
                        long longestCheckoutTime = oldestActiveConnection.getCheckoutTime();
                        //9.检查连接是否过期，如果checkoutTimestamp到现在的时间已经大于poolMaximumCheckoutTime就过期了
                        if (longestCheckoutTime > poolMaximumCheckoutTime) {
                            // Can claim overdue connection
                            //10.连接已经过期，那么把这个连接从活跃队列中移除
                            //11.修改相关的状态变量
                            state.claimedOverdueConnectionCount++;
                            //累计超时时间增加
                            state.accumulatedCheckoutTimeOfOverdueConnections += longestCheckoutTime;
                            //累计使用时间增加
                            state.accumulatedCheckoutTime += longestCheckoutTime;
                            //12.活跃队列移除对应的连接
                            state.activeConnections.remove(oldestActiveConnection);
                            //13.如果连接还有事物尚未提交，则回滚，这里条件的判断意思是连接没有开启自动提交，说明是手动提交模式，则帮他回滚这个事物
                            if (!oldestActiveConnection.getRealConnection().getAutoCommit()) {
                                try {
                                    oldestActiveConnection.getRealConnection().rollback();
                                } catch (SQLException e) {
                                    log.debug("Bad connection. Could not roll back");
                                }
                            }
                            //14.从拿到的连接里面获取到里面的真实的连接对象，再把这个真实的对象包装为一个PooledConnection
                            //(每个PooledConnection里面都包装了一个真正的连接对象)
                            conn = new PooledConnection(oldestActiveConnection.getRealConnection(), this);
                            //15.销毁之前的PooledConnection
                            oldestActiveConnection.invalidate();
                            if (log.isDebugEnabled()) {
                                log.debug("Claimed overdue connection " + conn.getRealHashCode() + ".");
                            }
                        } else {
                            //16.如果活跃队列没有过期(第一个没有过期，说明后面的也不会过期)，那么只能等待了
                            // (注意活跃连接不是可用连接，空闲连接才是可用连接，活跃连接是没有回收的连接，比如其他线程A在使用的连接，这个连接就是活跃连接，
                            // 获取到之后要判断这个连接是否超时了(比如网络原因)，超时了就移除并重新设置状态，没有超时那么说明线程A还在使用，就只能等待了)
                            // Must wait
                            try {
                                if (!countedWait) {
                                    //连接池等待次数加1
                                    state.hadToWaitCount++;
                                    countedWait = true;
                                }
                                if (log.isDebugEnabled()) {
                                    log.debug("Waiting as long as " + poolTimeToWait + " milliseconds for connection.");
                                }
                                long wt = System.currentTimeMillis();
                                //阻塞等待被唤醒，默认最长阻塞2秒
                                state.wait(poolTimeToWait);
                                //17.累加等待时间
                                state.accumulatedWaitTime += System.currentTimeMillis() - wt;
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                    }
                }
                //18.到这一步，理论上是拿到了一个连接了 --- commentA
                if (conn != null) {
                    if (conn.isValid()) {
                        //19.检查事物
                        if (!conn.getRealConnection().getAutoCommit()) {
                            conn.getRealConnection().rollback();
                        }
                        conn.setConnectionTypeCode(assembleConnectionTypeCode(dataSource.getUrl(), username, password));
                        conn.setCheckoutTimestamp(System.currentTimeMillis());
                        conn.setLastUsedTimestamp(System.currentTimeMillis());
                        //20.新创建的这个连接需要加到活跃队列里面，这个连接可能是空闲队列拿到的，也可能是创建新的连接,也可能是从活跃队列里面移除了一个过期的，
                        //并把过期的PooledConnection里面的真实连接拿到包装成一个新的PooledConnection，不管是3中情况哪一种创建的，这里都需要把它加到活跃队
                        //列，这里也说明了注释16处的说明，活跃连接不是可用连接(如果没超时那就不可用，只能等，超时的话，做一些工作之后创建一个新的是可用的)
                        state.activeConnections.add(conn);
                        state.requestCount++;
                        state.accumulatedRequestTime += System.currentTimeMillis() - t;
                    } else {
                        //21.拿到的连接不合法，抛出异常
                        if (log.isDebugEnabled()) {
                            log.debug("A bad connection (" + conn.getRealHashCode() + ") was returned from the pool, getting another connection.");
                        }
                        //相关状态修改
                        state.badConnectionCount++;
                        localBadConnectionCount++;
                        conn = null;
                        if (localBadConnectionCount > (poolMaximumIdleConnections + 3)) {
                            if (log.isDebugEnabled()) {
                                log.debug("PooledDataSource: Could not get a good connection to the database.");
                            }
                            throw new SQLException("PooledDataSource: Could not get a good connection to the database.");
                        }
                    }
                }
            }

        }

        //22.返回前进行判断，如果为null就抛出异常，按理到这一步应该是拿到连接了，为了健壮性
        if (conn == null) {
            if (log.isDebugEnabled()) {
                log.debug("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
            }
            throw new SQLException("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
        }
        //23.返回
        return conn;
    }

    /**
     * Method to check to see if a connection is still usable
     * 检查连接是否可用
     *
     * @param conn - the connection to check
     * @return True if the connection is still usable
     */
    protected boolean pingConnection(PooledConnection conn) {
        boolean result = true;

        try {
            //1.如果连接是关闭的或者有异常，返回false
            result = !conn.getRealConnection().isClosed();
        } catch (SQLException e) {
            if (log.isDebugEnabled()) {
                log.debug("Connection " + conn.getRealHashCode() + " is BAD: " + e.getMessage());
            }
            result = false;
        }

        //2.上面的没有异常，则进一步判断
        if (result) {
            //3.允许ping才进行判断
            if (poolPingEnabled) {
                //4.poolPingConnectionsNotUsedFor表示多久没有使用了才去ping，getTimeElapsedSinceLastUse返回上一次使用之后到现在过去了多久，
                //如果上一次使用到现在时间很短，那么就不要ping，为了提高效率，因为这样的情况下大概率连接是ok的，
                //如果上一次使用到现在已经过去很久了，大于poolPingConnectionsNotUsedFor这个阈值，那么就很有必要检查下这个连接了
                //这里也能看出，如果poolPingConnectionsNotUsedFor小于0，说明任何时候都不会去真正的ping服务器
                if (poolPingConnectionsNotUsedFor >= 0 && conn.getTimeElapsedSinceLastUse() > poolPingConnectionsNotUsedFor) {
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug("Testing connection " + conn.getRealHashCode() + " ...");
                        }
                        //5.这里可以看出，其实就是执行了poolPingQuery这个sql语句，如果有异常，则方法返回false，默认是没有设置这个语句
                        //的-->"NO PING QUERY SET"
                        Connection realConn = conn.getRealConnection();
                        Statement statement = realConn.createStatement();
                        ResultSet rs = statement.executeQuery(poolPingQuery);
                        rs.close();
                        statement.close();
                        if (!realConn.getAutoCommit()) {
                            realConn.rollback();
                        }
                        result = true;
                        if (log.isDebugEnabled()) {
                            log.debug("Connection " + conn.getRealHashCode() + " is GOOD!");
                        }
                    } catch (Exception e) {
                        //6.如果没有设置poolPingQuery，这里就报异常，内部进行了异常捕获处理
                        log.warn("Execution of ping query '" + poolPingQuery + "' failed: " + e.getMessage());
                        try {
                            conn.getRealConnection().close();
                        } catch (Exception e2) {
                            //ignore
                        }
                        result = false;
                        if (log.isDebugEnabled()) {
                            log.debug("Connection " + conn.getRealHashCode() + " is BAD: " + e.getMessage());
                        }
                    }
                }
            }
        }
        return result;
    }

    /*
     * Unwraps a pooled connection to get to the 'real' connection
     *
     * @param conn - the pooled connection to unwrap
     * @return The 'real' connection
     */
    public static Connection unwrapConnection(Connection conn) {
        if (Proxy.isProxyClass(conn.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(conn);
            if (handler instanceof PooledConnection) {
                return ((PooledConnection) handler).getRealConnection();
            }
        }
        return conn;
    }

    protected void finalize() throws Throwable {
        forceCloseAll();
        super.finalize();
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException(getClass().getName() + " is not a wrapper.");
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    public Logger getParentLogger() {
        return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME); // requires JDK version 1.6
    }

}
