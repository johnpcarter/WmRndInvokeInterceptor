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
import java.util.Map;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.json.simple.parser.ParseException;

import com.wm.app.b2b.server.BaseService;
import com.wm.app.b2b.server.InvokeState;
import com.wm.app.b2b.server.ServerAPI;
import com.wm.app.b2b.server.invoke.ServiceStatus;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class AuditLogManagerInterceptor implements InstanceMethodsAroundInterceptor {
	
	public static final StringTag CUSTOM_B2B_RULE = new StringTag(256, "b2b_processing_rule");

    @SuppressWarnings("rawtypes")
	@Override
    public void beforeMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final MethodInterceptResult result) {
 
        try {        	
        	BaseService baseService = (BaseService) allArguments[1];
        	IData pipeline =  (IData) allArguments[2];
        	ServiceStatus status = (ServiceStatus) allArguments[3];
        	
            String serviceName = SpanTools.getServiceName(baseService); 
            
            if (serviceName.equals("wm.EDIINT:receive")) {
            	        	
            	auditB2BEntryForEDIINT(pipeline);
            	
            } else if (serviceName.equals("wm.tn:receive")) {
            
            	auditB2BEntry(pipeline);

            } else if (serviceName.startsWith("wm.EDIINT.rules:") && !serviceName.equals("wm.EDIINT.rules:setProcessInfo")) {
            	
            	auditB2BRule(serviceName, pipeline);
            	
            } else if (serviceName.equals("wm.tn.route:routeBizdoc")) {
            	
            	// end point for all b2b documents ff, xml, edi and others to be written to TN DB, represents ironically an exitSpan for us
            	// as the the document will be restored asynchronous via TN task.
            	
            	auditB2bRouteBizdoc(serviceName, pipeline);
            	
            } else if (serviceName.startsWith("wm.tn.transport")) { 
            
            	auditB2bDelivery(serviceName, pipeline);

            } else if (ContextManager.isActive()) {
            
            	if (serviceName.equals("pub.client:http")) {
                	
                	startHttpExitSpan(serviceName, pipeline);
            		
                } else if (serviceName.equals("pub.publish:publish") || serviceName.equals("pub.publish:publishAndWait") || serviceName.equals("pub.publish:deliver")) {

                	startPublishExitSpan(serviceName, pipeline);
                	
                }  else if (serviceName.equals("pub.jms:send") || serviceName.equals("pub.jms:sendAndWait")) {

                	startJMSPublishExitSpan(serviceName, pipeline);
                } else if (serviceName.equals("wm.tn:log")) {
                	
                	auditB2bActivitylog(pipeline);
                }
            } 
            
            if (baseService.getAuditOption() == BaseService.AUDIT_ENABLE && baseService.getAuditSettings().isCompleteAuditEnabled()) {
                
            	if (B2bTools.getBizdocSwHeaderFromPipeline(pipeline) != null) {
            		auditB2BRule(serviceName, pipeline);
            	} else {
            		auditService(serviceName, baseService, pipeline, status);
            	}
            }
        } catch (ParseException e) {
        	// TODO Auto-generated catch block
        	ServerAPI.logError(e);
        }
    }

    @Override
    public Object afterMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final Object ret) {

        String serviceName = SpanTools.getServiceName((BaseService) allArguments[1]); 
        
        if (serviceName.equals("wm.tn:receive")) {
        	
        	// we cannot know if partner requires tracking until after the initial request has been processed i.e. sender id has been deterimed
        	// so we create a span and then discard it here if not required.
        	
        	String id = B2bTools.getSenderIdFromPipeline((IData) allArguments[2]);
        	
        	if (!B2bTools.partnerRequiresTransactionTracking(id)) {
        		ContextManager.discardActiveSpan();
        	}        	
        } 
        
    	BaseService baseService = (BaseService) allArguments[1];
    	IData pipeline =  (IData) allArguments[2];
    	ServiceStatus serviceStatus = (ServiceStatus) allArguments[3];
    	boolean isAudited = baseService.getAuditOption() == BaseService.AUDIT_ENABLE && baseService.getAuditSettings().isCompleteAuditEnabled(); 
    	        	
        String parentService = SpanTools.getServiceNameForActiveSpan();

        if (isAudited || isDeliveryService(serviceName)) {
        	
        	// services
        	
            if (serviceStatus.getStatus() == ServiceStatus.STATUS_FAIL || B2bTools.reliableStatusNotOkay(pipeline)) {
            	SpanTools.handleInterceptorException(((ServiceStatus) allArguments[3]).getThrownException());
            }
        		
            SpanTools.stopSpan(serviceName);
            
        } else if ((serviceName.equals("wm.tn:receive")
        		    || serviceName.equals("wm.EDIINT:receive")
        			|| serviceName.equals("wm.tn.route:routeBizdoc")
        			|| serviceName.startsWith("wm.EDIINT.rules")
        			|| serviceName.startsWith("wm.tn.transport")) && B2bTools.partnerRequiresTransactionTracking(pipeline)) {
        		
        	// b2b
        	
            if (((ServiceStatus) allArguments[3]).getStatus() == ServiceStatus.STATUS_FAIL) {
            	SpanTools.handleInterceptorException(((ServiceStatus) allArguments[3]).getThrownException());
            }
            
            SpanTools.stopSpan(serviceName);
        } /*else if (serviceName.equals("wm.tn.log") && ContextManager.isActive()) {
        	ServiceUtils.stopSpan(serviceName);
        }*/
        
        return ret;
    }

    @Override
    public void handleMethodException(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final Throwable t) {

        String serviceName = SpanTools.getServiceName((BaseService) allArguments[1]); 

        if (ContextManager.isActive()) {
            System.out.println("** IS AGENT ** AuditLogManager - exception - " + serviceName);
        	SpanTools.handleInterceptorException(t);
        }
    }
    
    private boolean isDeliveryService(String serviceName) {
    	
    	return serviceName.equals("pub.client:http") || serviceName.startsWith("pub.publish") || serviceName.startsWith("pub.jms:send")
    				|| serviceName.equals("pub.jms:sendAndWait");
    }
    
    protected void startHttpExitSpan(String serviceName, IData pipeline) throws ParseException {
    
    	// need to create an exit span 
		
		IDataCursor c = pipeline.getCursor();
		String url = IDataUtil.getString(c, "url");
    	IData headers = IDataUtil.getIData(c, "headers");

    	headers = SpanTools.startExitSpanWithHttpHeaders(serviceName, url, headers);
    	
    	IDataUtil.put(c, "headers", headers);
    	c.destroy();
    }
    
    protected void startPublishExitSpan(String serviceName, IData pipeline) throws ParseException {
    
    	IDataCursor c = pipeline.getCursor();
		IData doc = IDataUtil.getIData(c, "document");
		String docType = IDataUtil.getString(c, "documentTypeName");
		c.destroy();
    	
    	Map<String, String> headers = SpanTools.startExitSpan(serviceName, docType);
    	
    	TriggerTools.updatePublishDocWithGlobalTraceId(doc, headers);
    }
    
    protected void startJMSPublishExitSpan(String serviceName, IData pipeline) throws ParseException {
        
    	IDataCursor c = pipeline.getCursor();
		IData doc = IDataUtil.getIData(c, "JMSMessage");
		String docType = IDataUtil.getString(c, "documentTypeName");
		c.destroy();
    	
    	Map<String, String> headers = SpanTools.startExitSpan(serviceName, docType);
    	
    	TriggerTools.updateJMSPublishDocWithGlobalTraceId(doc, headers);
    }
    
    /**
     * Entry point for end to end monitoring to allow service start to be logged if required
     * 
     * @return true if end to end monitoring is activated, false if not
     * @throws ParseException 
     */
    protected void auditService(String serviceName, BaseService baseService, IData pipeline, ServiceStatus status) throws ParseException {    	 

    	String rootContextId = SpanTools.getRootContextId(status); // will NOT force root context id to header value identified by WM_ENDTOEND_TRANSACTION_ID
	    String parentContextId = SpanTools.getParentContextId(status); // will be same as root context id if this service is top level
		String customContextId = SpanTools.getCustomContextId();

	    String[][] businessDataKeys = baseService.getInputAuditFields();
	    		
	    Map<String, Object> businessData = SpanTools.extractDataFromPipeline(businessDataKeys, pipeline);
	    		
	    if (ContextManager.isActive()) {
	    			    			    		
	    	SpanTools.startLocalSpan(serviceName, SpanTools.omitPath(serviceName), customContextId, InvokeState.getCurrentState(), "IS");
         	
	    } else {
	    		
	    	SpanTools.startEntrySpan(serviceName, SpanTools.omitPath(serviceName), customContextId, InvokeState.getCurrentState(), B2bTools.getBizdocSwHeaderFromPipeline(pipeline), "IS");
	    }
    }
    
    protected void auditB2BRule(String service, IData pipeline) {
    	
    	BizDocWrapper bizdoc = B2bTools.getBizdocFromPipeline(pipeline);
    	
    	if (bizdoc != null) {
    		
    		if (ContextManager.isActive()) {
         		    			
             	SpanTools.startLocalSpan(service, bizdoc.getBizDocType(), bizdoc.getBizDocId(), InvokeState.getCurrentState(), "b2b");

            } else if (B2bTools.partnerRequiresTransactionTracking(bizdoc)) {
             		             	
            	SpanTools.startEntrySpan(service, bizdoc.getBizDocType(), bizdoc.getBizDocId(), InvokeState.getCurrentState(), bizdoc.getGlobalTracingId(), "b2b");
         	}
    	}
    }
 
    protected void auditB2BEntryForEDIINT(IData pipeline) throws ParseException {
    
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
        		
        	SpanTools.startLocalSpan("wm.EDIINT:receive", "EDIINT", id, InvokeState.getCurrentState(), "b2b");

        } else {
        		
        	String t = B2bTools.getInternalIdForExternalId(from, type);
        	
        	if (t != null && B2bTools.partnerRequiresTransactionTracking(t)) {
	    		SpanTools.startEntrySpan("wm.EDIINT:receive", "EDIINT", id, InvokeState.getCurrentState(), null, "b2b");
        	}
    	}
    }
    
    protected void auditB2BEntry(IData pipeline) throws ParseException {
    	
    	// This will get discarded in the after() method if the TN profile is not flagged for traced via discard()
    	
    	IDataCursor c = pipeline.getCursor();
    	IData tnParms = IDataUtil.getIData(c, "TN_parms");
    	
    	if (tnParms == null) {
    		tnParms = IDataFactory.create();
    		IDataUtil.put(c, "TN_parms", tnParms);
    	}
    	
    	c.destroy();
    	
    	c = tnParms.getCursor();
    	IDataUtil.put(c,  "clearTNObjects", false);
    	c.destroy();
    	
		SpanTools.startEntrySpan("wm.tn:receive", "EDIINT", null, InvokeState.getCurrentState(), null, "b2b");
    }
    
    protected void auditB2bActivitylog(IData pipeline) {
    	
    	IDataCursor c = pipeline.getCursor();
    	String entryType = IDataUtil.getString(c, "entryType");
    	String briefMessage = IDataUtil.getString(c, "briefMessage");
    	String fullMessage = IDataUtil.getString(c, "fullMessage");
    	c.destroy();
    	
		SpanTools.log(briefMessage, fullMessage);

    	c.destroy();
    }
    
    protected void auditB2bDelivery(String serviceName, IData pipeline) throws ParseException {
    	
    	IDataCursor c = pipeline.getCursor();
    	String transportService = IDataUtil.getString(c, "serviceName");
    	c.destroy();

    	BizDocWrapper bizdoc = B2bTools.getBizdocFromPipeline(pipeline);

    	if (ContextManager.isActive()) {
    		SpanTools.startExitSpan(serviceName, transportService);
    	}
    }
    
    protected void auditB2bRouteBizdoc(String serviceName, IData pipeline) throws ParseException {
    	
    	BizDocWrapper bdoc = B2bTools.getBizdocFromPipeline(pipeline);
    	
    	if (ContextManager.isActive()) {
    		      		
    		Map<String, String> vals = SpanTools.startExitSpan(serviceName, bdoc.getBizDocType());

    		bdoc.setGlobalTracingId(vals);

    	} else if (bdoc.getGlobalTracingId() != null) { // this should not happen!
    		    		    		    		
    		Map<String, String> vals = SpanTools.startExitSpan(serviceName, bdoc.getBizDocType());
    		
    		bdoc.setGlobalTracingId(vals);
    		    		    		
    	} else if (B2bTools.partnerRequiresTransactionTracking(bdoc)) {
    		    		
    		// nor this
    		
    		Map<String, String> vals = SpanTools.startExitSpan(serviceName, bdoc.getBizDocType());
    		
    		bdoc.setGlobalTracingId(vals);
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
