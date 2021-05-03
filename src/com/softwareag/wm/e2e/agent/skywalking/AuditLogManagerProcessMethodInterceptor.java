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

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import com.wm.app.b2b.server.BaseService;
import com.wm.app.b2b.server.Service;
import com.wm.net.HttpHeader;

public class AuditLogManagerProcessMethodInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final MethodInterceptResult result) {

       System.out.println("** IS AGENT ** AuditLogManager - before - " + method.getName());
       
       //if (baseService.getAuditOption() == BaseService.AUDIT_ENABLE && baseService.getAuditSettings().isStartAuditEnabled()) {
    	   String serviceName = "bob";
       
    	   System.out.println("Creating entry span for " + serviceName);

    	   HttpHeader headers = Service.getHttpRequestHeader();
       
    	   ContextCarrier contextCarrier = new ContextCarrier();
    	   CarrierItem next = contextCarrier.items();
    	   while (next.hasNext()) {
    		   next = next.next();
    		   next.setHeadValue(headers.getFieldValue(next.getHeadKey()));
    	   }

    	   AbstractSpan span = ContextManager.createEntrySpan(serviceName, contextCarrier);
    	   // only if a valid span is created, should we populate the rest of span
		
    	   if (ContextManager.isActive()) {
    		   System.out.println("Created successfully");
    	   }
       //}
    }

    @Override
    public Object afterMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final Object ret) {

        System.out.println("** IS AGENT ** AuditLogManager - after - " + method.getName());

        if (ContextManager.isActive()) {
			ContextManager.stopSpan();
		}

        return ret;
    }

    @Override
    public void handleMethodException(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final Throwable t) {

        System.out.println("** IS AGENT ** AuditLogManager - exception - " + method.getName());

       
    }

    private String generateOperationName(final EnhancedInstance objInst, final Method method) {
        return "Threading/" + objInst.getClass().getName() + "/" + method.getName();
    }

}
