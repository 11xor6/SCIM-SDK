package de.gold.scim.patch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.gold.scim.constants.ScimType;
import de.gold.scim.constants.enums.PatchOp;
import de.gold.scim.constants.enums.Type;
import de.gold.scim.exceptions.BadRequestException;
import de.gold.scim.exceptions.IOException;
import de.gold.scim.exceptions.ScimException;
import de.gold.scim.filter.AttributePathRoot;
import de.gold.scim.filter.FilterNode;
import de.gold.scim.filter.resources.PatchFilterResolver;
import de.gold.scim.resources.ResourceNode;
import de.gold.scim.resources.base.ScimArrayNode;
import de.gold.scim.resources.base.ScimBooleanNode;
import de.gold.scim.resources.base.ScimDoubleNode;
import de.gold.scim.resources.base.ScimIntNode;
import de.gold.scim.resources.base.ScimLongNode;
import de.gold.scim.resources.base.ScimObjectNode;
import de.gold.scim.resources.base.ScimTextNode;
import de.gold.scim.schemas.ResourceType;
import de.gold.scim.schemas.SchemaAttribute;
import de.gold.scim.utils.JsonHelper;
import de.gold.scim.utils.RequestUtils;
import lombok.extern.slf4j.Slf4j;


/**
 * author Pascal Knueppel <br>
 * created at: 30.10.2019 - 09:07 <br>
 * <br>
 * this class will handle the patch-add operation if a target is specified <br>
 *
 * <pre>
 *    The result of the add operation depends upon what the target location
 *    indicated by "path" references:
 *
 *    o  If the target location does not exist, the attribute and value are
 *       added.
 *
 *    o  If the target location specifies a complex attribute, a set of
 *       sub-attributes SHALL be specified in the "value" parameter.
 *
 *    o  If the target location specifies a multi-valued attribute, a new
 *       value is added to the attribute.
 *
 *    o  If the target location specifies a single-valued attribute, the
 *       existing value is replaced.
 *
 *    o  If the target location specifies an attribute that does not exist
 *       (has no value), the attribute is added with the new value.
 *
 *    o  If the target location exists, the value is replaced.
 *
 *    o  If the target location already contains the value specified, no
 *       changes SHOULD be made to the resource, and a success response
 *       SHOULD be returned.  Unless other operations change the resource,
 *       this operation SHALL NOT change the modify timestamp of the
 *       resource.
 * </pre>
 */
@Slf4j
public class PatchTargetHandler extends AbstractPatch
{

  /**
   * the specified path where the value should be added
   */
  private final AttributePathRoot path;

  /**
   * the patch operation to handle
   */
  private final PatchOp patchOp;

  /**
   * the attribute definition of the target
   */
  private SchemaAttribute schemaAttribute;

  public PatchTargetHandler(ResourceType resourceType, PatchOp patchOp, String path)
  {
    super(resourceType);
    try
    {
      this.path = RequestUtils.parsePatchPath(resourceType, path);
    }
    catch (ScimException ex)
    {
      ex.setScimType(ScimType.RFC7644.INVALID_PATH);
      throw ex;
    }
    this.patchOp = patchOp;
    this.schemaAttribute = getSchemaAttribute();
  }


  /**
   * will add the specified values into the specified path
   *
   * @param resource the resource to which the values should be added
   * @param values the values that should be added into the resource
   * @return true if an effective change was made, false else
   */
  public boolean addOperationValues(ResourceNode resource, List<String> values)
  {
    validateRequest(values);
    String[] fullAttributeNames = getAttributeNames();

    String firstAttributeName = fullAttributeNames[0];
    SchemaAttribute schemaAttribute = getSchemaAttribute(firstAttributeName);
    boolean isExtension = resourceType.getSchemaExtensions()
                                      .stream()
                                      .anyMatch(ext -> ext.getSchema().equals(schemaAttribute.getResourceUri()));
    ObjectNode currentParent = resource;
    if (isExtension)
    {
      currentParent = (ObjectNode)currentParent.get(schemaAttribute.getResourceUri());
      if (currentParent == null)
      {
        currentParent = new ScimObjectNode();
        resource.set(schemaAttribute.getResourceUri(), currentParent);
      }
    }
    JsonNode firstAttribute = getAttributeFromObject(currentParent, firstAttributeName);

    // if the attribute is null we know that this is a simple attribute
    if (firstAttribute == null && !Type.COMPLEX.equals(schemaAttribute.getType())
        || (firstAttribute != null && !firstAttribute.isArray() && !firstAttribute.isObject()))
    {
      return addOrReplaceSimpleNode(schemaAttribute, currentParent, values);
    }
    else if (firstAttribute != null && firstAttribute.isArray())
    {
      return handleMultiValuedAttribute(schemaAttribute, (ArrayNode)firstAttribute, fullAttributeNames, values);
    }
    else if (Type.COMPLEX.equals(schemaAttribute.getType()))
    {
      return handleComplexAttribute(schemaAttribute, currentParent, fullAttributeNames, values);
    }

    return false;
  }

  /**
   * adds or replaces a simple node in the given object node
   *
   * @param schemaAttribute the attribute schema definition
   * @param objectNode the object node into which the new node should be added or replaced
   * @param values the values that should be added to the node. This list must not contain more than a single
   *          entry
   * @return true if an effective change was made, false else
   */
  protected boolean addOrReplaceSimpleNode(SchemaAttribute schemaAttribute, ObjectNode objectNode, List<String> values)
  {
    if (values.size() != 1 && !schemaAttribute.isMultiValued())
    {
      throw new BadRequestException("found multiple values for simple attribute '"
                                    + schemaAttribute.getFullResourceName() + "': " + String.join(",", values), null,
                                    ScimType.RFC7644.INVALID_VALUE);
    }
    JsonNode oldNode = objectNode.get(schemaAttribute.getName());
    JsonNode newNode = createNewNode(schemaAttribute, values.get(0));
    if (!newNode.equals(oldNode))
    {
      objectNode.set(schemaAttribute.getName(), newNode);
      return true;
    }
    else
    {
      return false;
    }
  }

  /**
   * adds or replaces complex attributes
   *
   * @param schemaAttribute the attribute schema definition
   * @param resource the resource into which the complex type should be added or replaced
   * @param fullAttributeNames contains all attribute names. It starts with the name of the complex attributes
   *          and might follow with the sub-attribute of the complex type if specified. the full name contains
   *          the full resource uri
   * @param values the values that should be added to the complex type
   * @return
   */
  private boolean handleComplexAttribute(SchemaAttribute schemaAttribute,
                                         ObjectNode resource,
                                         String[] fullAttributeNames,
                                         List<String> values)
  {
    if (fullAttributeNames.length > 1)
    {
      SchemaAttribute subAttribute = getSchemaAttribute(fullAttributeNames[1]);

      ObjectNode complexNode = (ObjectNode)resource.get(schemaAttribute.getName());
      if (complexNode == null)
      {
        complexNode = new ScimObjectNode(schemaAttribute);
        resource.set(schemaAttribute.getName(), complexNode);
      }
      if (handleInnerComplexAttribute(subAttribute, complexNode, values))
      {
        return true;
      }
      JsonNode firstAttribute = resource.get(fullAttributeNames[1]);
      return firstAttribute == null || !firstAttribute.asText().equals(values.get(0));
    }
    else
    {
      if (values.size() != 1 || StringUtils.isBlank(values.get(0)))
      {
        throw new BadRequestException("found multiple or no values for non multi valued complex type '"
                                      + schemaAttribute.getFullResourceName() + "': \n\t" + String.join(",", values),
                                      null, ScimType.RFC7644.INVALID_VALUE);
      }
      JsonNode newNode = JsonHelper.readJsonDocument(values.get(0));
      if (newNode == null || !newNode.isObject())
      {
        throw new BadRequestException("given value is not a complex json representation for attribute '"
                                      + schemaAttribute.getFullResourceName() + "': \n\t" + String.join(",", values),
                                      null, ScimType.RFC7644.INVALID_VALUE);
      }
      JsonNode oldNode = resource.get(schemaAttribute.getName());
      newNode = mergeObjectNodes((ObjectNode)newNode, oldNode);
      resource.set(schemaAttribute.getName(), newNode);
      return !newNode.equals(oldNode);
    }
  }

  private boolean handleInnerComplexAttribute(SchemaAttribute subAttribute, ObjectNode complexNode, List<String> values)
  {
    if (subAttribute.isMultiValued())
    {
      ArrayNode arrayNode = (ArrayNode)complexNode.get(subAttribute.getName());
      if (arrayNode == null)
      {
        arrayNode = new ScimArrayNode(subAttribute);
        complexNode.set(subAttribute.getName(), arrayNode);
      }
      values.forEach(arrayNode::add);
      return true;
    }
    else
    {
      return addOrReplaceSimpleNode(subAttribute, complexNode, values);
    }
  }

  /**
   * merges two object nodes into a single node
   *
   * @param newNode
   * @param oldNode
   * @return
   */
  private JsonNode mergeObjectNodes(ObjectNode newNode, JsonNode oldNode)
  {
    if (oldNode == null)
    {
      return newNode;
    }
    oldNode.fields().forEachRemaining(stringJsonNodeEntry -> {
      final String key = stringJsonNodeEntry.getKey();
      final JsonNode value = stringJsonNodeEntry.getValue();
      JsonNode newSubNode = newNode.get(key);
      if (newSubNode == null)
      {
        newNode.set(key, value);
      }
      else if (newSubNode.isArray())
      {
        // did it in this way to preserve the original array order and to append the new values
        newSubNode.forEach(((ArrayNode)value)::add);
        newNode.set(key, value);
      }
    });
    return newNode;
  }

  /**
   * handles multi valued complex nodes
   *
   * @param schemaAttribute the schema attribute definition of the top level node
   * @param multiValuedComplex the array node that is represented by the {@code schemaAttribute}
   * @param fullAttributeNames the array of full attribute names with their resourceUris e.g. <br>
   *
   *          <pre>
   *              urn:gold:params:scim:schemas:custom:2.0:AllTypes:name<br>
   *              urn:gold:params:scim:schemas:custom:2.0:AllTypes:name.givenName
   *          </pre>
   *
   * @param values the values that should be added to the multi valued complex type
   * @return true if an effective change has been made, false else
   */
  private boolean handleMultiValuedAttribute(SchemaAttribute schemaAttribute,
                                             ArrayNode multiValuedComplex,
                                             String[] fullAttributeNames,
                                             List<String> values)
  {
    if (Type.COMPLEX.equals(schemaAttribute.getType()))
    {
      if (fullAttributeNames.length > 1)
      {
        if (multiValuedComplex.isEmpty())
        {
          throw new BadRequestException("the multi valued complex type '" + schemaAttribute.getFullResourceName()
                                        + "' is not set", null, ScimType.RFC7644.NO_TARGET);
        }
        SchemaAttribute subAttribute = RequestUtils.getSchemaAttributeByAttributeName(resourceType,
                                                                                      fullAttributeNames[1]);
        List<ObjectNode> matchingComplexNodes = resolveFilter(multiValuedComplex, path);
        AtomicBoolean changeWasMade = new AtomicBoolean(false);
        matchingComplexNodes.forEach(jsonNodes -> changeWasMade.weakCompareAndSet(false,
                                                                                  handleInnerComplexAttribute(subAttribute,
                                                                                                              jsonNodes,
                                                                                                              values)));
        return changeWasMade.get();
      }
      else if (StringUtils.isNotBlank(path.getSubAttributeName()))
      {
        String fullName = fullAttributeNames[0] + "." + path.getSubAttributeName();
        SchemaAttribute subAttribute = RequestUtils.getSchemaAttributeByAttributeName(resourceType, fullName);
        List<ObjectNode> matchingComplexNodes = resolveFilter(multiValuedComplex, path);
        AtomicBoolean changeWasMade = new AtomicBoolean(false);
        matchingComplexNodes.forEach(jsonNodes -> changeWasMade.weakCompareAndSet(false,
                                                                                  handleInnerComplexAttribute(subAttribute,
                                                                                                              jsonNodes,
                                                                                                              values)));
        return changeWasMade.get();
      }
      else
      {
        for ( String value : values )
        {
          try
          {
            JsonNode jsonNode = JsonHelper.readJsonDocument(value);
            multiValuedComplex.add(jsonNode);
          }
          catch (IOException ex)
          {
            throw new BadRequestException("the value must be a whole complex type json structure but was: '" + value
                                          + "'", ex, ScimType.RFC7644.INVALID_VALUE);
          }
        }
        return true;
      }
    }
    else
    {
      for ( String value : values )
      {
        multiValuedComplex.add(createNewNode(schemaAttribute, value));
      }
      return true;
    }
  }

  /**
   * this method will extract all complex types from the given array node that do match the filter
   *
   * @param multiValuedComplex the multi valued complex node
   * @param path the filter expression that must be resolved to get the matching nodes
   * @return the list of nodes that should be modified
   */
  private List<ObjectNode> resolveFilter(ArrayNode multiValuedComplex, FilterNode path)
  {
    PatchFilterResolver patchFilterResolver = new PatchFilterResolver();
    List<ObjectNode> matchingComplexNodes = new ArrayList<>();
    for ( JsonNode complex : multiValuedComplex )
    {
      patchFilterResolver.isNodeMatchingFilter((ObjectNode)complex, path).ifPresent(matchingComplexNodes::add);
    }
    return matchingComplexNodes;
  }

  /**
   * creates a new json node with the given value
   *
   * @param schemaAttribute the attribute schema definition
   * @param value the value that should be added into the node
   * @return the simple json node
   */
  private JsonNode createNewNode(SchemaAttribute schemaAttribute, String value)
  {
    switch (schemaAttribute.getType())
    {
      case STRING:
      case DATE_TIME:
      case REFERENCE:
        return new ScimTextNode(schemaAttribute, value);
      case BOOLEAN:
        return new ScimBooleanNode(schemaAttribute, Boolean.parseBoolean(value));
      case INTEGER:
        Long longVal = Long.parseLong(value);
        if (longVal == longVal.intValue())
        {
          return new ScimIntNode(schemaAttribute, longVal.intValue());
        }
        else
        {
          return new ScimLongNode(schemaAttribute, longVal);
        }
      default:
        return new ScimDoubleNode(schemaAttribute, Double.parseDouble(value));
    }
  }

  /**
   * will check that the expressions are correctly written for the defined patch operation
   *
   * @param values the values of the request
   */
  protected void validateRequest(List<String> values)
  {
    validateAttributeType(values);
    validatePath(path, patchOp, values);
  }

  /**
   * this method will check the the expression send by the client does follow its syntax rules based on the used
   * operation
   *
   * @param path the target expression
   * @param patchOp the operation
   * @param values the values (should be empty on delete)
   */
  private void validatePath(AttributePathRoot path, PatchOp patchOp, List<String> values)
  {
    switch (patchOp)
    {
      case ADD:
        validateAddOperation(path, values);
        break;
      case REPLACE:
        validateReplaceOperation(path, values);
        break;
      case REMOVE:
        validateRemoveOperation(path, values);
        break;
    }
  }

  /**
   * will validate that no values are present in the values list all other path representations should be valid
   * except for an empty representation
   *
   * @param path the attribute path expression
   * @param values in remove operation no values should be present
   */
  private void validateRemoveOperation(AttributePathRoot path, List<String> values)
  {
    if (values != null && !values.isEmpty())
    {
      throw new BadRequestException("values must not be set for remove operation but was: " + String.join(",", values),
                                    null, ScimType.RFC7644.INVALID_PATH);
    }
    if (path == null)
    {
      throw new BadRequestException("no target present within the request", null, ScimType.RFC7644.INVALID_PATH);
    }
  }

  /**
   * will validate that the given attribute path expression is valid for a replace operation
   *
   * @param path the attribute path expression
   * @param values the values that should replace other values
   */
  private void validateReplaceOperation(AttributePathRoot path, List<String> values)
  {
    if (values == null || values.isEmpty())
    {
      throw new BadRequestException("values parameter must be set for replace operation but was empty", null,
                                    ScimType.RFC7644.INVALID_VALUE);
    }
    // emails[value eq "123456"]
    if (StringUtils.isBlank(path.getSubAttributeName()) && path.getChild() != null
        && !values.stream().allMatch(JsonHelper::isValidJson))
    {
      throw new BadRequestException("the values are expected to be valid json representations for an expression as "
                                    + "'" + path.toString() + "' but was: " + String.join(",\n", values), null,
                                    ScimType.RFC7644.INVALID_PATH);
    }
    checkIsValidComplexJson(path, values);
  }

  /**
   * verifies that the values are valid json representations if we have an injection into a complex type without
   * a sub-attribute
   *
   * @param path the target of the expression
   * @param values the values should be added or replaced
   */
  private void checkIsValidComplexJson(AttributePathRoot path, List<String> values)
  {
    String[] namePath = path.getShortName().split("\\.");
    // emails or name
    if (path.getChild() == null && Type.COMPLEX.equals(path.getSchemaAttribute().getType()) && namePath.length == 1
        && !values.stream().allMatch(JsonHelper::isValidJson))
    {
      throw new BadRequestException("the value parameters must be valid json representations but was\n'"
                                    + String.join(",\n", values) + "'", null, ScimType.RFC7644.INVALID_PATH);

    }
  }

  /**
   * will validate that the given attribute path expression is valid for an add operation
   *
   * @param path the attribute path expression
   * @param values the values that should be injected
   */
  private void validateAddOperation(AttributePathRoot path, List<String> values)
  {
    // emails[value eq "123456"] (such an expression has no meaning in case of add)
    if (StringUtils.isBlank(path.getSubAttributeName()) && path.getChild() != null)
    {
      throw new BadRequestException("the given expression is not valid for an add-operation: '" + path.toString()
                                    + "'. Did you want an expression like this '" + path.toString()
                                    + ".subAttributeName'?", null, ScimType.RFC7644.INVALID_PATH);
    }
    checkIsValidComplexJson(path, values);
  }

  /**
   * checks that if the attribute is a simple type and not multi valued that only a single attribute is allowed
   * in the values parameter of the patch request
   *
   * @param values the values parameter that is under test
   */
  private void validateAttributeType(List<String> values)
  {
    switch (schemaAttribute.getType())
    {
      case STRING:
      case DATE_TIME:
      case REFERENCE:
      case BOOLEAN:
      case INTEGER:
      case DECIMAL:
        if (!schemaAttribute.isMultiValued() && values.size() > 1)
        {
          throw new BadRequestException("several values found for non multivalued node of type '"
                                        + schemaAttribute.getType() + "'", null, ScimType.RFC7644.INVALID_VALUE);
        }
        break;
    }
  }

  /**
   * will get the fully qualified attribute names
   */
  private String[] getAttributeNames()
  {
    String attributeName = path.getShortName();
    String[] attributeNames = attributeName.split("\\.");
    String resourceUri = path.getResourceUri() == null ? "" : path.getResourceUri() + ":";
    attributeNames[0] = resourceUri + attributeNames[0];
    for ( int i = 1 ; i < attributeNames.length ; i++ )
    {
      attributeNames[i] = attributeNames[i - 1] + "." + attributeNames[i];
    }
    return attributeNames;
  }

  /**
   * retrieves the schema attribute definition of the top loevel node of the patch attribute. The top level node
   * would be e.g. 'name' in the following representation "name.givenName"
   */
  private SchemaAttribute getSchemaAttribute()
  {
    if (this.schemaAttribute == null)
    {
      this.schemaAttribute = getSchemaAttribute(path.getFullName());
    }
    return this.schemaAttribute;
  }
}