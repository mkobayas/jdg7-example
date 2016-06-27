/*
 * Copyright 2016 Masazumi Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mk300.example.service;

import java.io.IOException;
import java.io.UncheckedIOException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;

import org.infinispan.manager.DefaultCacheManager;
import org.mk300.example.DeliverCacheListener;

/**
 * 
 * @author mkobayas@redhat.com
 *
 */
@Startup
@Singleton
public class JdgServiceBean {
    
    private DefaultCacheManager manager;
    
    @PostConstruct
    void init() {
        try {
            System.setProperty("jgroups.join_timeout", "500"); // コーディネータ検出のタイムアウト
            System.setProperty("jgroups.udp.ip_ttl", "0"); // クラスタ検出を同一ホスト内のみに絞る
            
            manager = new DefaultCacheManager("my-infinispan.xml", true);
            for (String cacheName : manager.getCacheNames()) {
                manager.startCache(cacheName);
            }
            
            manager.getCache("deliver").addListener(new DeliverCacheListener());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @PreDestroy
    void destory() {
        manager.stop();
    }

    @Lock(LockType.READ)
    public DefaultCacheManager getManager() {
        return manager;
    }
}