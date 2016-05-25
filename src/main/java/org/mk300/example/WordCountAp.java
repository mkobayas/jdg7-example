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

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.ejb.EJB;
import javax.ejb.Schedule;
import javax.ejb.Stateless;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.infinispan.Cache;
import org.infinispan.CacheStream;
import org.infinispan.stream.CacheCollectors;
import org.jboss.logging.Logger;
import org.mk300.example.service.JdgServiceBean;
import org.mk300.example.service.KokoroServiceBean;

import com.atilika.kuromoji.unidic.Tokenizer;

/**
 * 
 * @author mkobayas@redhat.com
 *
 */
@Stateless
@Path("/rest")
public class WordCountAp {
    private static final Logger log = Logger.getLogger(WordCountAp.class);
    
    @EJB
    private JdgServiceBean jdg;

    @EJB
    private KokoroServiceBean kokoro; // 夏目漱石「こころ」の各行を保持しているBean
    
    private static final Tokenizer tokenizer = new Tokenizer();
    private static final AtomicLong uid = new AtomicLong(); // Cacheのキー用(ユニークなID払い出し用)
    
    @GET
    @Path("/put")
    public String put(@QueryParam("num") int num) {
        log.debugf("PUT START num=%d", num);
        
        int putNum = num == 0 ? 1 : num;
        
        Cache<Long, String> cache = jdg.getManager().getCache("default");
        
        IntStream.rangeClosed(1, putNum).parallel().forEach(i -> {
            cache.put(
                    uid.incrementAndGet(),
                    kokoro.getRandom(),      // 夏目漱石「こころ」からランダムに1行取得してput
                    1,                       // LifeSapn = 1分(putしてから1分後に自動削除)
                    TimeUnit.MINUTES );
            });
        
        int cacheSize = cache.size();
        log.debugf("PUT END cacheSize=%,d", cacheSize);
        return "" + cacheSize;
    }
    
    @GET
    @Path("/wordcount")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public List<Map.Entry<String,Long>> wordCount(@QueryParam("topN") int topN) {
        Cache<Long, String> cache = jdg.getManager().getCache("default");

        log.debug("STREAM START");
        
        // CacheSteam(=DistributedStream)生成
        try (CacheStream<Entry<Long, String>> stream = cache.entrySet().parallelStream()) { 
            
            ConcurrentMap<String,Long> collected = stream
                    .flatMap(e -> tokenizer.tokenize(e.getValue()).stream())  // Kuromojiで形態素解析(分散処理)
                    .filter(e -> "名詞".equals(e.getPartOfSpeechLevel1()))     // 品詞＝名詞のみに絞る(分散処理)
                    .map(e -> e.getSurface())                                 // 原文に変換 (分散処理)
                    .collect(                                                 // 集計(各ノードで1次集計し、各集計結果を呼び出し元に集めて2次集計)
                            CacheCollectors.serializableCollector(
                                    () -> Collectors.groupingByConcurrent(Function.identity(), Collectors.counting()) // 単語単位で集計
                                    )
                            );

            log.debug("STREAM END");
            
            // 出現回数で降順ソートし、上位N番まで返却
            List<Map.Entry<String,Long>> result = 
                    collected.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(topN)
                    .collect(Collectors.toList());
            
            return result;
        }
    }

    @Schedule(second = "*/5", minute = "*", hour = "*", persistent = false)
    public void timer() {
        if (jdg.getManager().isCoordinator()) {
            List<Entry<String, Long>> count = wordCount(10);
            log.infof("TIMER %s", count);
        }
    }
    
    @GET
    @Path("/wordcount_debug")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public List<Map.Entry<String,Long>> wordCountDebug(@QueryParam("topN") int topN) {
        log.info("STREAM START");
        
        Cache<Long, String> cache = jdg.getManager().getCache("default");
        
        try (CacheStream<Entry<Long, String>> stream = cache.entrySet().parallelStream()) {

            ConcurrentMap<String,Long> collected = stream
                    .flatMap(e -> {
                        log.infof("flatMap %s", e);
                        return tokenizer.tokenize(e.getValue()).stream();  // Kuromojiで形態素解析(分散処理)
                    })
                    .filter(e -> {
                        log.infof("filter %s", e);
                        return "名詞".equals(e.getPartOfSpeechLevel1());     // 品詞＝名詞のみに絞る(分散処理)
                    })
                    .map(e -> {
                        log.infof("map %s", e);
                        return e.getSurface();                              // 原文に変換 (分散処理)
                    })
                    .collect(
                            CacheCollectors.serializableCollector(
                                    () -> Collectors.groupingByConcurrent(
                                            (Function<String, String>)s -> {log.infof("identify %s", s);return s;}, 
                                            ConcurrentHashMap::new,
                                            Collectors.reducing(
                                                    0L, 
                                                    e -> {log.infof("reduce-map %s", e);return 1L;}, 
                                                    (l1, l2) -> {log.infof("reduce-op %d, %d", l1, l2);return l1+l2;}
                                                    )
                                            )
                                    )
                            );

            log.info("STREAM END");

            // 出現回数で降順ソートし、上位N番まで返却
            List<Map.Entry<String,Long>> result = 
                    collected.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(topN)
                    .collect(Collectors.toList());
            
            return result;
        }
    }
    
}
