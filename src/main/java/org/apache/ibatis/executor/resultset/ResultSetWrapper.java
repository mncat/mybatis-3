/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.executor.resultset;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.ObjectTypeHandler;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.apache.ibatis.type.UnknownTypeHandler;

/**
 * @author Iwao AVE!
 * DefaultResultSetHandler使用的一个辅助类。包装了ResultSet
 */
public class ResultSetWrapper {

  //1.被包装的ResultSet
  private final ResultSet resultSet;
  //2.类型处理器
  private final TypeHandlerRegistry typeHandlerRegistry;
  //3.字段名集合
  private final List<String> columnNames = new ArrayList<String>();
  //4.字段对应的Java类型集合
  private final List<String> classNames = new ArrayList<String>();
  //3.字段对应的JdbcType集合
  private final List<JdbcType> jdbcTypes = new ArrayList<JdbcType>();
  private final Map<String, Map<Class<?>, TypeHandler<?>>> typeHandlerMap = new HashMap<String, Map<Class<?>, TypeHandler<?>>>();
  private Map<String, List<String>> mappedColumnNamesMap = new HashMap<String, List<String>>();
  private Map<String, List<String>> unMappedColumnNamesMap = new HashMap<String, List<String>>();

  public ResultSetWrapper(ResultSet rs, Configuration configuration) throws SQLException {
    super();
    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    this.resultSet = rs;
    final ResultSetMetaData metaData = rs.getMetaData();
    final int columnCount = metaData.getColumnCount();
    //遍历ResultSetMetaData，解析得到columnNames字段名称集合、jdbcTypes类型集合、classNames类型集合
    for (int i = 1; i <= columnCount; i++) {
      columnNames.add(configuration.isUseColumnLabel() ? metaData.getColumnLabel(i) : metaData.getColumnName(i));
      jdbcTypes.add(JdbcType.forCode(metaData.getColumnType(i)));
      classNames.add(metaData.getColumnClassName(i));
    }
  }

  public ResultSet getResultSet() {
    return resultSet;
  }

  public List<String> getColumnNames() {
    return this.columnNames;
  }

  public List<String> getClassNames() {
    return Collections.unmodifiableList(classNames);
  }

  public JdbcType getJdbcType(String columnName) {
    for (int i = 0 ; i < columnNames.size(); i++) {
      if (columnNames.get(i).equalsIgnoreCase(columnName)) {
        return jdbcTypes.get(i);
      }
    }
    return null;
  }

  /**
   * Gets the type handler to use when reading the result set.
   * Tries to get from the TypeHandlerRegistry by searching for the property type.
   * If not found it gets the column JDBC type and tries to get a handler for it.
   * 在读取结果集的时候，从TypeHandlerRegistry获取TypeHandler来使用
   * 通过属性名从TypeHandlerRegistry获取，如果获取失败
   * @param propertyType
   * @param columnName
   * @return
   */
  public TypeHandler<?> getTypeHandler(Class<?> propertyType, String columnName) {
    TypeHandler<?> handler = null;
    //1.先尝试从缓存的typeHandlerMap中获得指定字段名的JavaType对应的TypeHandler对象
    Map<Class<?>, TypeHandler<?>> columnHandlers = typeHandlerMap.get(columnName);
    if (columnHandlers == null) {
      //2.如果为null，放一个空Map进去
      columnHandlers = new HashMap<Class<?>, TypeHandler<?>>();
      typeHandlerMap.put(columnName, columnHandlers);
    } else {
      //3.不为null，那么尝试从这个Map中获取handler
      handler = columnHandlers.get(propertyType);
    }
    //4.如果handler是null，那么这里面做初始化
    if (handler == null) {
      //5.先获取列对应的jdbcType，其实就是遍历jdbcTypes集合
      JdbcType jdbcType = getJdbcType(columnName);
      //6.获取handler
      handler = typeHandlerRegistry.getTypeHandler(propertyType, jdbcType);
      // Replicate logic of UnknownTypeHandler#resolveTypeHandler
      // See issue #59 comment 10
      //7.如果没有获取到，在尝试查找
      if (handler == null || handler instanceof UnknownTypeHandler) {
        //8.获取这个列对应的Java类型(反射创建类)
        final int index = columnNames.indexOf(columnName);
        final Class<?> javaType = resolveClass(classNames.get(index));
        //9.Java类型和JDBC类型都不为null
        if (javaType != null && jdbcType != null) {
          //10.从typeHandlerRegistry获取
          handler = typeHandlerRegistry.getTypeHandler(javaType, jdbcType);
        } else if (javaType != null) {
          //11.java类型不为null从typeHandlerRegistry获取
          handler = typeHandlerRegistry.getTypeHandler(javaType);
        } else if (jdbcType != null) {
          //12.JDBC类型不为null从typeHandlerRegistry获取
          handler = typeHandlerRegistry.getTypeHandler(jdbcType);
        }
      }
      //13.前面的if处理之后，如果还是没获取到，还是null，那么就new一个
      if (handler == null || handler instanceof UnknownTypeHandler) {
        handler = new ObjectTypeHandler();
      }
      //14.放到columnHandlers里面去
      columnHandlers.put(propertyType, handler);
    }
    //5.如果handler不是null，那么返回
    return handler;
  }

  private Class<?> resolveClass(String className) {
    try {
      // #699 className could be null
      if (className != null) {
        return Resources.classForName(className);
      }
    } catch (ClassNotFoundException e) {
      // ignore
    }
    return null;
  }

  /**
   * 加载mapped和没有mapped的字段的名字数组
   *<resultMap type="Employee" id="baseResultMapLazy">
   *         <id column="id" property="id"/>
   *         <result column="name" property="name"/>
   *         <result column="age" property="age"/>
   *     </resultMap>
   *这是一个resultMap的典型配置，这里面的column id，那么，age等就会被添加到resultMap.mappedColumns属性中去
   *
   * */
  private void loadMappedAndUnmappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
    //1.使用2个集合保存
    List<String> mappedColumnNames = new ArrayList<String>();
    List<String> unmappedColumnNames = new ArrayList<String>();
    //2.将columnPrefix转换成大写，并拼接到 resultMap.mappedColumns 属性上，mappedColumns就是加上了前缀的列集合
    final String upperColumnPrefix = columnPrefix == null ? null : columnPrefix.toUpperCase(Locale.ENGLISH);
    final Set<String> mappedColumns = prependPrefixes(resultMap.getMappedColumns(), upperColumnPrefix);
    //3.遍历columnNames数组，在mappedColumns中则添加到mappedColumnNames，反之添加到unmappedColumnNames
    for (String columnName : columnNames) {
      final String upperColumnName = columnName.toUpperCase(Locale.ENGLISH);
      if (mappedColumns.contains(upperColumnName)) {
        mappedColumnNames.add(upperColumnName);
      } else {
        unmappedColumnNames.add(columnName);
      }
    }
    //4.将 mappedColumnNames和unmappedColumnNames结果，添加到mappedColumnNamesMap和 unMappedColumnNamesMap
    //getMapKey获取key值
    mappedColumnNamesMap.put(getMapKey(resultMap, columnPrefix), mappedColumnNames);
    unMappedColumnNamesMap.put(getMapKey(resultMap, columnPrefix), unmappedColumnNames);
  }

  /**
   * 获取mapped的字段的名称数组。DefaultResultSetHandler中会使用到
   * */
  public List<String> getMappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
    //1.获得对应的 mapped 数组
    List<String> mappedColumnNames = mappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    //2.如果为null就初始化，从mappedColumnNamesMap中获取
    if (mappedColumnNames == null) {
      //初始化mappedColumnNamesMap
      loadMappedAndUnmappedColumnNames(resultMap, columnPrefix);
      //从mappedColumnNamesMap获取mapped的字段的名字数组
      mappedColumnNames = mappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    }
    return mappedColumnNames;
  }

    /**
     * 获取没有mapped的字段的名称数组，DefaultResultSetHandler中会使用到，和getMappedColumnNames方法类似
     * */
  public List<String> getUnmappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
    List<String> unMappedColumnNames = unMappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    if (unMappedColumnNames == null) {
      loadMappedAndUnmappedColumnNames(resultMap, columnPrefix);
      unMappedColumnNames = unMappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    }
    return unMappedColumnNames;
  }

  private String getMapKey(ResultMap resultMap, String columnPrefix) {
    return resultMap.getId() + ":" + columnPrefix;
  }

  /**
   * 将全部的columnNames都加上prefix
   * */
  private Set<String> prependPrefixes(Set<String> columnNames, String prefix) {
    if (columnNames == null || columnNames.isEmpty() || prefix == null || prefix.length() == 0) {
      return columnNames;
    }
    final Set<String> prefixed = new HashSet<String>();
    for (String columnName : columnNames) {
      prefixed.add(prefix + columnName);
    }
    return prefixed;
  }

}
