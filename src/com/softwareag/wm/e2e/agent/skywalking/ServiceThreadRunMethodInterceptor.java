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

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import com.wm.lang.ns.NSName;


public class ServiceThreadRunMethodInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        
    	final Class<?>[] argumentsTypes, final MethodInterceptResult result) {
       
       // propagate fields from object to current thread
         
        String serviceName = generateOperationName(objInst, method);

    	if (objInst.getSkyWalkingDynamicField() != null) {
              
   		   System.out.println("** THREAD ** - start - " + serviceName);

   		   ServiceUtils.startLocalSpanFromContext(serviceName, objInst);   		   
		} else {
	   		   System.out.println("** THREAD ** - ignored - " + serviceName);
		}
    }

    @Override
    public Object afterMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final Object ret) {
					
    	if (ContextManager.isActive()) {
    		String serviceName = generateOperationName(objInst, method);

    		//ServiceUtils.stopSpan(serviceName);
			
    		System.out.println("** THREAD ** - end - " + serviceName);
    		ServiceUtils.asyncCompleted(serviceName, objInst);
    	}
    	
        return ret;
    }

    @Override
    public void handleMethodException(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final Throwable t) {

        System.out.println("** THREAD ** - exception - " + method.getName());

       
    }

    private String generateOperationName(final EnhancedInstance objInst, final Method method) {
    	
    	try {
			Field field = objInst.getClass().getDeclaredField("service");
			field.setAccessible(true);
			
			NSName ns = (NSName) field.get(objInst);
	        return "threading/" + ns.getFullName();

		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			return "threading/" + objInst.getClass().getCanonicalName();
		}
    }

}
