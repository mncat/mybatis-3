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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 * 解析核心配置文件mybatis-config.xml的类(核心配置文件的名称可以自定义)
 */
public class XMLConfigBuilder extends BaseBuilder {

  private boolean parsed;
  private XPathParser parser;
  private String environment;
  private ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  //解析配置
  public Configuration parse() {
    //1.判断是否已经解析过，不重复解析
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
    //2.读取主配置文件的configuration节点下面的配置信息，parseConfiguration方法完成解析的流程
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  /**
   * 解析核心配置文件的关键方法，
   * 读取节点的信息，并通过对应的方法去解析配置，解析到的配置全部会放在configuration里面
   * */
  private void parseConfiguration(XNode root) {
    try {
      Properties settings = settingsAsPropertiess(root.evalNode("settings"));
      //issue #117 read properties first
      //解析<properties>节点
      propertiesElement(root.evalNode("properties"));
      //解析<settings>节点
      loadCustomVfs(settings);
      //解析<typeAliases>节点
      typeAliasesElement(root.evalNode("typeAliases"));
      //解析<plugins>节点
      pluginElement(root.evalNode("plugins"));
      //解析<objectFactory>节点
      objectFactoryElement(root.evalNode("objectFactory"));
      //解析<objectWrapperFactory>节点
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      //解析<reflectorFactory>节点
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      //将settings填充到configuration
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      //解析<environments>节点
      environmentsElement(root.evalNode("environments"));
      //解析<databaseIdProvider>节点
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      //解析<typeHandlers>节点
      typeHandlerElement(root.evalNode("typeHandlers"));
      //解析<mappers>节点，里面会使用XMLMapperBuilder
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  private Properties settingsAsPropertiess(XNode context) {
    if (context == null) {
      return new Properties();
    }
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      //1.非空才会处理，依次遍历所有节点
      for (XNode child : parent.getChildren()) {
        //2.处理package类型配置
        if ("package".equals(child.getName())) {
          //2.1获取包名
          String typeAliasPackage = child.getStringAttribute("name");
          //2.2注册包名，将包名放到typeAliasRegistry里面，里面拿到包名之后还会进一步处理
          //最后会放到TypeAliasRegistry.TYPE_ALIASES这个Map里面去
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          //3.处理typeAlias类型配置
          //3.1获取别名
          String alias = child.getStringAttribute("alias");
          //3.2获取类名
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            //3.3下面的注册逻辑其实比前面注册包名要简单，注册包名要依次处理包下的类，也会调用registerAlias方法，
            //这里直接处理类，别名没有配置也没关系，里面会生成一个getSimpleName或者根据Alias注解去取别名
            if (alias == null) {
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            //4.其他类型直接报错
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  /**
   * 解析插件
   * */
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
        //1.遍历处理每个节点
      for (XNode child : parent.getChildren()) {
          //2.获取interceptor插件名称，实际上是全限定类名
        String interceptor = child.getStringAttribute("interceptor");
        //3.获取配置的属性
        Properties properties = child.getChildrenAsProperties();
        //4.反射获取到这个类的实例
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
        //5.给实例赋配置的属性值
        interceptorInstance.setProperties(properties);
        //6.保存到Configuration中的插件链对象
        //protected final InterceptorChain interceptorChain = new InterceptorChain();
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
       String type = context.getStringAttribute("type");
       ReflectorFactory factory = (ReflectorFactory) resolveClass(type).newInstance();
       configuration.setReflectorFactory(factory);
    }
  }

  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      //1.如果Properties有内容，就去读取子节点Propertie，读取配置放在一个Properties里面
      Properties defaults = context.getChildrenAsProperties();
      //解析resource属性
      String resource = context.getStringAttribute("resource");
      //解析url属性
      String url = context.getStringAttribute("url");
      //2者不能同时为空
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      //根据resource加载配置文件
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        //根据url加载配置文件
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      //配置全部合并
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      //更新配置，读取到的配置最终是要放到configuration里面去的
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  private void settingsElement(Properties props) throws Exception {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), true));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    @SuppressWarnings("unchecked")
    Class<? extends Log> logImpl = (Class<? extends Log>)resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  /**
   * 解析environments节点，数据源的初始化在该流程里面完成
   * */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      //1.根据default获取到多个环境中默认的那一个环境的id
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      //2.依次遍历解析所有environment子节点
      for (XNode child : context.getChildren()) {
        //3.获取到id
        String id = child.getStringAttribute("id");
        //4.和default获取到的id一样的才是目标environment,其余的都不需要解析
        if (isSpecifiedEnvironment(id)) {
          //5.解析transactionManager节点
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          //6.解析dataSource节点，得到数据源工厂
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          //7.工厂模式，由数据源工厂得到数据源(这部分可以参考: https://blog.csdn.net/my_momo_csdn/article/details/93489371)
          DataSource dataSource = dsFactory.getDataSource();
          //8.创建Environment，很显然使用了建造者模式
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          //9.将Environment设置到Configuration对象里面去
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      if ("VENDOR".equals(type)) {
          type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  /**
   *解析主配置文件dataSource子节点
   * */
  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
        //1.获取数据源类型
      String type = context.getStringAttribute("type");
      //2.获取子节点属性；其实是获取所有的name和value属性，封装到Properties里面，通常这里包含了driver，url，username，password的信息
      Properties props = context.getChildrenAsProperties();
      //3.通过类型获取对应类型的的数据源工厂，这里会到TypeAliasRegistry.TYPE_ALIASES这个Map里面去找type对应的类型
      //通常情况这里面保存的是类的别名和类的Class对象，但是Mybatis把pool，unpool，jndi也保存在里面，对应的类型是对应的工厂类
      //因此获取到之后再newInstance就得到了工厂实例, 最后调用的是TypeAliasRegistry.resolveAlias方法
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
      //4.数据源工厂是可以设置属性的，这些属性最后都会设置给数据源DataSource
      factory.setProperties(props);
      return factory;
    }
    //6.没有配置dataSource节点需要抛出异常
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  private void typeHandlerElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   * 解析配置文件的mappers子节点，方法主要是实现一个大体框架，按照resource->url->class的优先级读取配置，具体的解
   * 析细节是依赖于XMLMapperBuilder来实现的，XMLMapperBuilder通过parse方法屏蔽了细节，内部完成解析过程
   * */
  private void mapperElement(XNode parent) throws Exception {
    //
    if (parent != null) {
        //1.节点非空，遍历子节点逐个处理，因为mapperElement(root.evalNode("mappers"))解析的mappers里面可能有多个标签
      for (XNode child : parent.getChildren()) {
          //1.1 处理package类型的配置
        if ("package".equals(child.getName())) {
          //1.2 按照包来添加，扫包之后默认会在包下找与java接口名称相同的mapper映射文件，name就是包名，
          String mapperPackage = child.getStringAttribute("name");
          configuration.addMappers(mapperPackage);
        } else {
          //1.3 一个一个Mapper.xml文件的添加 ， resource、url和class三者是互斥的，resource优先级最高
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          if (resource != null && url == null && mapperClass == null) {
            //1.4 按照resource属性实例化XMLMapperBuilder来解析xml配置文件
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            //1.5解析配置，因为XMLMapperBuilder继承了BaseBuilder，BaseBuilder内部持有Configuration对象，因此
            //XMLMapperBuilder解析之后直接把配置设置到Configuration对象
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            //1.6 按照url属性实例化XMLMapperBuilder来解析xml配置文件
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            //1.7 按照class属性实例化XMLMapperBuilder来解析xml配置文件
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            //resource、url和class三者是互斥的，配置了多个或者不配置都抛出异常
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
