package com.softwareag.wm.e2e.agent.skywalking.uhm;

import org.apache.skywalking.apm.agent.core.context.tag.StringTag;

/**
 * These are custom span tags which are defined by Software AG. Some of these
 * tags would be populated in the spans based on the product and environment
 * where they are created.
 * 
 * @author indgo
 *
 */
public final class CustomTags {
	
	public static final StringTag FULLY_QUALIFIED_NAME = new StringTag(20,
			SegmentTraceDataFieldKeys.FULLY_QUALIFIED_NAME);
	public static final StringTag CUSTOM_TRANSACTION_ID = new StringTag(256, SegmentTraceDataFieldKeys.CUSTOM_TRANSACTION_ID);
	
	public static final StringTag STAGE = new StringTag(21, SegmentTraceDataFieldKeys.STAGE);
	public static final StringTag ERROR_MSG = new StringTag(22, SegmentTraceDataFieldKeys.ERROR_MSG);
	public static final StringTag TRANSACTION_STATUS = new StringTag(23,
			SegmentTraceDataFieldKeys.TRANSACTION_STATUS);
	public static final StringTag TRANSACTION_ID = new StringTag(24, SegmentTraceDataFieldKeys.TRANSACTION_ID);
	public static final StringTag TENANT_ID = new StringTag(25, SegmentTraceDataFieldKeys.TENANT_ID);
	public static final StringTag COMPONENT = new StringTag(26, SegmentTraceDataFieldKeys.COMPONENT);
	public static final StringTag OPERATION_NAME = new StringTag(27, SegmentTraceDataFieldKeys.OPERATION_NAME);
	public static final StringTag LEVEL = new StringTag(29, SegmentTraceDataFieldKeys.LEVEL);
	public static final StringTag DURATION = new StringTag(30, SegmentTraceDataFieldKeys.DURATION);
	public static final StringTag PARENT_LANDSCAPE = new StringTag(31, SegmentTraceDataFieldKeys.PARENT_LANDSCAPE);
	public static final StringTag URL = new StringTag(32, SegmentTraceDataFieldKeys.URL);
	public static final StringTag REQUEST_ID = new StringTag(33, SegmentTraceDataFieldKeys.REQUEST_ID);
	
	public static final StringTag UM_CHANNEL = new StringTag(11, "um.channel");
}