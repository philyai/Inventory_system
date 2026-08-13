package com.inventorysystem.Model;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;

import org.junit.Test;

public class DisposalRequestModelTest {
    private final Gson gson = new Gson();

    @Test
    public void disposalQuantityDoesNotFallBackToTotalItemStock() {
        DisposalRequestModel request = gson.fromJson(
                "{\"disposal_quantity\":2,\"Item\":{\"quantity\":50}}",
                DisposalRequestModel.class
        );

        assertEquals(2, request.getQuantity());
    }

    @Test
    public void legacyQuantityFieldRemainsSupported() {
        DisposalRequestModel request = gson.fromJson(
                "{\"quantity\":3}",
                DisposalRequestModel.class
        );

        assertEquals(3, request.getQuantity());
    }
}
