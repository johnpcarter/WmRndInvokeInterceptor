package com.softwareag.wm.e2e.agent.skywalking.uhm;

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.SW8CarrierItem;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * This class contains several methods to set various UHM specific span tags
 * after sanitizing them.
 * 
 * @author indgo
 *
 */
public final class Utils {

	private Utils() {
	}

	private static final ILog logger = LogManager.getLogger(Utils.class);

	public static final boolean IS_AGENT_LOADED = true;

	public static final String OP_NAME_TO_ENABLE_DEBUG_LOG = "SagUhmE2EEnableAgentDebugLog";

	public static final String OP_NAME_TO_DISABLE_DEBUG_LOG = "SagUhmE2EDisableAgentDebugLog";

	public static final int MAX_LOG_LEVEL_DEBUG_TIME_IN_MIN = 15;

	public static final int MIN_TO_MILLIS = 60 * 1000;

	/**
	 * Trims the given FQN to {@link UhmAgentConfigs#MAX_FQN_LENGTH} chars.
	 * 
	 * @param inFQN original fqn
	 * @return trimmed fqn if required
	 */
	public static String sanitizeFullyQualifiedName(String inFQN) {
		if (!StringUtil.isEmpty(inFQN) && inFQN.length() > 256) {
			return inFQN.substring(0, 256);
		}
		return inFQN;
	}

	/**
	 * Updates the given span by adding duration tag to it.
	 * 
	 * @param span      span to be updated
	 * @param startTime start time of the span
	 * @param endTime   end time of the span
	 */
	public static void addDuration(AbstractSpan span, long startTime, long endTime) {
		long duration = endTime - startTime;
		logger.debug("startTime: " + startTime + " endTime: " + endTime);
		addDuration(span, duration);
	}

	/**
	 * Updates the given span by adding duration tag to it.
	 * 
	 * @param span     span to be updated
	 * @param duration value to be updated
	 */
	public static void addDuration(AbstractSpan span, long duration) {
		logger.debug("duration: " + duration);
		span.tag(CustomTags.DURATION, String.valueOf(duration));
	}

	/**
	 * Extracts the operation name for the span from given FQN by reading only the
	 * last part of it.
	 * 
	 * @param fqn fqn to read the name from
	 * @return name of the operation
	 */
	public static String getOperationNameFromFQN(String fqn) {
		logger.debug("fqn: " + fqn);
		if (fqn.contains("/")) {
			fqn = fqn.substring(fqn.lastIndexOf('/') + 1).trim();
			logger.debug("Operation Name: " + fqn);
		}
		return fqn;
	}

	/**
	 * Creates a {@link ContextCarrier} object from the given sw6 key.
	 * 
	 * @param traceHeaderValue sw6 value to use
	 * @return {@link ContextCarrier} object
	 */
	public static ContextCarrier getContextCarrier(String traceHeaderValue) {
		ContextCarrier contextCarrier = new ContextCarrier();
		CarrierItem next = contextCarrier.items();
		if (traceHeaderValue != null) {
			while (next.hasNext()) {
				next = next.next();
				String key = next.getHeadKey();
				if (key.equalsIgnoreCase(SW8CarrierItem.HEADER_NAME))
					next.setHeadValue(traceHeaderValue);
			}
		}
		return contextCarrier;
	}

	/**
	 * Reads and returns the value of sw6 key from the given context carrier object.
	 * 
	 * @param contextCarrier context carrier object containing the sw6 key
	 * @return value of sw6
	 */
	public static String extractTracingHeaderFromCarrier(ContextCarrier contextCarrier) {
		if (null != contextCarrier) {
			CarrierItem next = contextCarrier.items();
			while (next.hasNext()) {
				next = next.next();
				String key = next.getHeadKey();
				if (key.equalsIgnoreCase(SW8CarrierItem.HEADER_NAME))
					return next.getHeadValue();
			}
		}
		return null;
	}

}