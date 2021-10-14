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

import java.lang.reflect.Method;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.json.simple.parser.ParseException;

import com.wm.data.IData;
import com.wm.msg.SimpleCondition;

public class TriggerInvokeServiceMethodInterceptor implements InstanceMethodsAroundInterceptor {


    @Override
    public void beforeMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final MethodInterceptResult result) {

    	SimpleCondition c = (SimpleCondition) allArguments[0];
    	
    	System.out.println("** IS AGENT ** Trigger - before - " + getOperationName(c));
       
		System.out.println("thread ref: " + Thread.currentThread().getId());

    	String transactionId = TriggerTools.getTransactionId((IData) allArguments[1]); 

    	if (transactionId != null) {
    		System.out.println("Creating entry span for trigger");

    		try {
    			ServiceUtils.startEntrySpan(getOperationName(c), transactionId);
    			
    			if (ContextManager.isActive()) {
    				System.out.println("trace id is " + ContextManager.getGlobalTraceId());
    			}
    		} catch (ParseException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}
    }

    @Override
    public Object afterMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final Object ret) {

    	SimpleCondition c = (SimpleCondition) allArguments[0];
    	
    	System.out.println("** IS AGENT ** Trigger - after - " + getOperationName(c));
       
        ServiceUtils.stopSpan(getOperationName(c));

        return ret;
    }

    @Override
    public void handleMethodException(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final Throwable t) {

        System.out.println("** IS AGENT ** ServiceThread - exception - " + method.getName());

       
    }

    private String getOperationName(SimpleCondition triggerCondition) {
    	
    	return triggerCondition.getServiceName().getFullName();
    }

}
