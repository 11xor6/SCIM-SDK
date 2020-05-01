package de.captaingoldfish.scim.sdk.client.builder;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.captaingoldfish.scim.sdk.client.ScimClientConfig;
import de.captaingoldfish.scim.sdk.client.constants.ResponseType;
import de.captaingoldfish.scim.sdk.client.http.ScimHttpClient;
import de.captaingoldfish.scim.sdk.client.response.ScimServerResponse;
import de.captaingoldfish.scim.sdk.client.setup.HttpServerMockup;
import de.captaingoldfish.scim.sdk.client.setup.scim.handler.UserHandler;
import de.captaingoldfish.scim.sdk.common.constants.EndpointPaths;
import de.captaingoldfish.scim.sdk.common.constants.HttpHeader;
import de.captaingoldfish.scim.sdk.common.constants.HttpStatus;
import de.captaingoldfish.scim.sdk.common.etag.ETag;
import de.captaingoldfish.scim.sdk.common.resources.User;
import de.captaingoldfish.scim.sdk.common.resources.complex.Meta;
import de.captaingoldfish.scim.sdk.common.response.ErrorResponse;


/**
 * author Pascal Knueppel <br>
 * created at: 13.12.2019 - 09:05 <br>
 * <br>
 */
public class DeleteBuilderTest extends HttpServerMockup
{

  /**
   * verifies simply that the request is setup correctly for simple cases
   */
  @Test
  public void testDeleteRequestWithMissingId()
  {
    ScimClientConfig scimClientConfig = new ScimClientConfig();
    ScimHttpClient scimHttpClient = new ScimHttpClient(scimClientConfig);
    try
    {
      new DeleteBuilder<>(getServerUrl(), EndpointPaths.USERS, scimClientConfig, User.class,
                          scimHttpClient).sendRequest();
      Assertions.fail("this point must not be reached");
    }
    catch (IllegalStateException ex)
    {
      Assertions.assertEquals("id must not be blank for delete-requests", ex.getMessage());
    }
  }

  /**
   * verifies simply that the request is setup correctly for simple cases
   */
  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  public void testSetEmptyId(String id)
  {
    ScimClientConfig scimClientConfig = new ScimClientConfig();
    ScimHttpClient scimHttpClient = new ScimHttpClient(scimClientConfig);
    try
    {
      new DeleteBuilder<>(getServerUrl(), EndpointPaths.USERS, scimClientConfig, User.class, scimHttpClient).setId(id);
      Assertions.fail("this point must not be reached");
    }
    catch (IllegalStateException ex)
    {
      Assertions.assertEquals("id must not be blank for delete-requests", ex.getMessage());
    }
  }

  /**
   * verifies simply that the request is setup correctly for simple cases
   */
  @Test
  public void testSimpleDeleteRequestSuccess()
  {
    final String id = UUID.randomUUID().toString();
    Meta meta = Meta.builder().created(Instant.now()).lastModified(Instant.now()).build();
    User user = User.builder().id(id).userName("goldfish").meta(meta).build();
    UserHandler userHandler = (UserHandler)scimConfig.getUserResourceType().getResourceHandlerImpl();
    userHandler.getInMemoryMap().put(id, user);

    ScimClientConfig scimClientConfig = new ScimClientConfig();
    ScimHttpClient scimHttpClient = new ScimHttpClient(scimClientConfig);
    ScimServerResponse<User> response = new DeleteBuilder<>(getServerUrl(), EndpointPaths.USERS, scimClientConfig,
                                                            User.class, scimHttpClient).setId(id).sendRequest();
    Assertions.assertFalse(response.getResource().isPresent());
    Assertions.assertEquals(ResponseType.DELETE, response.getResponseType());
    Assertions.assertEquals(HttpStatus.NO_CONTENT, response.getHttpStatus());
  }


  /**
   * verifies simply that the request is setup correctly for simple cases
   */
  @Test
  public void testSimpleDeleteRequestFail()
  {
    ScimClientConfig scimClientConfig = new ScimClientConfig();
    ScimHttpClient scimHttpClient = new ScimHttpClient(scimClientConfig);
    ScimServerResponse<User> response = new DeleteBuilder<>(getServerUrl(), EndpointPaths.USERS, scimClientConfig,
                                                            User.class, scimHttpClient)
                                                                                       .setId(UUID.randomUUID()
                                                                                                  .toString())
                                                                                       .sendRequest();
    Assertions.assertEquals(ResponseType.ERROR, response.getResponseType());
    Assertions.assertEquals(ErrorResponse.class, response.getScimResponse().get().getClass());
    Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getHttpStatus());
  }

  /**
   * sets the if-match-header in the request
   */
  @Test
  public void testIfMatchHeader()
  {
    ScimClientConfig scimClientConfig = new ScimClientConfig();
    ScimHttpClient scimHttpClient = new ScimHttpClient(scimClientConfig);

    final String version = "123456";

    AtomicBoolean wasCalled = new AtomicBoolean(false);
    setVerifyRequestAttributes((httpExchange, requestBody) -> {
      Assertions.assertEquals(ETag.parseETag(version).toString(),
                              httpExchange.getRequestHeaders().getFirst(HttpHeader.IF_MATCH_HEADER));
      wasCalled.set(true);
    });

    new DeleteBuilder<>(getServerUrl(), EndpointPaths.USERS, scimClientConfig, User.class,
                        scimHttpClient).setId(UUID.randomUUID().toString()).setETagForIfMatch(version).sendRequest();
    Assertions.assertTrue(wasCalled.get());
  }

  /**
   * sets the if-match-header in the request
   */
  @Test
  public void testIfMatchHeader2()
  {
    ScimClientConfig scimClientConfig = new ScimClientConfig();
    ScimHttpClient scimHttpClient = new ScimHttpClient(scimClientConfig);

    final String version = "123456";

    AtomicBoolean wasCalled = new AtomicBoolean(false);
    setVerifyRequestAttributes((httpExchange, requestBody) -> {
      Assertions.assertEquals(ETag.parseETag(version).toString(),
                              httpExchange.getRequestHeaders().getFirst(HttpHeader.IF_MATCH_HEADER));
      wasCalled.set(true);
    });

    new DeleteBuilder<>(getServerUrl(), EndpointPaths.USERS, scimClientConfig, User.class,
                        scimHttpClient).setId(UUID.randomUUID().toString())
                                       .setETagForIfMatch(ETag.parseETag(version))
                                       .sendRequest();
    Assertions.assertTrue(wasCalled.get());
  }

  /**
   * sets the if-match-header in the request
   */
  @Test
  public void testIfNoneMatchHeader()
  {
    ScimClientConfig scimClientConfig = new ScimClientConfig();
    ScimHttpClient scimHttpClient = new ScimHttpClient(scimClientConfig);

    final String version = "123456";

    AtomicBoolean wasCalled = new AtomicBoolean(false);
    setVerifyRequestAttributes((httpExchange, requestBody) -> {
      Assertions.assertEquals(ETag.parseETag(version).toString(),
                              httpExchange.getRequestHeaders().getFirst(HttpHeader.IF_NONE_MATCH_HEADER));
      wasCalled.set(true);
    });

    new DeleteBuilder<>(getServerUrl(), EndpointPaths.USERS, scimClientConfig, User.class,
                        scimHttpClient).setId(UUID.randomUUID().toString())
                                       .setETagForIfNoneMatch(version)
                                       .sendRequest();
    Assertions.assertTrue(wasCalled.get());
  }

  /**
   * sets the if-match-header in the request
   */
  @Test
  public void testIfNoneMatchHeader2()
  {
    ScimClientConfig scimClientConfig = new ScimClientConfig();
    ScimHttpClient scimHttpClient = new ScimHttpClient(scimClientConfig);

    final String version = "123456";

    AtomicBoolean wasCalled = new AtomicBoolean(false);
    setVerifyRequestAttributes((httpExchange, requestBody) -> {
      Assertions.assertEquals(ETag.parseETag(version).toString(),
                              httpExchange.getRequestHeaders().getFirst(HttpHeader.IF_NONE_MATCH_HEADER));
      wasCalled.set(true);
    });

    new DeleteBuilder<>(getServerUrl(), EndpointPaths.USERS, scimClientConfig, User.class,
                        scimHttpClient).setId(UUID.randomUUID().toString())
                                       .setETagForIfNoneMatch(ETag.parseETag(version))
                                       .sendRequest();
    Assertions.assertTrue(wasCalled.get());
  }

  /**
   * verifies that the response from the server can successfully be parsed if a not modified with an empty
   * response body is returned
   */
  @Test
  public void parseNotModifiedResponse()
  {
    UserHandler userHandler = (UserHandler)scimConfig.getUserResourceType().getResourceHandlerImpl();
    final String id = UUID.randomUUID().toString();
    final String version = UUID.randomUUID().toString();
    Meta meta = Meta.builder().created(Instant.now()).lastModified(Instant.now()).version(version).build();
    User user = User.builder().id(id).userName("goldfish").meta(meta).build();
    userHandler.getInMemoryMap().put(id, user);

    ScimClientConfig scimClientConfig = new ScimClientConfig();
    ScimHttpClient scimHttpClient = new ScimHttpClient(scimClientConfig);
    ScimServerResponse<User> response = new DeleteBuilder<>(getServerUrl(), EndpointPaths.USERS, scimClientConfig,
                                                            User.class, scimHttpClient).setETagForIfNoneMatch(version)
                                                                                       .setId(id)
                                                                                       .sendRequest();
    Assertions.assertEquals(ResponseType.ERROR, response.getResponseType());
    Assertions.assertEquals(HttpStatus.NOT_MODIFIED, response.getHttpStatus());
  }

  /**
   * verifies that the response from the server can successfully be parsed if a precondition failed with an
   * empty response body is returned
   */
  @Test
  public void parsePreConditionFailedResponse()
  {
    UserHandler userHandler = (UserHandler)scimConfig.getUserResourceType().getResourceHandlerImpl();
    final String id = UUID.randomUUID().toString();
    final String version = UUID.randomUUID().toString();
    Meta meta = Meta.builder().created(Instant.now()).lastModified(Instant.now()).version(version).build();
    User user = User.builder().id(id).userName("goldfish").meta(meta).build();
    userHandler.getInMemoryMap().put(id, user);

    ScimClientConfig scimClientConfig = new ScimClientConfig();
    ScimHttpClient scimHttpClient = new ScimHttpClient(scimClientConfig);
    ScimServerResponse<User> response = new DeleteBuilder<>(getServerUrl(), EndpointPaths.USERS, scimClientConfig,
                                                            User.class, scimHttpClient).setETagForIfMatch(version + "1")
                                                                                       .setId(id)
                                                                                       .sendRequest();
    Assertions.assertEquals(ResponseType.ERROR, response.getResponseType());
    Assertions.assertEquals(HttpStatus.PRECONDITION_FAILED, response.getHttpStatus());
  }


}
