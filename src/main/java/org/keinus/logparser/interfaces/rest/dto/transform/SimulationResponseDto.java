package org.keinus.logparser.interfaces.rest.dto.transform;

import java.util.ArrayList;
import java.util.List;

import org.keinus.logparser.domain.model.structured.StructuredEvent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SimulationResponseDto {
    private StructuredEvent result;
    private List<String> errors; // Simple string messages for now
    private boolean success;
}
