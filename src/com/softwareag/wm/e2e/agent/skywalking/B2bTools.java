package com.softwareag.wm.e2e.agent.skywalking;

import java.util.Map;
import java.util.Vector;

import com.wm.app.b2b.server.ServiceException;
import com.wm.app.tn.profile.ExtendedProfileField;
import com.wm.app.tn.profile.LookupStore;
import com.wm.app.tn.profile.ProfileStore;
import com.wm.app.tn.profile.ProfileStoreException;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataUtil;

public class B2bTools {

	public static final String EXT_FIELD_GROUP = "E2E";
	public static final String EXT_FIELD_LOGGED_TRANSACTIONS = "log transactions";
	
	public static String getInternalIdForExternalId(String externalId, String idType) {
		
		try {
			return ProfileStore.getInternalID(externalId, LookupStore.getExternalIDType(idType));
		} catch (ProfileStoreException e) {
			return null;
		}
	}
	
	public static boolean partnerRequiresTransactionTracking(IData pipeline) {

		BizDocWrapper bizdoc = B2bTools.getBizdocFromPipeline(pipeline);
		
		if (bizdoc != null) {
			return partnerRequiresTransactionTracking(bizdoc.getSenderId());
		} else {
			return false;
		}
	}
	
	public static boolean partnerRequiresTransactionTracking(BizDocWrapper bizdoc) {

		return partnerRequiresTransactionTracking(bizdoc.getSenderId());
	}
	
	public static boolean partnerRequiresTransactionTracking(String internalId) {

		boolean isTraceReq = false;

		Vector<?> fields = ProfileStore.getExtendedFields(internalId, EXT_FIELD_GROUP);
					
		if (fields != null) {
			for (int i = 0; i < fields.size(); i++) {
				ExtendedProfileField r = (ExtendedProfileField) fields.get(i);
				
				System.out.println("attribute: " + r.getName() + " = " + r.getValue());
				
				if (r.getName().equalsIgnoreCase(EXT_FIELD_LOGGED_TRANSACTIONS)) {
					isTraceReq = r.getValue() != null && r.getValue().equals("yes");
					break;
				}
			}
		}
				
		return isTraceReq;
	}
	
	public static String getSenderIdFromPipeline(IData pipeline) {
	
		IDataCursor c = pipeline.getCursor();
    	IData sender = IDataUtil.getIData(c, "sender");
    	c.destroy();
    	
    	if (sender != null) {
    		
    		c = sender.getCursor();
    		String id = IDataUtil.getString(c, "ProfileID");
    		c.destroy();
    		
    		return id;
    		
    	} else {
    		return null;
    	}
	}
	
	public static Map<String, String> getBizdocSwHeaderFromPipeline(IData pipeline) {
		BizDocWrapper bizdoc = getBizdocFromPipeline(pipeline);
		
		if (bizdoc != null) {
			return bizdoc.getGlobalTracingId();
		} else {
			return null;
		}
	}
	
 	public static BizDocWrapper getBizdocFromPipeline(IData pipeline) {
	    
    	IDataCursor c = pipeline.getCursor();
    	IData bizDoc = IDataUtil.getIData(c, "bizdoc");
    	c.destroy();
    	
    	return bizDoc != null ? new BizDocWrapper(bizDoc) : null;
	}
	
	public static boolean reliableStatusNotOkay(IData pipeline) {
		
		IDataCursor c = pipeline.getCursor();
    	IData output = IDataUtil.getIData(c, "serviceOutput");
    	c.destroy();
		
    	if (output != null) {
    		c = output.getCursor();
        	String status = IDataUtil.getString(c, "status");
    		c.destroy();
    		
    		return status.equals("fail");
    	} else {
    		return false;
    	}
	}
	
	public static Exception reliableStatusError(IData pipeline) {
		
		IDataCursor c = pipeline.getCursor();
    	IData output = IDataUtil.getIData(c, "serviceOutput");
    	c.destroy();
		
    	if (output != null) {
    		c = output.getCursor();
        	String status = IDataUtil.getString(c, "status");
        	String statusMessage = IDataUtil.getString(c, "statusMessage");
    		c.destroy();
    		
    		if (status.equals("fail")) {
    			return new ReliableProcessingServiceException(statusMessage);
    		} else {
    			return null;
    		}
    	}
    	
		return null;
	}
	
	public static class ReliableProcessingServiceException extends ServiceException {
		
		/**
		 * 
		 */
		private static final long serialVersionUID = -3162460517977426406L;

		ReliableProcessingServiceException(String message) {
			super(message);
		}
	}
}
