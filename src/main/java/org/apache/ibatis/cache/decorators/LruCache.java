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
package org.apache.ibatis.cache.decorators;

import org.apache.ibatis.cache.Cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * Lru (least recently used) cache decorator
 * 为缓存添加LRU的淘汰策略
 * 1.LRU的实现思路是，额外维护一个LRU队列按照key的访问顺序保存缓存的key，它是基于LinkedHashMap实现的
 * 2.每次put新增缓存，新的缓存key会被放在LRU队列尾部，并且会判断LRU队列中队列头部的最旧的那个key是否需要删除，
 *   如果长度达到了1024就把LRU队列中这个key删除并把这个key记录到一个变量eldestKey中，然后会判断eldestKey是否
 *   为null，如果不为null就在缓存队列中删除这个最旧的key
 *   (参考LinkedHashMap#afterNodeInsertion)
 * 3.每次get获取缓存的时候，在缓存队列获取缓存数据之前，会在LRU队列中获取一次key，这个获取其实并不是得到他的返回值，
 * 仅仅只是触发一次排除，get之后这个key就会被放在LRU队列的尾部，让队列按照LRU原则排序排序
 * (参考LinkedHashMap.get和LinkedHashMap.afterNodeAccess)
 *
 * @author Clinton Begin
 */
public class LruCache implements Cache {

    private final Cache delegate;
    //1.keyMap是一个LinkedHashMap，也是一个LRU队列，他是按照元素的访问顺序排队的，最久未被访问的元素排在
    //队头，最近访问的元素排在队尾(value和key是一样的，其实就是一个有序的链表)
    private Map<Object, Object> keyMap;
    //2.记录最老的key
    private Object eldestKey;

    //3.构造方法初始化LRU队列长度为1024
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

    //4.初始化LRU队列
    public void setSize(final int size) {
        //1.LinkedHashMap的元素是有序的，可以按照插入顺序或者按访问顺序(调用get方法)。构造方法的第三个参数可以指定使用哪一种顺序排序
        //true按照访问顺序排序，
        //false按照插入顺序排序
        //这里是按照访问顺序排序，符合LRU思想
        keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {

            private static final long serialVersionUID = 4267176411845948333L;
            /**
             * LinkedHashMap的removeEldestEntry方法，只要返回true，就会自动删除最近最少使用的key
             * 默认是按插入顺序排序，如果指定按访问顺序排序，那么调用get方法后，会将这次访问的元素移至
             * 链表尾部，不断访问可以形成按访问顺序排序的链表。可以重写removeEldestEntry方法返回
             * true值指定插入元素时移除最老的元素
             *
             * 这里的删除逻辑是这样的，在每一次插入一个新的元素之后，LinkedHashMap里面都会调用removeEldestEntry
             * 方法来判断是否需要删除元素，而传给removeEldestEntry的参数就是链表的头指针，也就是是将最旧的元素传进来，
             * 如果方法一直返回false说明队列永远不会删除元素。这里我们的逻辑是大于1024长度之后，就会返回true，那么返
             * 回true之后LinkedHashMap就会把first代表的最旧的元素删除，与此同时我们会把这个最旧的key记录到eldestKey变量，
             * 因为仅仅删除了这个LRU队列还不够，我们还需要删除真正的缓存队列，缓存队列这个删除逻辑是在put进元素的时候回去检
             * 查，在cycleKeyList中实现，可参考LinkedHashMap#afterNodeInsertion方法
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
        //1.put进来一个缓存之后，将这个key放到LRU队列尾部，并判断是否有最旧的元素需要删除。逻辑都在cycleKeyList里面实现
        cycleKeyList(key);
    }

    @Override
    public Object getObject(Object key) {
        //1.这里访问get方法就是为了让这个访问的key放到队列的尾部，因此keyMap是按照访问顺序排序的，最久未被
        //问的会放在队列的前面,这里访问该key，就会把置于队尾，实现LRU
        //可以参考LinkedHashMap.get和LinkedHashMap.afterNodeAccess
        //touch
        keyMap.get(key);
        return delegate.getObject(key);
    }

    @Override
    public Object removeObject(Object key) {
        return delegate.removeObject(key);
    }

    @Override
    public void clear() {
        delegate.clear();
        //清理缓存的时候也要清理key集合
        keyMap.clear();
    }

    @Override
    public ReadWriteLock getReadWriteLock() {
        return null;
    }

    //将key放到LRU队列尾部，并判断是否有最旧的key需要删除
    private void cycleKeyList(Object key) {
        //1.这一步就会将key放到LRU队列的尾部，为什么是尾部，参考LinkedHashMap.afterNodeAccess
        keyMap.put(key, key);
        //2.如果有最旧的key需要删除，就删掉
        //LinkedHashMap删除key是在LinkedHashMap#afterNodeInsertion中调用的，这只是把LRU队列的key删除了，但是真正的缓存队列还没有删除key，
        //但是会把key赋值给eldestKey，每一次放进缓存的时候都会来检查，发现有eldestKey，就把他从真正的缓存队列里面清除，并把eldestKey置null
        if (eldestKey != null) {
            delegate.removeObject(eldestKey);
            eldestKey = null;
        }
    }
}
