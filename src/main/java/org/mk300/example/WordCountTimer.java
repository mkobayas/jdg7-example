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

package org.mk300.example;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;

import org.infinispan.Cache;
import org.infinispan.CacheStream;
import org.infinispan.stream.CacheCollectors;
import org.jboss.logging.Logger;
import org.mk300.example.service.JdgServiceBean;

import com.atilika.kuromoji.unidic.Tokenizer;

@Singleton
@Startup
@Lock(LockType.READ)
public class WordCountTimer implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Logger.getLogger(RestAp.class);
    
    @EJB
    private transient JdgServiceBean jdg;
    
    private static Tokenizer tokenizer = new Tokenizer();
    
    private transient AtomicBoolean isRunning;
    
    @PostConstruct
    private void init() {
        isRunning = new AtomicBoolean(false);
    }

    @Schedule(second = "*/3", minute = "*", hour = "*", persistent = false)
    public void timer() {
        if (jdg.getManager().isCoordinator()) {
            if(!isRunning.compareAndSet(false, true)) {
                return ;
            }
            
            try {
                Map<String, Long> count = wordCount();
                log.infof("TIMER %s", count);
                
                Cache<Date, Map<String, Long>> cache = jdg.getManager().getCache("deliver");
                cache.put(new Date(), count, 2, TimeUnit.HOURS);
            } finally {
                isRunning.set(false);
            }
        }
    }
    
    private Map<String,Long> wordCount() {
        Cache<Long, String> cache = jdg.getManager().getCache("default");

        log.debug("STREAM START");
        
        // CacheSteam(=DistributedStream)生成
        try (CacheStream<Entry<Long, String>> stream = cache.entrySet().parallelStream()) { 
            
            ConcurrentMap<String,Long> collected = stream
                    .flatMap(e -> tokenizer.tokenize(e.getValue()).stream())  // Kuromojiで形態素解析(分散処理)
                    .map(e -> e.getPartOfSpeechLevel1())                      // 品詞取得に変換 (分散処理)
                    .collect(                                                 // 集計(各ノードで1次集計し、各集計結果を呼び出し元に集めて2次集計)
                            CacheCollectors.serializableCollector(
                                    () -> Collectors.groupingByConcurrent(Function.identity(), Collectors.counting()) // 品詞単位で集計
                                    )
                            );

            log.debug("STREAM END");
            
            return collected;
        }
    }
}
