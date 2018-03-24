package org.sunbird.telemetry.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.telemetry.dto.Actor;
import org.sunbird.telemetry.dto.Context;
import org.sunbird.telemetry.dto.Producer;
import org.sunbird.telemetry.dto.Target;
import org.sunbird.telemetry.dto.Telemetry;
import org.sunbird.telemetry.util.lmaxdisruptor.TelemetryEvents;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * class to generate the telemetry events and convert the final event oject to
 * string ...
 */

public class TelemetryGenerator {

	private static ObjectMapper mapper = new ObjectMapper();

	private TelemetryGenerator() {
	}

	/**
	 * To generate api_access LOG telemetry JSON string.
	 *
	 * @param context
	 * @param params
	 * @return
	 */
	public static String audit(Map<String, Object> context, Map<String, Object> params) {
		if (!validateRequest(context, params)) {
			return "";
		}
		String actorId = (String) context.get(JsonKey.ACTOR_ID);
		String actorType = (String) context.get(JsonKey.ACTOR_TYPE);
		Actor actor = new Actor(actorId, actorType);
		Target targetObject = generateTargetObject((Map<String, Object>) params.get(JsonKey.TARGET_OBJECT));
		Context eventContext = getContext(context);
		// assign cdata into context from params correlated objects...
		if (params.containsKey(JsonKey.CORRELATED_OBJECTS)) {
			setCorrelatedDataToContext(params.get(JsonKey.CORRELATED_OBJECTS), eventContext);
		}

		// assign request id into context cdata ...
		String reqId = (String) context.get(JsonKey.REQUEST_ID);
		if (!StringUtils.isBlank(reqId)) {
			Map<String, Object> map = new HashMap<>();
			map.put(JsonKey.ID, reqId);
			map.put(JsonKey.TYPE, JsonKey.REQUEST);
			eventContext.getCdata().add(map);
		}

		Map<String, Object> edata = generateAuditEdata(params);

		Telemetry telemetry = new Telemetry(TelemetryEvents.AUDIT.getName(), actor, eventContext, edata, targetObject);
		return getTelemetry(telemetry);
	}

	private static void setCorrelatedDataToContext(Object correlatedObjects, Context eventContext) {
		ArrayList<Map<String, Object>> list = (ArrayList<Map<String, Object>>) correlatedObjects;
		ArrayList<Map<String, Object>> targetList = new ArrayList<>();
		if (null != list && !list.isEmpty()) {

			for (Map<String, Object> m : list) {
				Map<String, Object> map = new HashMap<>();
				map.put(JsonKey.ID, m.get(JsonKey.ID));
				map.put(JsonKey.TYPE, m.get(JsonKey.TYPE));
				targetList.add(map);
			}
		}
		eventContext.setCdata(targetList);
	}

	private static Target generateTargetObject(Map<String, Object> targetObject) {

		Target target = new Target((String) targetObject.get(JsonKey.ID), (String) targetObject.get(JsonKey.TYPE));
		if (targetObject.get(JsonKey.ROLLUP) != null) {
			target.setRollup((Map<String, String>) targetObject.get(JsonKey.ROLLUP));
		}
		return target;
	}

	private static Map<String, Object> generateAuditEdata(Map<String, Object> params) {

		Map<String, Object> edata = new HashMap<>();
		Map<String, Object> props = (Map<String, Object>) params.get(JsonKey.PROPS);
		edata.put(JsonKey.PROPS, props.entrySet().stream().map(entry -> entry.getKey()).collect(Collectors.toList()));

		Map<String, Object> target = (Map<String, Object>) params.get(JsonKey.TARGET_OBJECT);
		if (target.get(JsonKey.CURRENT_STATE) != null) {
			edata.put(JsonKey.STATE, target.get(JsonKey.CURRENT_STATE));
		}
		return edata;

	}

	private static Context getContext(Map<String, Object> context) {
		String channel = (String) context.get(JsonKey.CHANNEL);
		String env = (String) context.get(JsonKey.ENV);
		Producer producer = getProducer(context);
		Context eventContext = new Context(channel, env, producer);
		if (context.get(JsonKey.ROLLUP) != null && !((Map<String, String>) context.get(JsonKey.ROLLUP)).isEmpty()) {
			eventContext.setRollup((Map<String, String>) context.get(JsonKey.ROLLUP));
		}
		return eventContext;
	}

	private static Producer getProducer(Map<String, Object> context) {

		String id = (String) context.get(JsonKey.PDATA_ID);
		String pid = (String) context.get(JsonKey.PDATA_PID);
		String ver = (String) context.get(JsonKey.PDATA_VERSION);
		return new Producer(id, pid, ver);
	}

	private static String getTelemetry(Telemetry telemetry) {
		String event = "";
		try {
			event = mapper.writeValueAsString(telemetry);
		} catch (Exception e) {
			ProjectLogger.log(e.getMessage(), e);
		}
		return event;
	}

	public static String search(Map<String, Object> context, Map<String, Object> params) {

		if (!validateRequest(context, params)) {
			return "";
		}
		String actorId = (String) context.get(JsonKey.ACTOR_ID);
		String actorType = (String) context.get(JsonKey.ACTOR_TYPE);
		Actor actor = new Actor(actorId, actorType);

		Context eventContext = getContext(context);

		// assign request id into context cdata ...
		String reqId = (String) context.get(JsonKey.REQUEST_ID);
		if (!StringUtils.isBlank(reqId)) {
			Map<String, Object> map = new HashMap<>();
			map.put(JsonKey.ID, reqId);
			map.put(JsonKey.TYPE, JsonKey.REQUEST);
			eventContext.getCdata().add(map);
		}
		Map<String, Object> edata = generateSearchEdata(params);
		Telemetry telemetry = new Telemetry(TelemetryEvents.SEARCH.getName(), actor, eventContext, edata);
		return getTelemetry(telemetry);
	}

	private static Map<String, Object> generateSearchEdata(Map<String, Object> params) {

		Map<String, Object> edata = new HashMap<>();
		String type = (String) params.get(JsonKey.TYPE);
		String query = (String) params.get(JsonKey.QUERY);
		Map filters = (Map) params.get(JsonKey.FILTERS);
		Map sort = (Map) params.get(JsonKey.SORT);
		Long size = (Long) params.get(JsonKey.SIZE);
		List<Map> topn = (List<Map>) params.get(JsonKey.TOPN);

		edata.put(JsonKey.TYPE, type);
		if (null == query) {
			query = "";
		}
		edata.put(JsonKey.QUERY, query);
		edata.put(JsonKey.FILTERS, filters);
		edata.put(JsonKey.SORT, sort);
		edata.put(JsonKey.SIZE, size);
		edata.put(JsonKey.TOPN, topn);
		return edata;

	}

	public static String log(Map<String, Object> context, Map<String, Object> params) {

		if (!validateRequest(context, params)) {
			return "";
		}
		String actorId = (String) context.get(JsonKey.ACTOR_ID);
		String actorType = (String) context.get(JsonKey.ACTOR_TYPE);
		Actor actor = new Actor(actorId, actorType);

		Context eventContext = getContext(context);

		// assign request id into context cdata ...
		String reqId = (String) context.get(JsonKey.REQUEST_ID);
		if (!StringUtils.isBlank(reqId)) {
			Map<String, Object> map = new HashMap<>();
			map.put(JsonKey.ID, reqId);
			map.put(JsonKey.TYPE, JsonKey.REQUEST);
			eventContext.getCdata().add(map);
		}

		Map<String, Object> edata = generateLogEdata(params);
		Telemetry telemetry = new Telemetry(TelemetryEvents.LOG.getName(), actor, eventContext, edata);
		return getTelemetry(telemetry);

	}

	private static Map<String, Object> generateLogEdata(Map<String, Object> params) {

		Map<String, Object> edata = new HashMap<>();
		String logType = (String) params.get(JsonKey.LOG_TYPE);
		String logLevel = (String) params.get(JsonKey.LOG_LEVEL);
		String message = (String) params.get(JsonKey.MESSAGE);

		edata.put(JsonKey.TYPE, logType);
		edata.put(JsonKey.LEVEL, logLevel);
		edata.put(JsonKey.MESSAGE, message);

		edata.put(JsonKey.PARAMS, getParamsList(params, Arrays.asList(JsonKey.LOG_TYPE, JsonKey.LOG_LEVEL, JsonKey.MESSAGE)));
		return edata;
	}
	
	private static List<Map<String, Object>> getParamsList(Map<String, Object> params, List<String> ignore) {
		List<Map<String, Object>> paramsList = new ArrayList<Map<String, Object>>();
		if (null != params && !params.isEmpty()) {
			for (Entry<String, Object> entry : params.entrySet()) {
				if (!ignore.contains(entry.getKey())) {
					Map<String, Object> param = new HashMap<String, Object>();
					param.put(entry.getKey(), entry.getValue());
					paramsList.add(param);
				}
			}
		}
		return paramsList;
	}

	public static String error(Map<String, Object> context, Map<String, Object> params) {

		if (!validateRequest(context, params)) {
			return "";
		}
		String actorId = (String) context.get(JsonKey.ACTOR_ID);
		String actorType = (String) context.get(JsonKey.ACTOR_TYPE);
		Actor actor = new Actor(actorId, actorType);

		Context eventContext = getContext(context);

		// assign request id into context cdata ...
		String reqId = (String) context.get(JsonKey.REQUEST_ID);
		if (!StringUtils.isBlank(reqId)) {
			Map<String, Object> map = new HashMap<>();
			map.put(JsonKey.ID, reqId);
			map.put(JsonKey.TYPE, JsonKey.REQUEST);
			eventContext.getCdata().add(map);
		}

		Map<String, Object> edata = generateErrorEdata(params);
		Telemetry telemetry = new Telemetry(TelemetryEvents.ERROR.getName(), actor, eventContext, edata);
		return getTelemetry(telemetry);

	}

	private static Map<String, Object> generateErrorEdata(Map<String, Object> params) {
		Map<String, Object> edata = new HashMap<>();
		String error = (String) params.get(JsonKey.ERROR);
		String errorType = (String) params.get(JsonKey.ERR_TYPE);
		String stackTrace = (String) params.get(JsonKey.STACKTRACE);
		edata.put(JsonKey.ERROR, error);
		edata.put(JsonKey.ERR_TYPE, errorType);
		edata.put(JsonKey.STACKTRACE, stackTrace);
		return edata;
	}

	private static boolean validateRequest(Map<String, Object> context, Map<String, Object> params) {

		boolean flag = true;
		if (null == context || context.isEmpty() || params == null || params.isEmpty()) {
			flag = false;
		}
		return flag;

	}
}