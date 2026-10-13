package com.desktopaccountingapi.quickbooksdesktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.desktopaccountingapi.quickbooksdesktop.core.Confirm;
import com.desktopaccountingapi.quickbooksdesktop.models.AssignToObject;
import com.desktopaccountingapi.quickbooksdesktop.models.CustomFieldDefinition;
import com.desktopaccountingapi.quickbooksdesktop.models.CustomFieldOperation;
import com.desktopaccountingapi.quickbooksdesktop.models.CustomFieldValueInput;
import com.desktopaccountingapi.quickbooksdesktop.models.CustomFieldValueInputFields;
import com.desktopaccountingapi.quickbooksdesktop.models.CustomerDeleteParams;
import com.desktopaccountingapi.quickbooksdesktop.models.CustomerMergeInput;
import com.desktopaccountingapi.quickbooksdesktop.models.Lead;
import com.desktopaccountingapi.quickbooksdesktop.models.LeadStatus;
import com.desktopaccountingapi.quickbooksdesktop.models.ListMergeInputFields;
import com.desktopaccountingapi.quickbooksdesktop.models.TransactionCustomFieldValueInput;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Gap G4: confirm required by the constructor, typed open enums, shared input interfaces. */
class TypedInputTest {
    @Test
    void deleteParametersTakeConfirmYes() {
        CustomerDeleteParams p = new CustomerDeleteParams(Confirm.YES);
        assertEquals(Boolean.TRUE, p.confirm());
        assertEquals("[confirm=true]", p.queryPairs().toString());
        assertThrows(IllegalArgumentException.class, () -> new CustomerDeleteParams(null));
    }

    @Test
    void mergeInputTakesConfirmYesAndSharesAnInterface() {
        CustomerMergeInput input = new CustomerMergeInput("1", "80000002-1", "2", Confirm.YES);
        assertEquals("{\"confirm\":true,\"revisionNumber\":\"1\",\"mergeIntoId\":\"80000002-1\",\"mergeIntoRevisionNumber\":\"2\"}", input.toJson());
        ListMergeInputFields shared = input;
        assertEquals("80000002-1", shared.mergeIntoId());
        assertEquals(Boolean.TRUE, shared.confirm());
    }

    @Test
    void openEnumsAreTypedAndKeepUnknownValues() {
        assertSame(LeadStatus.HOT, LeadStatus.of("hot"));
        assertEquals(LeadStatus.Known.HOT, LeadStatus.HOT.known());
        LeadStatus future = LeadStatus.of("lukewarm");
        assertFalse(future.isKnown());
        assertEquals(LeadStatus.Known.UNRECOGNIZED, future.known());
        assertEquals("lukewarm", future.value());
        Lead lead = Lead.parse("{\"id\":\"1\",\"objectType\":\"qbd_lead\",\"status\":\"lukewarm\"}");
        assertEquals(future, lead.status());
        assertTrue(lead.toJson().contains("\"status\":\"lukewarm\""));
        CustomFieldDefinition definition = CustomFieldDefinition.parse("{\"assignToObjects\":[\"customer\",\"invoice\"]}");
        assertEquals(Arrays.asList(AssignToObject.CUSTOMER, AssignToObject.INVOICE), definition.assignToObjects());
    }

    @Test
    void customFieldValueInputsShareAnInterface() {
        CustomFieldValueInputFields list = new CustomFieldValueInput(CustomFieldOperation.UPDATE, "Region").value("West");
        CustomFieldValueInputFields transaction = new TransactionCustomFieldValueInput(CustomFieldOperation.UPDATE, "Region").value("West").transactionLineId("1-1");
        assertEquals(list.name(), transaction.name());
        assertEquals(list.operation(), transaction.operation());
        assertEquals("{\"operation\":\"update\",\"name\":\"Region\",\"value\":\"West\"}", ((CustomFieldValueInput) list).toJson());
    }
}
