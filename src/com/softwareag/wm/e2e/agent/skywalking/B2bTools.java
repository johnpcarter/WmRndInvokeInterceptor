package com.softwareag.wm.e2e.agent.skywalking;

import java.util.Vector;

import com.wm.app.tn.profile.ExtendedProfileField;
import com.wm.app.tn.profile.LookupStore;
import com.wm.app.tn.profile.ProfileStore;
import com.wm.app.tn.profile.ProfileStoreException;

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
}
