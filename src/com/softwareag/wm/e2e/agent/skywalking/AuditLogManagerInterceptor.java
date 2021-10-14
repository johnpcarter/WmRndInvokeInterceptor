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
import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.json.simple.parser.ParseException;

import com.wm.app.b2b.server.BaseService;
import com.wm.app.b2b.server.InvokeState;
import com.wm.app.b2b.server.invoke.ServiceStatus;
import com.wm.app.tn.db.BizDocStore;
import com.wm.app.tn.db.DatastoreException;
import com.wm.app.tn.doc.BizDocEnvelope;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataUtil;

public class AuditLogManagerInterceptor implements InstanceMethodsAroundInterceptor {
	
	public static final StringTag CUSTOM_B2B_RULE = new StringTag(256, "b2b_processing_rule");

    @SuppressWarnings("rawtypes")
	@Override
    public void beforeMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final MethodInterceptResult result) {
 
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
    	
       /* if (ServiceUtils.getFullyQualifiedName().startsWith("threading")) {
    		return ret;
    	}*/
        
        if (((ServiceStatus) allArguments[3]).getStatus() == ServiceStatus.STATUS_FAIL) {
        	
    		//System.out.println("** IS AGENT ** AuditLogManager - after - " + serviceName + " / " + ((ServiceStatus) allArguments[3]).getThrownException());

    		if (ServiceUtils.getFullyQualifiedName() != null && (ServiceUtils.getFullyQualifiedName().equals("wm.EDIINT:receive") || ServiceUtils.getFullyQualifiedName().equals("wm.tn.route:routeBizdoc"))) {
            	
            // EDI is a special case, quite a lot can go wrong before the routeBizDoc services gets called, do we need to report it if we have a sw8 param ??

            	if (ServiceUtils.getFullyQualifiedName().contentEquals(serviceName)) {
            		ServiceUtils.handleInterceptorException(((ServiceStatus) allArguments[3]).getThrownException());
                	ServiceUtils.stopSpan(serviceName);
            	}
            } else {
            	ServiceUtils.handleInterceptorException(((ServiceStatus) allArguments[3]).getThrownException());
            	ServiceUtils.stopSpan(serviceName);
            }
        	            
        } else if (serviceName.equals(ServiceUtils.getFullyQualifiedName())) {
        		//System.out.println("** IS AGENT ** AuditLogManager - after - " + serviceName);

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
     
        if (serviceName.equals("wm.EDIINT:receive")) {
        	        	
           // System.out.println("** IS AGENT ** AuditLogManager - before - " + serviceName);

        	auditB2BEDIINtViaSkywalking(pipeline);
        	
        } else if (serviceName.equals("wm.tn.route:routeBizdoc")) {
        	
        	// entry point for all b2b documents ff, xml, edi and others
        	
           // System.out.println("** IS AGENT ** AuditLogManager - before - " + serviceName);

        	auditB2bRoutingViaSkywalking(pipeline, serviceName);
        	
        } else if (serviceName.equals("wm.EDIINT.rules:processPayload")) {

            //System.out.println("** IS AGENT ** AuditLogManager - before - " + serviceName);

        	auditB2bProcessingRule(pipeline, serviceName);
        	
        } else if (serviceName.equals("wm.tn:log")) {
        	
        	auditB2bActivitylog(pipeline);
        } else if (serviceName.equals("wm.tn.delivery:deliver")) { 
        
        	auditB2bDelivery(pipeline);

        } else if (ContextManager.isActive()) {
        
        	if (serviceName.equals("pub.client:http")) {
            	
            	startHttpExitSpan(pipeline);
        		
            } else if (serviceName.equals("pub.publish:publish") || serviceName.equals("pub.publish:publishAndWait") || serviceName.equals("pub.publish:deliver")) {

            	startPublishExitSpan(pipeline);
            	
            } else {
            	
               // System.out.println("** IS AGENT ** AuditLogManager - before - " + serviceName);

            	auditViaSkywalking(serviceName, baseService, pipeline, status);

        	}
        } else if (baseService.getAuditOption() == BaseService.AUDIT_ENABLE && baseService.getAuditSettings().isCompleteAuditEnabled()) {
    			
            //System.out.println("** IS AGENT ** AuditLogManager - before - " + serviceName);
            
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

	    	//System.out.println("sw6 auditing start " + serviceName + " / " + rootContextId + " - " + customContextId);

	    	String[][] businessDataKeys = baseService.getInputAuditFields();
	    		
	    	Map<String, Object> businessData = ServiceUtils.extractDataFromPipeline(businessDataKeys, pipeline);
	    		
	    	if (ContextManager.isActive()) {
	    		
	    		AbstractSpan span = ContextManager.activeSpan();
	    			    		
	    		ServiceUtils.startLocalSpan(ServiceUtils.omitPath(serviceName), serviceName, customContextId, InvokeState.getCurrentState(), "Integration");
         		
	    	} else {
	    		
	    		ServiceUtils.startEntrySpan(ServiceUtils.omitPath(serviceName), serviceName, pipeline, customContextId, InvokeState.getCurrentState(), "Integration");
	    	}
    	}
    }
    
    protected void auditB2BEDIINtViaSkywalking(IData pipeline) throws ParseException {
    
    	IDataCursor c = pipeline.getCursor();
    	String id = IDataUtil.getString(c, "Message-ID");
    	String from = IDataUtil.getString(c, "AS2-From");
    	String to = IDataUtil.getString(c, "AS2-To");
    	String type = "EDIINT AS2";
    			
    	if (from == null) {
    		type = "EDIINT AS3";
        	from = IDataUtil.getString(c, "AS3-From");
    	}
    	
    	if (to == null) {
        	to = IDataUtil.getString(c, "AS3-To");
    	}
    	
    	c.destroy();
    	
    	        	
        if (ContextManager.isActive()) {
        		
        	ServiceUtils.startLocalSpan("EDIINT", "wm.EDIINT:receive", id, InvokeState.getCurrentState(), "b2b");

        } else {
        		
        	String t = B2bTools.getInternalIdForExternalId(from, type);
        	
        	if (t != null && B2bTools.partnerRequiresTransactionTracking(t)) {
	    		ServiceUtils.startEntrySpan("EDIINT", "wm.EDIINT:receive", pipeline, id, InvokeState.getCurrentState(), "b2b");
        	}
    	}
    }
    
    protected void auditB2bProcessingRule(IData pipeline, String serviceName) {
    
    
    	if (ContextManager.isActive()) {
    		
    		IDataCursor c = pipeline.getCursor();
        	String internalId = IDataUtil.getString(c, "internalId");
        	c.destroy();
        	
        	try {
				BizDocEnvelope bdoc = BizDocStore.getDocument(internalId, false);				
	    		ServiceUtils.startLocalSpan(bdoc.getDocType().getName(), serviceName, bdoc.getDocumentId(), InvokeState.getCurrentState(), "b2b");

			} catch (DatastoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    }
    
    protected void auditB2bActivitylog(IData pipeline) {
    	
    	IDataCursor c = pipeline.getCursor();
    	String entryType = IDataUtil.getString(c, "entryType");
    	String briefMessage = IDataUtil.getString(c, "briefMessage");
    	String fullfMessage = IDataUtil.getString(c, "fullMessage");
    	c.destroy();
    	
		ServiceUtils.startLocalSpan(briefMessage, "wm.tn:log", null, InvokeState.getCurrentState(), "b2b");

    	c.destroy();
    }
    
    protected void auditB2bDelivery(IData pipeline) {
    	
    	IDataCursor c = pipeline.getCursor();
    	String serviceName = IDataUtil.getString(c, "serviceName");
    	c.destroy();

    	BizDocWrapper bizdoc = ServiceUtils.getBizdocFromPipeline(pipeline);

    	if (ContextManager.isActive()) {
    		ServiceUtils.startLocalSpan(serviceName, "wm.tn.delivery:deliver", bizdoc.getBizDocId(), InvokeState.getCurrentState(), "b2b");
    	} else {
    		ServiceUtils.startEntrySpan(serviceName, "wm.tn.delivery:deliver", pipeline, bizdoc.getBizDocId(), InvokeState.getCurrentState(), "b2b");
    	}
    }
    
    protected void auditB2bRoutingViaSkywalking(IData pipeline, String serviceName) {
    	
    	BizDocWrapper bdoc = ServiceUtils.getBizdocFromPipeline(pipeline);
    	
    	if (ContextManager.isActive()) {
    		ServiceUtils.startLocalSpan(bdoc.getBizDocType(), serviceName, bdoc.getBizDocId(), InvokeState.getCurrentState(), "b2b");
    		      		
    		bdoc.setGlobalTracingId(ServiceUtils.getGlobalTraceId());

    	} else if (ServiceUtils.getGlobalTraceId(bdoc) != null) {
    		
    		ServiceUtils.startEntrySpan(bdoc.getBizDocType(), serviceName, pipeline, bdoc.getBizDocId(), InvokeState.getCurrentState(), "b2b");
    		    		
    	} else if (B2bTools.partnerRequiresTransactionTracking(bdoc)) {
    		
    		// check partner settings to see if they want e2e monitoring or not
    		ServiceUtils.startEntrySpan(bdoc.getBizDocType(), serviceName, pipeline, bdoc.getBizDocId(), InvokeState.getCurrentState(), "b2b");
    		
    		// update bizdoc with global tracing id
    		
    		bdoc.setGlobalTracingId(ServiceUtils.getGlobalTraceId());
    	}
    	
    	// flag rule name if provided
    	
    	IDataCursor c = pipeline.getCursor();
    	IData parms = IDataUtil.getIData(c, "TN_Parms");    	
    	c.destroy();
    	
    	
    	if (parms != null) {
    		c = parms.getCursor();
    		if (IDataUtil.getString(c, "processingRuleName") != null) {
    			String rname = IDataUtil.getString(c, "processingRuleName");
    			ContextManager.activeSpan().tag(CUSTOM_B2B_RULE, rname);
    		}
    		c.destroy();
    	}
    }
    
    private String generateOperationName(final EnhancedInstance objInst, final Method method) {
        return "Threading/" + objInst.getClass().getName() + "/" + method.getName();
    }

}
