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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;

/**
 * 
 * @author mkobayas@redhat.com
 *
 */
@Startup
@Singleton
@Lock(LockType.READ)
public class KokoroServiceBean {

    private List<String> lines;
    
    @PostConstruct
    void init() {
        try (InputStream in = KokoroServiceBean.class.getClassLoader().getResourceAsStream("kokoro.txt");
                BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8")
                        )){
            lines = new ArrayList<>();
            String line = null;
            while((line = br.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }
    
    public String get(int lineNumber) {
        int l = lineNumber % lines.size();
        return lines.get(l);
    }
    
    public String getRandom() {
        int l = (int)(Math.random()*lines.size());
        return lines.get(l);
    }
}
