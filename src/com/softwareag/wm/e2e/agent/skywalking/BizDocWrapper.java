package com.softwareag.wm.e2e.agent.skywalking;

import java.util.HashMap;
import java.util.Map;

import org.apache.skywalking.apm.agent.core.context.ContextManager;

import com.wm.app.tn.profile.ProfileStore;
import com.wm.app.tn.profile.ProfileStoreException;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class BizDocWrapper {

	public static final String GLOBAL_TRACING_ID = "e2e";
	
	public static final String  DOC_ID = "DocumentID";
	public static final String 	DOC_SENDER_ID = "SenderID";
	public static final String 	DOC_RECEIVER_ID = "ReceiverID";
	public static final String  DOC_TYPE = "DocType";
	public static final String  DOC_TYPE_NAME = "TypeName";
	public static final String 	DOC_ATTRIBUTES = "Attributes";

	private IData _bizDoc;
	
	public BizDocWrapper(IData bizDoc) {
	
		this._bizDoc = bizDoc;
	}
	
	public String getBizDocType() {
    	
		IDataCursor c = _bizDoc.getCursor();
    	IData docType = IDataUtil.getIData(c, DOC_TYPE);
    	c.destroy();
    	
		c = docType.getCursor();
    	String name = IDataUtil.getString(c, DOC_TYPE_NAME);
    	c.destroy();
    	
    	return name;
    }
    
    public String getBizDocId() {
    	
    	IDataCursor c = _bizDoc.getCursor();
    	String id = IDataUtil.getString(c, DOC_ID);
    	c.destroy();
    	
    	return id;
    }
    
    public String getSenderId() {
    	
    	IDataCursor c = _bizDoc.getCursor();
    	String id = IDataUtil.getString(c, DOC_SENDER_ID);
    	c.destroy();
    	
    	return id;
    }
    
    public String getSenderName() {
    	
    	try {
			return ProfileStore.getProfileSummary(getSenderId()).getCorporationName();
		} catch (ProfileStoreException e) {
			return null;
		}
    }

    public String getReceiverId() {
    	
    	IDataCursor c = _bizDoc.getCursor();
    	String id = IDataUtil.getString(c, DOC_RECEIVER_ID);
    	c.destroy();
    	
    	return id;
    }
    
    public String getReceiverName() {
    	
    	try {
			return ProfileStore.getProfileSummary(getReceiverId()).getCorporationName();
		} catch (ProfileStoreException e) {
			return null;
		}
    }
 
    public Map<String, String> getGlobalTracingId() {
    	
    	return getBizDocAttributes(GLOBAL_TRACING_ID);
    }
    
    public void setGlobalTracingId(Map<String, String> vals) {
    	
    	IDataCursor c = _bizDoc.getCursor();
    	IData attribs = IDataUtil.getIData(c, DOC_ATTRIBUTES);
    	
    	if (attribs == null) {
    		attribs = IDataFactory.create();
    		IDataUtil.put(c, DOC_ATTRIBUTES, attribs);
    	}
    	
    	c.destroy();
    	
    	c = attribs.getCursor();
    	for (String key : vals.keySet()) {
    		IDataUtil.put(c, GLOBAL_TRACING_ID + ":" + key, vals.get(key));
    	}
    	
    	IDataUtil.put(c, GLOBAL_TRACING_ID + ":" + "id", ContextManager.getGlobalTraceId());
    	
    	c.destroy();
    }
    
    public Map<String, String> getBizDocAttributes(String filter) {
    	
    	Map<String, String> attributes = new HashMap<String, String>();

    	IDataCursor c = _bizDoc.getCursor();
    	IData attribs = IDataUtil.getIData(c, DOC_ATTRIBUTES);
    	c.destroy();

    	if (attribs != null) {
    		c = attribs.getCursor();
    	
    		c.first();
    	
    		do {
    			String key = c.getKey();
    			Object value = c.getValue();
    		
    			if (filter == null || key.startsWith(filter)) {
    				
    				if (filter != null && key.indexOf(filter) != -1) {
    					key = key.substring(key.indexOf(filter)+4);
    				}
    				
    				if (value instanceof String) {
    					attributes.put(key, (String) value);
    				} else if (value instanceof Boolean || value instanceof Integer || value instanceof Double || value instanceof Float) {
    					attributes.put(key, "" + value);
    				}
    			}
    			c.next();

    		} while(c.hasMoreData());
    	
    		c.destroy();
    	}
    	
    	return attributes;
    }
}
