package org.sunbird.common;

import akka.dispatch.Futures;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.SimpleQueryStringBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortMode;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.HttpUtil;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.ProjectUtil.EsType;
import org.sunbird.common.models.util.PropertiesCache;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.dto.SearchDTO;
import org.sunbird.helper.ConnectionManager;
import scala.concurrent.Future;
import scala.concurrent.Promise;

/**
 * This class will provide all required operation for elastic search.
 *
 * @author github.com/iostream04
 */
public class ElasticSearchRestHighImpl implements ElasticSearchService {
  private static final String ERROR = "ERROR";
  private static final String _DOC = "_doc";

  /**
   * This method will put a new data entry inside Elastic search. identifier value becomes _id
   * inside ES, so every time provide a unique value while saving it.
   *
   * @param index String ES index name
   * @param identifier ES column identifier as an String
   * @param data Map<String,Object>
   * @return Future<String> which contains identifier for created data
   */
  @Override
  public Future<String> save(String index, String identifier, Map<String, Object> data) {
    long startTime = System.currentTimeMillis();
    Promise<String> promise = Futures.promise();
    ProjectLogger.log(
        "ElasticSearchUtilRest save method started at ==" + startTime + " for Index " + index,
        LoggerEnum.PERF_LOG);
    if (StringUtils.isBlank(identifier) || StringUtils.isBlank(index)) {
      ProjectLogger.log(
          "ElasticSearchRestHighImpl:save Identifier value is null or empty ,not able to save data.");
      promise.success(ERROR);
      return promise.future();
    }
    data.put("identifier", identifier);

    IndexRequest indexRequest = new IndexRequest(index, _DOC, identifier).source(data);

    ActionListener<IndexResponse> listener =
        new ActionListener<IndexResponse>() {
          @Override
          public void onResponse(IndexResponse indexResponse) {
            ProjectLogger.log(
                "ElasticSearchRestHighImpl:save Success for index : "
                    + index
                    + ", identifier :"
                    + identifier,
                LoggerEnum.INFO);

            promise.success(indexResponse.getId());
            ProjectLogger.log(
                "ElasticSearchRestHighImpl:save method end at =="
                    + System.currentTimeMillis()
                    + " for Index "
                    + index
                    + " ,Total time elapsed = "
                    + calculateEndTime(startTime),
                LoggerEnum.PERF_LOG);
          }

          @Override
          public void onFailure(Exception e) {
            promise.failure(e);
            ProjectLogger.log(
                "ElasticSearchRestHighImpl:save "
                    + "Error while saving "
                    + index
                    + " id : "
                    + identifier
                    + " with error :"
                    + e,
                LoggerEnum.ERROR.name());
            ProjectLogger.log(
                "ElasticSearchRestHighImpl:save method end at =="
                    + System.currentTimeMillis()
                    + " for INdex "
                    + index
                    + " ,Total time elapsed = "
                    + calculateEndTime(startTime),
                LoggerEnum.PERF_LOG);
          }
        };

    ConnectionManager.getRestClient().indexAsync(indexRequest, listener);

    return promise.future();
  }
  /**
   * This method will update data entry inside Elastic search, using identifier and provided data .
   *
   * @param index String ES index name
   * @param identifier ES column identifier as an String
   * @param data Map<String,Object>
   * @return true or false
   */
  @Override
  public Future<Boolean> update(String index, String identifier, Map<String, Object> data) {
    long startTime = System.currentTimeMillis();
    ProjectLogger.log(
        "ElasticSearchRestHighImpl:update method started at ==" + startTime + " for Index " + index,
        LoggerEnum.PERF_LOG);
    Promise<Boolean> promise = Futures.promise();
    ;

    if (!StringUtils.isBlank(index) && !StringUtils.isBlank(identifier) && data != null) {
      UpdateRequest updateRequest = new UpdateRequest(index, _DOC, identifier).doc(data);

      ActionListener<UpdateResponse> listener =
          new ActionListener<UpdateResponse>() {
            @Override
            public void onResponse(UpdateResponse updateResponse) {
              promise.success(true);
              if (updateResponse.getResult() == DocWriteResponse.Result.CREATED
                  || updateResponse.getResult() == DocWriteResponse.Result.UPDATED
                  || updateResponse.getResult() == DocWriteResponse.Result.NOOP) {
                ProjectLogger.log(
                    "ElasticSearchRestHighImpl:update  Success with upsert for index : "
                        + index
                        + ",identifier : "
                        + identifier,
                    LoggerEnum.INFO);
              }
              long elapsedTime = calculateEndTime(startTime);
              ProjectLogger.log(
                  "ElasticSearchRestHighImpl:update method end =="
                      + " for INdex "
                      + index
                      + " ,Total time elapsed = "
                      + elapsedTime,
                  LoggerEnum.PERF_LOG);
            }

            @Override
            public void onFailure(Exception e) {
              ProjectLogger.log(
                  "ElasticSearchRestHighImpl:update exception occured:" + e.getMessage(),
                  LoggerEnum.ERROR.name());
              promise.failure(e);
            }
          };
      ConnectionManager.getRestClient().updateAsync(updateRequest, listener);

    } else {
      ProjectLogger.log(
          "ElasticSearchRestHighImpl:update Requested data is invalid.", LoggerEnum.INFO.name());
      promise.failure(
          new ProjectCommonException(
              ResponseCode.invalidData.getErrorCode(),
              ResponseCode.invalidData.getErrorMessage(),
              ResponseCode.invalidData.getResponseCode()));
    }
    return promise.future();
  }

  /**
   * This method will provide data form ES based on incoming identifier. we can get data by passing
   * index and identifier values , or all the three
   *
   * @param type String
   * @param identifier String
   * @return Map<String,Object> or null
   */
  @Override
  public Future<Map<String, Object>> getDataByIdentifier(String index, String identifier) {
    long startTime = System.currentTimeMillis();
    Promise<Map<String, Object>> promise = Futures.promise();
    ProjectLogger.log(
        "ElasticSearchRestHighImpl:getDataByIdentifier method started at =="
            + startTime
            + " for Index "
            + index,
        LoggerEnum.PERF_LOG);

    GetRequest getRequest = new GetRequest(index, _DOC, identifier);

    ActionListener<GetResponse> listener =
        new ActionListener<GetResponse>() {
          @Override
          public void onResponse(GetResponse getResponse) {
            if (getResponse.isExists()) {
              Map<String, Object> sourceAsMap = getResponse.getSourceAsMap();
              if (MapUtils.isNotEmpty(sourceAsMap)) {
                promise.success(sourceAsMap);
                long elapsedTime = calculateEndTime(startTime);
                ProjectLogger.log(
                    "ElasticSearchRestHighImpl:getDataByIdentifier method end =="
                        + " for Index "
                        + index
                        + " ,Total time elapsed = "
                        + elapsedTime,
                    LoggerEnum.PERF_LOG);
              } else {
                promise.success(new HashMap<>());
              }
            }
          }

          @Override
          public void onFailure(Exception e) {
            ProjectLogger.log(
                "ElasticSearchRestHighImpl:getDataByIdentifier method Failed with error == " + e,
                LoggerEnum.INFO);
            promise.failure(e);
          }
        };

    ConnectionManager.getRestClient().getAsync(getRequest, listener);
    return promise.future();
  }

  /**
   * This method will remove data from ES based on identifier.
   *
   * @param index String
   * @param type String
   * @param identifier String
   */
  @Override
  public Future<Boolean> delete(String index, String identifier) {
    long startTime = System.currentTimeMillis();
    ProjectLogger.log(
        "ElasticSearchRestHighImpl:delete method started at ==" + startTime, LoggerEnum.PERF_LOG);
    Promise<Boolean> promise = Futures.promise();
    DeleteRequest delRequest = new DeleteRequest(index, _DOC, identifier);
    ActionListener<DeleteResponse> listener =
        new ActionListener<DeleteResponse>() {
          @Override
          public void onResponse(DeleteResponse deleteResponse) {
            if (deleteResponse.getResult() == DocWriteResponse.Result.NOT_FOUND) {
              ProjectLogger.log(
                  "ElasticSearchRestHighImpl:delete Async : Document  not found for index : "
                      + index
                      + " , identifier : "
                      + identifier,
                  LoggerEnum.INFO);
              promise.success(false);
            } else {
              promise.success(true);
            }
          }

          @Override
          public void onFailure(Exception e) {
            ProjectLogger.log(
                "ElasticSearchRestHighImpl:delete Async Failed due to error :" + e,
                LoggerEnum.INFO);
            promise.failure(e);
          }
        };

    ConnectionManager.getRestClient().deleteAsync(delRequest, listener);

    long elapsedTime = calculateEndTime(startTime);
    ProjectLogger.log(
        "ElasticSearchRestHighImpl:delete method end ==" + " ,Total time elapsed = " + elapsedTime,
        LoggerEnum.PERF_LOG);
    return promise.future();
  }

  /**
   * Method to perform the elastic search on the basis of SearchDTO . SearchDTO contains the search
   * criteria like fields, facets, sort by , filters etc. here user can pass single type to search
   * or multiple type or null
   *
   * @param type var arg of String
   * @return search result as Map.
   */
  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public Future<Map<String, Object>> search(SearchDTO searchDTO, String index) {
    long startTime = System.currentTimeMillis();

    ProjectLogger.log(
        "ElasticSearchRestHighImpl:search method started at ==" + startTime, LoggerEnum.PERF_LOG);
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    SearchRequest searchRequest = new SearchRequest(index);
    searchRequest.types(_DOC);

    // check mode and set constraints
    Map<String, Float> constraintsMap = ElasticSearchHelper.getConstraints(searchDTO);

    BoolQueryBuilder query = new BoolQueryBuilder();

    // add channel field as mandatory
    String channel = PropertiesCache.getInstance().getProperty(JsonKey.SUNBIRD_ES_CHANNEL);
    if (!(StringUtils.isBlank(channel) || JsonKey.SUNBIRD_ES_CHANNEL.equals(channel))) {
      query.must(
          ElasticSearchHelper.createMatchQuery(
              JsonKey.CHANNEL, channel, constraintsMap.get(JsonKey.CHANNEL)));
    }

    // apply simple query string
    if (!StringUtils.isBlank(searchDTO.getQuery())) {
      SimpleQueryStringBuilder sqsb = QueryBuilders.simpleQueryStringQuery(searchDTO.getQuery());
      if (CollectionUtils.isEmpty(searchDTO.getQueryFields())) {
        query.must(sqsb.field("all_fields"));
      } else {
        Map<String, Float> searchFields =
            searchDTO
                .getQueryFields()
                .stream()
                .collect(Collectors.<String, String, Float>toMap(s -> s, v -> 1.0f));
        query.must(sqsb.fields(searchFields));
      }
    }
    // apply the sorting
    if (searchDTO.getSortBy() != null && searchDTO.getSortBy().size() > 0) {
      for (Map.Entry<String, Object> entry : searchDTO.getSortBy().entrySet()) {
        if (!entry.getKey().contains(".")) {
          searchSourceBuilder.sort(
              entry.getKey() + ElasticSearchHelper.RAW_APPEND,
              ElasticSearchHelper.getSortOrder((String) entry.getValue()));
        } else {
          Map<String, Object> map = (Map<String, Object>) entry.getValue();
          Map<String, String> dataMap = (Map) map.get(JsonKey.TERM);
          for (Map.Entry<String, String> dateMapEntry : dataMap.entrySet()) {
            FieldSortBuilder mySort =
                new FieldSortBuilder(entry.getKey() + ElasticSearchHelper.RAW_APPEND)
                    .setNestedFilter(
                        new TermQueryBuilder(dateMapEntry.getKey(), dateMapEntry.getValue()))
                    .sortMode(SortMode.MIN)
                    .order(ElasticSearchHelper.getSortOrder((String) map.get(JsonKey.ORDER)));
            searchSourceBuilder.sort(mySort);
          }
        }
      }
    }

    // apply the fields filter
    searchSourceBuilder.fetchSource(
        searchDTO.getFields() != null
            ? searchDTO.getFields().stream().toArray(String[]::new)
            : null,
        searchDTO.getExcludedFields() != null
            ? searchDTO.getExcludedFields().stream().toArray(String[]::new)
            : null);

    // setting the offset
    if (searchDTO.getOffset() != null) {
      searchSourceBuilder.from(searchDTO.getOffset());
    }

    // setting the limit
    if (searchDTO.getLimit() != null) {
      searchSourceBuilder.size(searchDTO.getLimit());
    }
    // apply additional properties
    if (searchDTO.getAdditionalProperties() != null
        && searchDTO.getAdditionalProperties().size() > 0) {
      for (Map.Entry<String, Object> entry : searchDTO.getAdditionalProperties().entrySet()) {
        ElasticSearchHelper.addAdditionalProperties(query, entry, constraintsMap);
      }
    }

    // set final query to search request builder
    searchSourceBuilder.query(query);
    List finalFacetList = new ArrayList();

    if (null != searchDTO.getFacets() && !searchDTO.getFacets().isEmpty()) {
      searchSourceBuilder = addAggregations(searchSourceBuilder, searchDTO.getFacets());
    }
    ProjectLogger.log(
        "ElasticSearchRestHighImpl:search calling search builder======"
            + searchSourceBuilder.toString(),
        LoggerEnum.INFO.name());

    searchRequest.source(searchSourceBuilder);
    Promise<Map<String, Object>> promise = Futures.promise();

    ActionListener<SearchResponse> listener =
        new ActionListener<SearchResponse>() {
          @Override
          public void onResponse(SearchResponse response) {
            if (response.getHits() == null || response.getHits().getTotalHits() == 0) {
              promise.success(new HashMap<>());
            } else {
              Map<String, Object> responseMap =
                  ElasticSearchHelper.getSearchResponseMap(response, searchDTO, finalFacetList);
              long elapsedTime = calculateEndTime(startTime);
              ProjectLogger.log(
                  "ElasticSearchRestHighImpl:search method end "
                      + " ,Total time elapsed = "
                      + elapsedTime,
                  LoggerEnum.PERF_LOG);
              promise.success(responseMap);
            }
          }

          @Override
          public void onFailure(Exception e) {
            promise.failure(e);

            long elapsedTime = calculateEndTime(startTime);
            ProjectLogger.log(
                "ElasticSearchRestHighImpl:search method end   for Index "
                    + index
                    + " ,Total time elapsed = "
                    + elapsedTime,
                LoggerEnum.PERF_LOG);
            ProjectLogger.log(
                "ElasticSearchRestHighImpl:search method Failed with error :" + e,
                LoggerEnum.ERROR);
          }
        };

    ConnectionManager.getRestClient().searchAsync(searchRequest, listener);
    return promise.future();
  }

  /**
   * This method will do the health check of elastic search.
   *
   * @return boolean
   */
  @Override
  public Future<Boolean> healthCheck() {

    GetIndexRequest indexRequest = new GetIndexRequest().indices("user");
    Promise<Boolean> promise = Futures.promise();
    ActionListener<Boolean> listener =
        new ActionListener<Boolean>() {
          @Override
          public void onResponse(Boolean getResponse) {
            if (getResponse) {
              promise.success(getResponse);
            } else {
              promise.success(false);
            }
          }

          @Override
          public void onFailure(Exception e) {
            promise.failure(e);
            ProjectLogger.log("ElasticSearchRestHighImpl:healthCheck error " + e.getMessage(), e);
          }
        };
    ConnectionManager.getRestClient().indices().existsAsync(indexRequest, listener);

    return promise.future();
  }

  /**
   * Method to execute ES query with the limitation of size set to 0 Currently this is a rest call
   *
   * @param index ES indexName
   * @param type ES type
   * @param rawQuery actual query to be executed
   * @return ES response for the query
   */
  @SuppressWarnings("unchecked")
  @Override
  public Response searchMetricsData(String index, String rawQuery) {
    long startTime = System.currentTimeMillis();
    ProjectLogger.log(
        "ElasticSearchRestHighImpl:searchMetricsData method started at ==" + startTime,
        LoggerEnum.PERF_LOG);
    String baseUrl = null;
    if (!StringUtils.isBlank(System.getenv(JsonKey.SUNBIRD_ES_IP))) {
      String envHost = System.getenv(JsonKey.SUNBIRD_ES_IP);
      String[] host = envHost.split(",");
      baseUrl =
          "http://"
              + host[0]
              + ":"
              + PropertiesCache.getInstance().getProperty(JsonKey.ES_METRICS_PORT);
    } else {
      ProjectLogger.log(
          "ElasticSearchRestHighImpl:searchMerticData, ES URL from Properties file",
          LoggerEnum.INFO);
      baseUrl = PropertiesCache.getInstance().getProperty(JsonKey.ES_URL);
    }
    String requestURL = baseUrl + "/" + index + "/" + _DOC + "/" + "_search";
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    Map<String, Object> responseData = new HashMap<>();
    try {
      // TODO:Currently this is making a rest call but needs to be modified to make
      // the call using
      // ElasticSearch client
      String responseStr = HttpUtil.sendPostRequest(requestURL, rawQuery, headers);
      ObjectMapper mapper = new ObjectMapper();
      responseData = mapper.readValue(responseStr, Map.class);
    } catch (Exception e) {
      ProjectLogger.log(
          "ElasticSearchRestHighImpl:searchMerticData, error occured : " + e, LoggerEnum.ERROR);
      throw new ProjectCommonException(
          ResponseCode.unableToConnectToES.getErrorCode(),
          ResponseCode.unableToConnectToES.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    Response response = new Response();
    response.put(JsonKey.RESPONSE, responseData);
    long elapsedTime = calculateEndTime(startTime);
    ProjectLogger.log(
        "ElasticSearchRestHighImpl:searchMetricsData search method end == "
            + " ,Total time elapsed = "
            + elapsedTime,
        LoggerEnum.PERF_LOG);
    return response;
  }

  /**
   * This method will do the bulk data insertion.
   *
   * @param index String index name
   * @param type String type name
   * @param dataList List<Map<String, Object>>
   * @return boolean
   */
  @Override
  public Future<Boolean> bulkInsert(String index, List<Map<String, Object>> dataList) {
    long startTime = System.currentTimeMillis();
    ProjectLogger.log(
        "ElasticSearchRestHighImpl:bulkInsert method started at =="
            + startTime
            + " for Index "
            + index,
        LoggerEnum.PERF_LOG);
    BulkRequest request = new BulkRequest();
    Promise<Boolean> promise = Futures.promise();
    for (Map<String, Object> data : dataList) {
      request.add(new IndexRequest(index, _DOC).source(data));
    }
    ActionListener<BulkResponse> listener =
        new ActionListener<BulkResponse>() {
          @Override
          public void onResponse(BulkResponse bulkResponse) {
            Iterator<BulkItemResponse> responseItr = bulkResponse.iterator();
            if (responseItr != null) {
              promise.success(true);
              while (responseItr.hasNext()) {

                BulkItemResponse bResponse = responseItr.next();
                ProjectLogger.log(
                    "ElasticSearchRestHighImpl:Bulkinsert api response==="
                        + bResponse.getId()
                        + " "
                        + bResponse.isFailed(),
                    LoggerEnum.ERROR);
              }
            }
          }

          @Override
          public void onFailure(Exception e) {
            ProjectLogger.log("ElasticSearchRestHighImpl:Bulkinsert Bulk upload error block", e);
            promise.success(false);
          }
        };
    ConnectionManager.getRestClient().bulkAsync(request, listener);

    long elapsedTime = calculateEndTime(startTime);
    ProjectLogger.log(
        "ElasticSearchRestHighImpl:bulkInsert method end =="
            + " for Index "
            + index
            + " ,Total time elapsed = "
            + elapsedTime,
        LoggerEnum.PERF_LOG);
    return promise.future();
  }

  private static long calculateEndTime(long startTime) {
    return System.currentTimeMillis() - startTime;
  }

  private static SearchSourceBuilder addAggregations(
      SearchSourceBuilder searchSourceBuilder, List<Map<String, String>> facets) {
    long startTime = System.currentTimeMillis();
    ProjectLogger.log(
        "ElasticSearchUtilRest:addAggregations method started at ==" + startTime,
        LoggerEnum.PERF_LOG);
    Map<String, String> map = facets.get(0);
    for (Map.Entry<String, String> entry : map.entrySet()) {

      String key = entry.getKey();
      String value = entry.getValue();
      if (JsonKey.DATE_HISTOGRAM.equalsIgnoreCase(value)) {
        searchSourceBuilder.aggregation(
            AggregationBuilders.dateHistogram(key)
                .field(key + ElasticSearchHelper.RAW_APPEND)
                .dateHistogramInterval(DateHistogramInterval.days(1)));

      } else if (null == value) {
        searchSourceBuilder.aggregation(
            AggregationBuilders.terms(key).field(key + ElasticSearchHelper.RAW_APPEND));
      }
    }
    long elapsedTime = calculateEndTime(startTime);
    ProjectLogger.log(
        "ElasticSearchUtilRest:addAggregations method end =="
            + " ,Total time elapsed = "
            + elapsedTime,
        LoggerEnum.PERF_LOG);
    return searchSourceBuilder;
  }

  /**
   * This method will update data based on identifier.take the data based on identifier and merge
   * with incoming data then update it.
   *
   * @param index String
   * @param type String
   * @param identifier String
   * @param data Map<String,Object>
   * @return boolean
   */
  @Override
  public Future<Boolean> upsert(String index, String identifier, Map<String, Object> data) {
    long startTime = System.currentTimeMillis();
    Promise<Boolean> promise = Futures.promise();
    ProjectLogger.log(
        "ElasticSearchUtil upsert method started at ==" + startTime + " for INdex " + index,
        LoggerEnum.PERF_LOG);
    if (!StringUtils.isBlank(index)
        && !StringUtils.isBlank(identifier)
        && data != null
        && data.size() > 0) {

      IndexRequest indexRequest = new IndexRequest(index, _DOC, identifier).source(data);

      UpdateRequest updateRequest = new UpdateRequest(index, _DOC, identifier).upsert(indexRequest);

      ActionListener<UpdateResponse> listener =
          new ActionListener<UpdateResponse>() {
            @Override
            public void onResponse(UpdateResponse updateResponse) {
              promise.success(true);
              if (ElasticSearchHelper.upsertResults.contains(updateResponse.getResult())) {
                ProjectLogger.log(
                    "ElasticSearchUtilRest updateData  Success for index : "
                        + index
                        + ",identifier : "
                        + identifier,
                    LoggerEnum.INFO);
              }
              long elapsedTime = calculateEndTime(startTime);
              ProjectLogger.log(
                  "ElasticSearchUtilRest upsert method end =="
                      + " for Index "
                      + index
                      + " ,Total time elapsed = "
                      + elapsedTime,
                  LoggerEnum.PERF_LOG);
            }

            @Override
            public void onFailure(Exception e) {
              ProjectLogger.log(
                  "ElasticSearchRestHighImpl:upsert exception occured:" + e.getMessage(),
                  LoggerEnum.ERROR.name());
              promise.failure(e);
            }
          };
      ConnectionManager.getRestClient().updateAsync(updateRequest, listener);
      return promise.future();
    } else {
      ProjectLogger.log(
          "ElasticSearchRestHighImpl:upsert Requested data is invalid.", LoggerEnum.ERROR);
      promise.failure(
          new ProjectCommonException(
              ResponseCode.invalidData.getErrorCode(),
              ResponseCode.invalidData.getErrorMessage(),
              ResponseCode.invalidData.getResponseCode()));
      return promise.future();
    }
  }

  /**
   * This method will return map of objects on the basis of ids and type provided.
   *
   * @param ids List of String
   * @param fields List of String
   * @param Estype type
   * @param data Map<String,Object>
   * @return boolean
   */
  @Override
  public Future<Map<String, Map<String, Object>>> getEsResultByListOfIds(
      List<String> ids, List<String> fields, EsType typeToSearch) {
    Map<String, Object> filters = new HashMap<>();
    filters.put(JsonKey.ID, ids);

    SearchDTO searchDTO = new SearchDTO();
    searchDTO.getAdditionalProperties().put(JsonKey.FILTERS, filters);
    searchDTO.setFields(fields);

    Future<Map<String, Object>> resultF = search(searchDTO, typeToSearch.getTypeName());
    Map<String, Object> result =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);
    List<Map<String, Object>> esContent = (List<Map<String, Object>>) result.get(JsonKey.CONTENT);
    Promise<Map<String, Map<String, Object>>> promise = Futures.promise();
    promise.success(
        esContent
            .stream()
            .collect(
                Collectors.toMap(
                    obj -> {
                      return (String) obj.get("id");
                    },
                    val -> val)));
    return promise.future();
  }
}
