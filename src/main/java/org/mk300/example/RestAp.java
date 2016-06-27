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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.infinispan.Cache;
import org.jboss.logging.Logger;
import org.mk300.example.service.JdgServiceBean;
import org.mk300.example.service.KokoroServiceBean;

/**
 * 
 * @author mkobayas@redhat.com
 *
 */
@Stateless
@Path("/rest")
public class RestAp {
    private static final Logger log = Logger.getLogger(RestAp.class);
    
    @EJB
    private JdgServiceBean jdg;

    @EJB
    private KokoroServiceBean kokoro; // 夏目漱石「こころ」の各行を保持しているBean
    
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
}
