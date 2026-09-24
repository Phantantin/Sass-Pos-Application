package com.tindev.mapper;

import com.tindev.modal.Branch;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BranchMapperTest {

    @Test
    void materializesWorkingDaysInsteadOfReturningEntityCollection() {
        List<String> entityCollection = new ArrayList<>(List.of("Thứ 2"));
        Branch branch = new Branch();
        branch.setWorkingDays(entityCollection);

        var dto = BranchMapper.toDTO(branch);
        entityCollection.add("Thứ 3");

        assertEquals(List.of("Thứ 2"), dto.getWorkingDays());
    }
}
