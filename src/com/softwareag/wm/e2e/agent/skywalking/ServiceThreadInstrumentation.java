/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.softwareag.wm.e2e.agent.skywalking;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.StaticMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.dependencies.net.bytebuddy.description.method.MethodDescription;
import org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.ElementMatchers;

import com.wm.app.b2b.server.ServiceThread;

import static org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.apache.skywalking.apm.dependencies.net.bytebuddy.matcher.ElementMatchers.any;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

public class ServiceThreadInstrumentation extends ClassEnhancePluginDefine {
	
	private static final String INTERCEPT_CLASS = "com.wm.app.b2b.server.ServiceThread";
    private static final String CALLABLE_CLASS_INTERCEPTOR = "com.softwareag.wm.e2e.agent.skywalking.ServiceThreadConstructorInterceptor";

    private static final String RUN_METHOD = "run";
    private static final String RUN_METHOD_INTERCEPTOR =     "com.softwareag.wm.e2e.agent.skywalking.ServiceThreadRunMethodInterceptor";
    
    private static final String REUSE_METHOD = "init";
    private static final String REUSE_METHOD_INTERCEPTOR =   "com.softwareag.wm.e2e.agent.skywalking.ServiceThreadInitMethodInterceptor";

    @Override
    protected ClassMatch enhanceClass() {
    	
    	System.out.println("** IS AGENT ** - Setting up ServiceThread agent");
    	       
    	return byName(INTERCEPT_CLASS);
    }

   @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
	   
	   return new ConstructorInterceptPoint[0];
	   
	   /* not required init below is called from constructor systematically
	    * 
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return any();
                }

                @Override
                public String getConstructorInterceptor() {
                	
                    return CALLABLE_CLASS_INTERCEPTOR;
                }
            }
        };     */
    }

   	@Override
   	public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
       return new InstanceMethodsInterceptPoint[] {
    		   new InstanceMethodsInterceptPoint() {
                   @Override
                   public ElementMatcher<MethodDescription> getMethodsMatcher() {
                       return named(RUN_METHOD);
                   }

                   @Override
                   public String getMethodsInterceptor() {
                       return RUN_METHOD_INTERCEPTOR;
                   }

                   @Override
                   public boolean isOverrideArgs() {
                       return false;
                   }
               },
    		   new InstanceMethodsInterceptPoint() {
               @Override
               public ElementMatcher<MethodDescription> getMethodsMatcher() {
                   return named(REUSE_METHOD);
               }

               @Override
               public String getMethodsInterceptor() {
                   return REUSE_METHOD_INTERCEPTOR;
               }

               @Override
               public boolean isOverrideArgs() {
                   return false;
               }
           }
       };
   	}

    @Override
    public StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
    	    	
        return new StaticMethodsInterceptPoint[0];
    }

    /*@Override
    public boolean isBootstrapInstrumentation() {
        return true;
    }*/
}