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
package org.apache.ibatis.scripting.defaults;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeException;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class DefaultParameterHandler implements ParameterHandler {

  //1.类型处理器注册中心
  private final TypeHandlerRegistry typeHandlerRegistry;

  //2.MappedStatement是保存sql语句的数据结构
  private final MappedStatement mappedStatement;
  //3.参数对象
  private final Object parameterObject;
  //4.BoundSql对象是sql语句和相关信息的封装
  private BoundSql boundSql;
  //5.全局配置对象
  private Configuration configuration;

  //构造方法
  public DefaultParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
    this.mappedStatement = mappedStatement;
    this.configuration = mappedStatement.getConfiguration();
    this.typeHandlerRegistry = mappedStatement.getConfiguration().getTypeHandlerRegistry();
    this.parameterObject = parameterObject;
    this.boundSql = boundSql;
  }

  @Override
  public Object getParameterObject() {
    return parameterObject;
  }

  /**
   * 将占位符替换为参数值
   * */
  @Override
  public void setParameters(PreparedStatement ps) {
    ErrorContext.instance().activity("setting parameters").object(mappedStatement.getParameterMap().getId());
    //1.获取sql语句的参数，ParameterMapping里面包含参数的名称类型等详细信息，还包括类型处理器
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    if (parameterMappings != null) {
      //2.遍历依次处理
      for (int i = 0; i < parameterMappings.size(); i++) {
        ParameterMapping parameterMapping = parameterMappings.get(i);
        //3.OUT类型参数不处理
        if (parameterMapping.getMode() != ParameterMode.OUT) {
          Object value;
          //4.获取参数名称
          String propertyName = parameterMapping.getProperty();
          //5.如果propertyName是动态参数，就会从动态参数中取值。(当使用<foreach>的时候，MyBatis会自动生成额外的动态参数)
          if (boundSql.hasAdditionalParameter(propertyName)) { // issue #448 ask first for additional params
            value = boundSql.getAdditionalParameter(propertyName);
          } else if (parameterObject == null) {
              //6.如果参数是null，不管属性名是什么，都会返回null。
            value = null;
          } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
              //7.判断类型处理器是否有参数类型,如果参数是一个简单类型，或者是一个注册了typeHandler的对象类型，就会直接使用该参数作为返回值，和属性名无关。
            value = parameterObject;
          } else {
            //8.这种情况下是复杂对象或者Map类型，通过反射方便的取值。通过MetaObject操作
            MetaObject metaObject = configuration.newMetaObject(parameterObject);
            value = metaObject.getValue(propertyName);
          }
          TypeHandler typeHandler = parameterMapping.getTypeHandler();
          //9.获取对应的数据库类型
          JdbcType jdbcType = parameterMapping.getJdbcType();
          //空类型
          if (value == null && jdbcType == null) {
            jdbcType = configuration.getJdbcTypeForNull();
          }
          //10.对PreparedStatement的占位符设置值(类型处理器可以给PreparedStatement设值)
          try {
            typeHandler.setParameter(ps, i + 1, value, jdbcType);
          } catch (TypeException e) {
            throw new TypeException("Could not set parameters for mapping: " + parameterMapping + ". Cause: " + e, e);
          } catch (SQLException e) {
            throw new TypeException("Could not set parameters for mapping: " + parameterMapping + ". Cause: " + e, e);
          }
        }
      }
    }
  }

}
