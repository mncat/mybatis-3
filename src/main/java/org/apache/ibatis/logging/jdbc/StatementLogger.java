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
package org.apache.ibatis.logging.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.Statement;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.reflection.ExceptionUtil;

/**
 * Statement proxy to add logging
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 *
 */
public final class StatementLogger extends BaseJdbcLogger implements InvocationHandler {

  //1.内部封装的Statement
  private Statement statement;

  private StatementLogger(Statement stmt, Log statementLog, int queryStack) {
    super(statementLog, queryStack);
    this.statement = stmt;
  }

  /**
   * invoke方法，动态代理的核心方法
   *
   * */
  @Override
  public Object invoke(Object proxy, Method method, Object[] params) throws Throwable {
    try {
      //1.如果是Object定义的方法，使用当前对象直接调用
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, params);
      }

      /**
       * 2.如果是："execute"、"executeUpdate"、"executeQuery"或者"addBatch"方法，那就打印日志
       * EXECUTE_METHODS.add("execute");
       *     EXECUTE_METHODS.add("executeUpdate");
       *     EXECUTE_METHODS.add("executeQuery");
       *     EXECUTE_METHODS.add("addBatch");
       * */
      if (EXECUTE_METHODS.contains(method.getName())) {
        if (isDebugEnabled()) {
          debug(" Executing: " + removeBreakingWhitespace((String) params[0]), true);
        }
        if ("executeQuery".equals(method.getName())) {
            //3.如果是executeQuery查询方法会返回ResultSet，那就执行之后，将ResultSet包装成一个具备日志功能的ResultSetLogger
          ResultSet rs = (ResultSet) method.invoke(statement, params);
          //4.返回具备日志能力的ResultSetLogger
          return rs == null ? null : ResultSetLogger.newInstance(rs, statementLog, queryStack);
        } else {
            //5.如果不是executeQuery方法(其余三个方法都是返回int，不需要包装)，那就直接调用,不需要使用增强了日志功能的对象
          return method.invoke(statement, params);
        }
      } else if ("getResultSet".equals(method.getName())) {
        ResultSet rs = (ResultSet) method.invoke(statement, params);
        //6.如果返回的ResultSet不是null，那就返回一个ResultSet的代理对象，一个具备日志打印能力的ResultSet
        return rs == null ? null : ResultSetLogger.newInstance(rs, statementLog, queryStack);
      } else {
          //7.如果是其他方法，就直接调用，不需要使用增强了日志功能的对象
        return method.invoke(statement, params);
      }
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
  }

  /*
   * Creates a logging version of a Statement
   *
   * @param stmt - the statement
   * @return - the proxy
   * 创建实例
   */
  public static Statement newInstance(Statement stmt, Log statementLog, int queryStack) {
    InvocationHandler handler = new StatementLogger(stmt, statementLog, queryStack);
    ClassLoader cl = Statement.class.getClassLoader();
    return (Statement) Proxy.newProxyInstance(cl, new Class[]{Statement.class}, handler);
  }

  /*
   * return the wrapped statement
   *
   * @return the statement
   */
  public Statement getStatement() {
    return statement;
  }

}
