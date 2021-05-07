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
import java.util.Iterator;
import java.util.Map;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.SW8CarrierItem;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.json.simple.parser.ParseException;

import com.wm.app.b2b.server.BaseService;
import com.wm.app.b2b.server.InvokeState;
import com.wm.app.b2b.server.Service;
import com.wm.app.b2b.server.invoke.ServiceStatus;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class ServiceRuntimeInterceptor implements InstanceMethodsAroundInterceptor {
	
    @SuppressWarnings("rawtypes")
	@Override
    public void beforeMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final MethodInterceptResult result) {

        String serviceName = ServiceUtils.getServiceName((BaseService) allArguments[1]); 

        System.out.println("** IS AGENT ** AuditLogManager - before - " + serviceName);
       
        try {
        	process(objInst, method, (Iterator) allArguments[0], (BaseService) allArguments[1], (IData) allArguments[2], (ServiceStatus) allArguments[3]);
        } catch (ParseException e) {
        	// TODO Auto-generated catch block
        	e.printStackTrace();
        }
    }

    @Override
    public Object afterMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final Object ret) {

        String serviceName = ServiceUtils.getServiceName((BaseService) allArguments[1]); 

        if (((ServiceStatus) allArguments[3]).getStatus() == ServiceStatus.STATUS_FAIL) {
        	
        	System.out.println("** IS AGENT ** AuditLogManager - exception - " + serviceName);

            ServiceUtils.handleInterceptorException(((ServiceStatus) allArguments[3]).getThrownException());
            
        } else {
            System.out.println("** IS AGENT ** AuditLogManager - after - " + serviceName);

        	ServiceUtils.stopSpan(serviceName);
        }
        
        return ret;
    }

    @Override
    public void handleMethodException(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final Throwable t) {

        String serviceName = ServiceUtils.getServiceName((BaseService) allArguments[1]); 

        System.out.println("** IS AGENT ** AuditLogManager - exception - " + serviceName);

        ServiceUtils.handleInterceptorException(t);
    }

    private void process(final EnhancedInstance objInst, final Method method, @SuppressWarnings("rawtypes") Iterator chain, BaseService baseService, IData pipeline, ServiceStatus status) throws ParseException {
    	
        String serviceName = ServiceUtils.getServiceName(baseService); 
     
        if (ContextManager.isActive()) {
        	
        	if (serviceName.equals("pub.client:http")) {
            	
            	startHttpExitSpan(pipeline);
        		
            } else if (serviceName.equals("pub.publish:publish") || serviceName.equals("pub.publish:publishAndWait") || serviceName.equals("pub.publish:deliver")) {

            	startPublishExitSpan(pipeline);
            	
            } else {
            	auditViaSkywalking(serviceName, baseService, pipeline, status);

        	}
        } else if (baseService.getAuditOption() == BaseService.AUDIT_ENABLE && baseService.getAuditSettings().isCompleteAuditEnabled()) {
    			
        	auditViaSkywalking(serviceName, baseService, pipeline, status);
        }
    }
    
    protected void startHttpExitSpan(IData pipeline) throws ParseException {
    
    	// need to create an exit span 
		
		IDataCursor c = pipeline.getCursor();
		String url = IDataUtil.getString(c, "url");
    	IData headers = IDataUtil.getIData(c, "headers");

    	headers = ServiceUtils.startExitSpan(ContextManager.activeSpan().getOperationName(), headers, url);
    	
    	IDataUtil.put(c, "headers", headers);
    	c.destroy();
    }
    
    protected void startPublishExitSpan(IData pipeline) throws ParseException {
    
    	IDataCursor c = pipeline.getCursor();
		IData doc = IDataUtil.getIData(c, "document");
		String docType = IDataUtil.getString(c, "documentTypeName");
		c.destroy();
    	
    	IData headers = ServiceUtils.startExitSpan(ContextManager.activeSpan().getOperationName(), null, docType);
    	
    	TriggerTools.updatePublishedDocWithTransactionId(doc, headers);
    }
    
    /**
     * Entry point for end to end monitoring to allow service start to be logged if required
     * 
     * @return true if end to end monitoring is activated, false if not
     * @throws ParseException 
     */
    protected void auditViaSkywalking(String serviceName, BaseService baseService, IData pipeline, ServiceStatus status) throws ParseException {    	 

    	if (baseService.getAuditOption() == BaseService.AUDIT_ENABLE && baseService.getAuditSettings().isCompleteAuditEnabled()) {
	    		
	    	String rootContextId = ServiceUtils.getRootContextId(status); // will NOT force root context id to header value identified by WM_ENDTOEND_TRANSACTION_ID
	    	String parentContextId = ServiceUtils.getParentContextId(status); // will be same as root context id if this service is top level
			String customContextId = ServiceUtils.getCustomContextId();

	    	System.out.println("sw6 auditing start " + serviceName + " / " + rootContextId + " - " + rootContextId);

	    	String[][] businessDataKeys = baseService.getInputAuditFields();
	    		
	    	Map<String, Object> businessData = ServiceUtils.extractDataFromPipeline(businessDataKeys, pipeline);
	    		
	    	System.out.println("no of keys: " + businessData.size());
	    		
			System.out.println("thread ref: " + Thread.currentThread().getId());

	    	if (ContextManager.isActive()) {
	    		
	    		System.out.println("Creating local span under " + ContextManager.activeSpan().getOperationName() + " for " + serviceName);

	    		ServiceUtils.startLocalSpan(serviceName);
        		  
	    		
	    	} else {
	    		
	    		System.out.println("Creating entry span for " + serviceName);

	    		ServiceUtils.startEntrySpan(serviceName, customContextId, InvokeState.getCurrentState());
	    	}
	    	
	    	if (ContextManager.isActive()) {
	    		System.out.println("trace id " + ContextManager.getGlobalTraceId());
	    	}
    	}
    }
    
    private String generateOperationName(final EnhancedInstance objInst, final Method method) {
        return "Threading/" + objInst.getClass().getName() + "/" + method.getName();
    }

}
