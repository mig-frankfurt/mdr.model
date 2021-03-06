package de.mig.mdr.model.handler.element;

import static java.util.stream.Collectors.toList;

import de.mig.mdr.model.dto.element.Element;
import de.mig.mdr.model.dto.element.section.Identification;
import de.mig.mdr.model.handler.element.section.DefinitionHandler;
import de.mig.mdr.model.handler.element.section.MemberHandler;
import de.mig.mdr.model.handler.element.section.SlotHandler;
import de.mig.mdr.dal.jooq.enums.ElementType;
import de.mig.mdr.dal.jooq.enums.Status;
import de.mig.mdr.dal.jooq.tables.pojos.ScopedIdentifier;
import de.mig.mdr.dal.jooq.tables.records.IdentifiedElementRecord;
import de.mig.mdr.model.CtxUtil;
import de.mig.mdr.model.dto.element.Record;
import de.mig.mdr.model.dto.element.section.Member;
import de.mig.mdr.model.handler.element.section.IdentificationHandler;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;

public class RecordHandler extends ElementHandler {

  /**
   * Get a record by its urn.
   */
  public static Record get(DSLContext ctx, int userId, String urn) {
    Identification identification = IdentificationHandler.fromUrn(urn);
    IdentifiedElementRecord identifiedElementRecord = ElementHandler
        .getIdentifiedElementRecord(ctx, identification);
    Element element = ElementHandler.convertToElement(ctx, identification, identifiedElementRecord);

    Record record = new Record();
    record.setIdentification(identification);
    record.setDefinitions(element.getDefinitions());
    record.setMembers(MemberHandler.get(ctx, identification));
    record.setSlots(SlotHandler.get(ctx, element.getIdentification()));
    return record;
  }


  /** Create a new Record and return its new Scoped identifier. */
  public static ScopedIdentifier create(
      DSLContext ctx, int userId, Record record)
      throws IllegalArgumentException {

    // Check if all member urns are present
    List<Element> members = ElementHandler
        .fetchByUrns(ctx, userId, record.getMembers().stream().map(Member::getElementUrn)
            .collect(toList()));
    if (members.size() != record.getMembers().size()) {
      throw new IllegalArgumentException();
    }

    final boolean autoCommit = CtxUtil.disableAutoCommit(ctx);
    de.mig.mdr.dal.jooq.tables.pojos.Element element =
        new de.mig.mdr.dal.jooq.tables.pojos.Element();
    element.setElementType(ElementType.RECORD);
    if (element.getUuid() == null) {
      element.setUuid(UUID.randomUUID());
    }
    element.setId(saveElement(ctx, element));
    ScopedIdentifier scopedIdentifier =
        IdentificationHandler.create(
            ctx, userId, record.getIdentification(), element.getId());
    DefinitionHandler.create(
        ctx, record.getDefinitions(), element.getId(), scopedIdentifier.getId());
    if (record.getSlots() != null) {
      SlotHandler.create(ctx, record.getSlots(), scopedIdentifier.getId());
    }
    if (record.getMembers() != null) {
      MemberHandler.create(ctx, userId, members, scopedIdentifier.getId());
    }
    CtxUtil.commitAndSetAutoCommit(ctx, autoCommit);
    return scopedIdentifier;
  }

  /**
   * Update an identifier.
   */
  public static Identification update(DSLContext ctx, int userId, Record record)
      throws IllegalAccessException {
    Record previousRecord = get(ctx, userId, record.getIdentification().getUrn());

    // If the members changed in any way, an update is not allowed
    if (!record.getMembers().equals(previousRecord.getMembers())) {
      throw new IllegalArgumentException();
    }

    //update scopedIdentifier if status != DRAFT
    if (previousRecord.getIdentification().getStatus() != Status.DRAFT) {

      ScopedIdentifier scopedIdentifier =
          IdentificationHandler.update(ctx, userId, record.getIdentification(),
              ElementHandler.getIdentifiedElementRecord(ctx,record.getIdentification()).getId());
      record.setIdentification(IdentificationHandler.convert(scopedIdentifier));
      record.getIdentification().setNamespaceId(
          Integer.parseInt(previousRecord.getIdentification().getUrn().split(":")[1]));
    }

    delete(ctx, userId, previousRecord.getIdentification().getUrn());
    create(ctx, userId, record);

    return record.getIdentification();
  }
}
