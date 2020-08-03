package de.captaingoldfish.scim.sdk.keycloak.entities;

import java.io.Serializable;
import java.time.Instant;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import org.keycloak.models.utils.KeycloakModelUtils;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * @author Pascal Knueppel
 * @since 02.08.2020
 */
@NoArgsConstructor
@Data
@Entity
@Table(name = "SCIM_SERVICE_PROVIDER")
@NamedQueries({@NamedQuery(name = "getScimServiceProvider", query = "SELECT sp FROM ScimServiceProviderEntity sp WHERE sp.realmId = :realmId")})
public class ScimServiceProviderEntity implements Serializable
{

  private static final long serialVersionUID = -6739263204338667208L;

  /**
   * primary key
   */
  @Id
  @Column(name = "ID")
  @Access(AccessType.PROPERTY) // we do this because relationships often fetch id, but not entity. This avoids an extra
                               // SQL
  @Setter(AccessLevel.PROTECTED)
  private String id = KeycloakModelUtils.generateId();

  /**
   * id of the owning realm<br>
   * <b>NOTE:</b><br>
   * this column wasn't set as foreign key to make realm deletion easier
   */
  @Column(name = "REALM_ID", unique = true)
  private String realmId;

  /**
   * if filtering should be supported
   */
  @Column(name = "FILTER_SUPPORTED")
  private boolean filterSupported;

  /**
   * the maximum number of results the API may return in a list request no matter if a filter was set or not
   */
  @Column(name = "FILTER_MAX_RESULTS")
  private int filterMaxResults;

  /**
   * if sorting should be supported or not
   */
  @Column(name = "SORT_SUPPORTED")
  private boolean sortSupported;

  /**
   * if patch should be supported or not
   */
  @Column(name = "PATCH_SUPPORTED")
  private boolean patchSupported;

  /**
   * if changing the password should be supported or not
   */
  @Column(name = "CHANGE_PASSWORD_SUPPORTED")
  private boolean changePasswordSupported;

  /**
   * if bulk requests should be supported or not
   */
  @Column(name = "BULK_SUPPORTED")
  private boolean bulkSupported;

  /**
   * the maximum number of operations that is accepted within a bulk request
   */
  @Column(name = "BULK_MAX_OPERATIONS")
  private int bulkMaxOperations;

  /**
   * the maximum payload size of a bulk request
   */
  @Column(name = "BULK_MAX_PAYLOAD_SIZE")
  private long bulkMaxPayloadSize;

  /**
   * the timestamp when this configuration was created
   */
  @Column(name = "CREATED")
  private Instant created;

  /**
   * the timestamp when this configuration was last modified
   */
  @Column(name = "LAST_MODIFIED")
  private Instant lastModified;

  @Builder
  public ScimServiceProviderEntity(String realmId,
                                   boolean filterSupported,
                                   int filterMaxResults,
                                   boolean sortSupported,
                                   boolean patchSupported,
                                   boolean changePasswordSupported,
                                   boolean bulkSupported,
                                   int bulkMaxOperations,
                                   long bulkMaxPayloadSize,
                                   Instant created,
                                   Instant lastModified)
  {
    this.realmId = realmId;
    this.filterSupported = filterSupported;
    this.filterMaxResults = filterMaxResults;
    this.sortSupported = sortSupported;
    this.patchSupported = patchSupported;
    this.changePasswordSupported = changePasswordSupported;
    this.bulkSupported = bulkSupported;
    this.bulkMaxOperations = bulkMaxOperations;
    this.bulkMaxPayloadSize = bulkMaxPayloadSize;
    this.created = created;
    this.lastModified = lastModified;
  }
}