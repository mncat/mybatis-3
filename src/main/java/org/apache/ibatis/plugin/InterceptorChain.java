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
package org.apache.ibatis.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 保存所有的Mybatis插件，Configuration对象中持有InterceptorChain实例，保存全部的插件。
 * 该类主要提供2个作用：
 * 1.提供添加插件的功能。添加功能在解析配置的时候会用到，将解析后的插件保存到一个集合里面
 * 2.提供获取增强后的对象的功能。pluginAll方法会对传入的对象进行代理增强，每一次plugin都是一个代理的过程，
 * ，前一个插件获取的代理后的对象是后面一个插件进行增强的原始对象，最终返回的对象包含全部插件的增强。
 * @author Clinton Begin
 */
public class InterceptorChain {

  //1.内部用于保存插件的list
  private final List<Interceptor> interceptors = new ArrayList<Interceptor>();

  //2.依次调用每个插件，让每个插件对target对象进行包装代理
  //比如传进来的是一个parameterHandler，现在有三个插件interceptor1，interceptors2，interceptors3
  //那么这里第一步会先调用interceptor1.plugin(parameterHandler),这个过程就会把parameterHandler进行一次代理，然后将代理对象返回，
  //(如果不需要代理就会返回原来的对象，这个过程在plugin方法中会进行处理)
  //然后在将第一步得到的对象应用于interceptors2，interceptors3...依次处理
  //处理完毕之后获得的就是一个原始target对象的代理对象，Mybatis提供了一个简单的获取代理对象的方法Plugin.wrap(target, this);
  //因此很多插件的plugin方法都是调用的这个方法
  public Object pluginAll(Object target) {
    for (Interceptor interceptor : interceptors) {
      target = interceptor.plugin(target);
    }
    return target;
  }

  //添加插件的方法
  public void addInterceptor(Interceptor interceptor) {
    interceptors.add(interceptor);
  }
  
  public List<Interceptor> getInterceptors() {
    return Collections.unmodifiableList(interceptors);
  }

}
