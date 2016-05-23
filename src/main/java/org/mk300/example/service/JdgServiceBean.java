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