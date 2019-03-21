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
package org.apache.ibatis.cache.decorators;

import org.apache.ibatis.cache.Cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * Lru (least recently used) cache decorator
 * 为缓存添加LRU的淘汰策略
 *
 * @author Clinton Begin
 */
public class LruCache implements Cache {

    private final Cache delegate;
    private Map<Object, Object> keyMap;
    //记录最老的key
    private Object eldestKey;

    public LruCache(Cache delegate) {
        this.delegate = delegate;
        setSize(1024);
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public int getSize() {
        return delegate.getSize();
    }

    public void setSize(final int size) {
        //构造方法的第三个参数可以指定是按照访问顺序排序还是按照
        //true是access-order,按照访问顺序排序， false是insertion-order按照插入顺序排序
        keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {
            private static final long serialVersionUID = 4267176411845948333L;

            /**
             * LinkedHashMap的removeEldestEntry方法，只要返回true，就会自动删除最近最少使用的key
             * 默认是按插入顺序排序，如果指定按访问顺序排序，那么调用get方法后，会将这次访问的元素移至
             * 链表尾部，不断访问可以形成按访问顺序排序的链表。  可以重写removeEldestEntry方法返回
             * true值指定插入元素时移除最老的元素
             * */
            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
                boolean tooBig = size() > size;
                if (tooBig) {
                    eldestKey = eldest.getKey();
                }
                return tooBig;
            }
        };
    }

    @Override
    public void putObject(Object key, Object value) {
        delegate.putObject(key, value);
        //put进来一个缓存的k-V之后，需要将最近最少使用的删除
        cycleKeyList(key);
    }

    @Override
    public Object getObject(Object key) {
        //这里访问get方法就是为了让这个访问的key放到队列的尾部，因此创建的keyMap是按照访问顺序排序的，最久未被
        // 访问的会放在队列的前面这里访问该key，就会把置于队尾，实现LRU
        keyMap.get(key); //touch
        return delegate.getObject(key);
    }

    @Override
    public Object removeObject(Object key) {
        return delegate.removeObject(key);
    }

    @Override
    public void clear() {
        delegate.clear();
        keyMap.clear();
    }

    @Override
    public ReadWriteLock getReadWriteLock() {
        return null;
    }

    private void cycleKeyList(Object key) {
        keyMap.put(key, key);
        //在keyMap中删除最久未被使用的key时，会将key赋值给eldestKey，每一次放进缓存的时候都会来检查，发现
        //有eldestKey，就把他从真正的缓存队列里面清除，并把eldestKey置null
        if (eldestKey != null) {
            delegate.removeObject(eldestKey);
            eldestKey = null;
        }
    }

}
